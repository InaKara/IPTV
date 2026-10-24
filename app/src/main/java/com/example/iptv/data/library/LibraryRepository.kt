package com.example.iptv.data.library

import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.CredentialsManager
import com.example.iptv.ui.browser.BrowserCatalog
import com.example.iptv.ui.browser.BrowserChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(val database: LibraryDatabase, private val credentials: CredentialsManager,
    private val client: OkHttpClient, private val logging: com.example.iptv.diagnostics.VerboseLogging? = null) {
    private val flights = ConcurrentHashMap<String, Mutex>()
    private val refreshVersions = ConcurrentHashMap<String, Long>()
    private val listingAges = ConcurrentHashMap<LibraryKind, Long>()
    @Volatile private var listingAccount: String? = null
    private val preparedAliases = java.util.Collections.synchronizedMap(object : LinkedHashMap<String, Unit>(256, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > 256
    })
    private var aliasGeneration = 0L
    private var activeAccount: LibraryAccount? = null
    private var accountGeneration = 0L
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var writeTail: Job? = null
    @Synchronized fun enqueue(account: LibraryAccount, channel: M3UChannel, position: Long, duration: Long, seekable: Boolean, ended: Boolean, legacyKey: String? = null) {
        val previous = writeTail
        writeTail = writeScope.launch {
            try { previous?.join(); record(account, channel, position, duration, seekable, ended, legacyKey) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* A disk failure must not interrupt playback or expose private values. */ }
        }
    }
    suspend fun flush() { val tail = synchronized(this) { writeTail }; tail?.join() }
    @Volatile private var identity: LibraryIdentity? = null
    @Volatile private var identityCache: String? = null
    @Volatile private var catalog: BrowserCatalog? = null
    @Synchronized fun account(): LibraryAccount {
        val current = LibraryAccount.create(credentials.serverUrl, credentials.username, credentials.password)
        return activeAccount?.takeIf { it.cache == current.cache } ?: LibraryAccount(current.base, current.user,
            current.password, ++accountGeneration).also {
                listingAccount = null; listingAges.clear(); preparedAliases.clear()
                activeAccount = it; listingAccount = it.cache
            }
    }
    fun valid(account: LibraryAccount) = runCatching {
        val current = account(); current.cache == account.cache && current.generation == account.generation
    }.getOrDefault(false)
    @Synchronized fun activate(account: LibraryAccount, catalog: BrowserCatalog) {
        this.catalog = catalog
        identity = null
        aliasGeneration++
        preparedAliases.clear()
        identityCache = account.cache
    }
    @Synchronized fun identity(account: LibraryAccount): LibraryIdentity {
        if (identityCache != account.cache) return LibraryIdentity(account, emptyList())
        return identity ?: LibraryIdentity(account, catalog?.channels.orEmpty()).also { identity = it }
    }
    fun resolve(account: LibraryAccount, entry: LibraryEntry): BrowserChannel = identity(account).resolve(entry).copy(library = entry)
    fun ensureAliases(account: LibraryAccount, references: Set<String> = emptySet()) {
        if (identityCache != account.cache || references.isEmpty()) return
        val links = identity(account).aliases(references)
        if (links.isEmpty()) return
        database.transaction({ valid(account) }) { db -> links.forEach { database.alias(db, account.owner, it.first, it.second, it.third) } }
    }
    fun retainPageAliases(account: LibraryAccount, entries: List<LibraryEntry>) {
        val (links, generation) = synchronized(this) { identity(account) to aliasGeneration }
        fun preparationKey(entry: LibraryEntry) = "${account.cache}:${account.generation}:$generation:${entry.kind}:${entry.id}:${entry.extension}"
        val pending = entries.distinctBy { preparationKey(it) }.filter { preparedAliases[preparationKey(it)] == null }
        val selected = pending.mapNotNull { entry -> links.legacy(entry)?.let { Triple(it.id, entry.kind, entry.id) } }
        if (selected.isNotEmpty()) database.transaction({ valid(account) }) { db ->
            selected.forEach { database.alias(db, account.owner, it.first, it.second, it.third) }
        }
        if (valid(account)) pending.forEach { preparedAliases[preparationKey(it)] = Unit }
        logging?.gate?.event(com.example.iptv.diagnostics.DiagnosticEvent.ALIAS_LINKS, count = selected.size)
    }
    private fun fresh(age: Long, now: Long) = age > 0 && now >= age && now - age < LIST_AGE
    fun listingKnownFresh(account: LibraryAccount, kind: LibraryKind): Boolean = listingAccount == account.cache &&
        listingAges[kind]?.let { fresh(it, System.currentTimeMillis()) } == true
    private fun reference(account: LibraryAccount, key: String): Pair<LibraryKind, String>? {
        if (key.startsWith("library:${account.owner}:")) {
            val parts = key.split(':')
            if (parts.size == 4) return runCatching { LibraryKind.valueOf(parts[2]) to parts[3] }.getOrNull()
        }
        return database.alias(account.owner, key)
    }
    fun retainedEntry(account: LibraryAccount, key: String): LibraryEntry? {
        val route = reference(account, key) ?: return null
        database.find(account, route.first, route.second)?.let { return it }
        val typed = account.key(route.first, route.second)
        val stored = database.storedPlayable(account.owner, typed) ?: database.storedPlayable(account.owner, key) ?: return null
        return LibraryEntry(route.first, route.second, stored.name, categoryName = stored.groupTitle, poster = stored.logoUrl,
            extension = stored.streamUrl.substringBefore('?').substringAfterLast('.', "mp4"),
            seriesId = database.storedSeries(account.owner, typed))
    }
    suspend fun retainPlayable(account: LibraryAccount, channel: BrowserChannel) = local {
        if (!channel.channel.isVod || channel.season != null || !valid(account)) return@local
        ensureAliases(account, setOf(channel.id))
        val key = key(account, channel.channel, channel.id)
        if (database.storedPlayable(account.owner, key) == null) database.transaction({ valid(account) }) {
            database.saveProgress(account.owner, key, channel.channel, channel.library?.seriesId.orEmpty(), WatchProgress())
        }
    }
    fun key(account: LibraryAccount, channel: M3UChannel, legacyKey: String? = null): String {
        val legacy = legacyKey ?: BrowserCatalog.create(listOf(channel)).channels.single().id
        return identity(account).progressKey(channel, legacy)
    }
    fun restored(account: LibraryAccount, key: String, channel: M3UChannel): BrowserChannel {
        identity(account).legacyId(key)?.let { return it }
        if (key.startsWith("library:${account.owner}:")) {
            val parts = key.split(':')
            val kind = runCatching { LibraryKind.valueOf(parts[2]) }.getOrNull()
            if (kind != null && kind != LibraryKind.SERIES) {
                val entry = database.find(account, kind, parts.last()) ?: LibraryEntry(kind, parts.last(), channel.name,
                    categoryName = channel.groupTitle, poster = channel.logoUrl,
                    extension = channel.streamUrl.substringBefore('?').substringAfterLast('.', "mp4"))
                return resolve(account, entry)
            }
        }
        return BrowserChannel(key, channel)
    }
    suspend fun <T> local(action: () -> T): T = withContext(Dispatchers.IO) { action() }
    @OptIn(InternalCoroutinesApi::class)
    private fun <T> response(url: HttpUrl, limit: Long, job: Job, consume: (InputStream, () -> Long) -> T): T {
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", "AypiTivi").build())
        val cancellation = job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { if (it != null) call.cancel() }
        try {
            return call.execute().use { response ->
                check(response.isSuccessful)
                val body = requireNotNull(response.body)
                require(body.contentLength() <= limit)
                var bytes = 0L
                val bounded = object : FilterInputStream(body.byteStream()) {
                    private fun checkSize(size: Int) { job.ensureActive(); if (size > 0) bytes += size; require(bytes <= limit) }
                    override fun read(): Int = super.read().also { checkSize(if (it < 0) 0 else 1) }
                    override fun read(b: ByteArray, off: Int, len: Int): Int = `in`.read(b, off, len).also(::checkSize)
                }
                consume(bounded) { bytes }
            }
        } finally { cancellation.dispose() }
    }
    suspend fun refresh(account: LibraryAccount, kind: LibraryKind, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        require(kind != LibraryKind.EPISODE)
        val flight = "${account.cache}:${kind.name}"
        val before = refreshVersions[flight] ?: 0
        flights.getOrPut(flight) { Mutex() }.withLock {
            val job = currentCoroutineContext()[Job]!!
            if (!valid(account)) return@withLock false
            if (force && (refreshVersions[flight] ?: 0) != before) return@withLock true
            val age = database.age(account.cache, kind)
            if (!force && fresh(age, System.currentTimeMillis())) {
                if (valid(account)) listingAges[kind] = age
                return@withLock true
            }
            val started = System.nanoTime()
            logging?.gate?.event(com.example.iptv.diagnostics.DiagnosticEvent.LIST_BEGIN)
            try {
                database.transaction({ valid(account) && job.isActive }) { db ->
                    database.clearListing(db, account.cache, kind)
                    val prefix = if (kind == LibraryKind.MOVIE) "vod" else "series"
                    var ordinal = 0
                    response(account.api("get_${prefix}_categories"), 4L * 1024 * 1024, job) { input, _ ->
                        LibraryParser.categories(input) { database.category(db, account.cache, kind, it, ordinal++) }
                    }
                    response(account.api(if (kind == LibraryKind.MOVIE) "get_vod_streams" else "get_series"), 64L * 1024 * 1024, job) { input, _ ->
                        LibraryParser.listing(input, kind) { database.entry(db, account.cache, it) }
                    }
                    database.fresh(db, account.cache, kind, System.currentTimeMillis())
                }
                refreshVersions[flight] = before + 1
                if (valid(account)) listingAges[kind] = database.age(account.cache, kind)
                logging?.gate?.event(com.example.iptv.diagnostics.DiagnosticEvent.LIST_OK, (System.nanoTime() - started) / 1_000_000)
                true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { logging?.gate?.event(com.example.iptv.diagnostics.DiagnosticEvent.LIST_FAILED); false }
        }
    }
    suspend fun details(account: LibraryAccount, entry: LibraryEntry, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (entry.kind == LibraryKind.EPISODE) return@withContext true
        val flight = "${account.cache}:${entry.kind}:${entry.id}"
        val before = refreshVersions[flight] ?: 0
        flights.getOrPut(flight) { Mutex() }.withLock {
            val job = currentCoroutineContext()[Job]!!
            if (!valid(account)) return@withLock false
            if (force && (refreshVersions[flight] ?: 0) != before) return@withLock true
            val now = System.currentTimeMillis()
            val parserCurrent = entry.kind != LibraryKind.SERIES || database.seriesParserCurrent(account, entry)
            if (!force && parserCurrent && database.details(account, entry, now)?.second?.let { now >= it && now - it < DETAIL_AGE } == true) return@withLock true
            val started = System.nanoTime()
            logging?.gate?.event(com.example.iptv.diagnostics.DiagnosticEvent.DETAIL_BEGIN)
            try {
                val series = entry.kind == LibraryKind.SERIES
                database.transaction({ valid(account) && job.isActive }) { db ->
                    if (series) db.delete("entries", "cache=? AND kind='EPISODE' AND series=?", arrayOf(account.cache, entry.id))
                    response(account.api(if (series) "get_series_info" else "get_vod_info",
                        (if (series) "series_id" else "vod_id") to entry.id), LibraryDatabase.DETAIL_BUDGET - 1024, job) { input, bytes ->
                        var episodeBytes = 0L
                        val details = LibraryParser.details(input, entry.id.takeIf { series }) { episode ->
                            episodeBytes += listOf(episode.id, episode.name, episode.poster.orEmpty(), episode.runtime, episode.season, episode.episode)
                                .sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() } + 512
                            require(episodeBytes <= LibraryDatabase.DETAIL_BUDGET - 1024)
                            database.entry(db, account.cache, episode.copy(category = entry.category))
                        }
                        val weight = maxOf(bytes(), episodeBytes) + 1024
                        require(weight <= LibraryDatabase.DETAIL_BUDGET)
                        database.putDetails(db, account, entry, details, now, weight)
                    }
                }
                refreshVersions[flight] = before + 1
                logging?.gate?.event(com.example.iptv.diagnostics.DiagnosticEvent.DETAIL_OK, (System.nanoTime() - started) / 1_000_000)
                true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { logging?.gate?.event(com.example.iptv.diagnostics.DiagnosticEvent.DETAIL_FAILED); false }
        }
    }
    suspend fun progress(account: LibraryAccount, channel: M3UChannel, legacyKey: String? = null): WatchProgress = local {
        database.progress(account.owner, key(account, channel, legacyKey))
    }
    suspend fun record(account: LibraryAccount, channel: M3UChannel, position: Long, duration: Long, seekable: Boolean, ended: Boolean = false, legacyKey: String? = null) = local {
        if (!channel.isVod || !valid(account)) return@local
        val key = key(account, channel, legacyKey)
        val old = database.progress(account.owner, key)
        val route = identity(account).route(channel)
        val series = route?.takeIf { it.first == LibraryKind.EPISODE }?.let { database.find(account, it.first, it.second)?.seriesId }
            ?: database.storedSeries(account.owner, key)
        database.transaction({ valid(account) }) {
            database.saveProgress(account.owner, key, channel, series,
                WatchProgress.record(old, position, duration, seekable, ended, System.currentTimeMillis()))
        }
    }
    suspend fun reset(account: LibraryAccount, channel: M3UChannel, watched: Boolean = false, legacyKey: String? = null) = local {
        if (!valid(account) || !channel.isVod) return@local
        val route = identity(account).route(channel)
        val series = route?.let { database.find(account, it.first, it.second)?.seriesId }
            ?: database.storedSeries(account.owner, key(account, channel, legacyKey))
        database.transaction({ valid(account) }) {
            database.saveProgress(account.owner, key(account, channel, legacyKey), channel, series,
                WatchProgress(watched = watched, updated = System.currentTimeMillis()))
        }
    }
    suspend fun supplemental(account: LibraryAccount, ids: Set<String>, query: String? = null): List<Pair<LibraryEntry, BrowserChannel>> = local {
        if (!valid(account)) return@local emptyList()
        val rows = if (query != null) database.page(account, query = query, filter = false)
        else ids.asSequence().mapNotNull { key ->
            identity(account).legacyId(key)?.let { legacy ->
                identity(account).route(legacy.channel)?.let { database.find(account, it.first, it.second) }
            } ?: run {
            retainedEntry(account, key)
            }
        }.toList()
        rows.map { it to resolve(account, it) }.distinctBy { it.second.id }
    }
    suspend fun favoriteReferences(account: LibraryAccount, ids: Set<String>): Map<String, BrowserChannel> = local {
        if (!valid(account)) return@local emptyMap()
        ids.mapNotNull { id ->
            if (id.startsWith("season:${account.owner}:")) {
                database.season(account, id)?.let { saved ->
                    val latest = database.find(account, LibraryKind.SERIES, saved.seriesId)
                    val season = if (latest != null) saved.copy(name = latest.name, category = latest.category,
                        categoryName = latest.categoryName, poster = latest.poster) else saved
                    id to BrowserChannel(id, M3UChannel("${season.name} · ${season.season}", season.categoryName,
                        season.poster, "", null, null, null, true), season = season)
                }
            } else retainedEntry(account, id)?.takeIf { it.kind != LibraryKind.SERIES }?.let { id to resolve(account, it) }
        }.toMap()
    }
    suspend fun saveSeason(account: LibraryAccount, season: SeasonFavorite) = local {
        if (valid(account)) database.transaction({ valid(account) }) { database.saveSeason(account, season) }
    }
    companion object { const val LIST_AGE = 24L * 60 * 60 * 1000; const val DETAIL_AGE = 7 * LIST_AGE }
}
