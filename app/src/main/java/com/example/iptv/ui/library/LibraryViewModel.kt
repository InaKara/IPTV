package com.example.iptv.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iptv.data.library.*
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.BrowserPreferences
import com.example.iptv.data.storage.RecentlyWatchedManager
import com.example.iptv.ui.browser.BrowserChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryRoute { ROOT, CATEGORIES, TITLES, DETAIL, SEASONS, EPISODES, CONTINUE, RECENT, PLAYLIST }
data class LibraryLocation(val route: LibraryRoute = LibraryRoute.ROOT, val kind: LibraryKind = LibraryKind.MOVIE,
    val category: String? = null, val label: String = "", val entry: LibraryEntry? = null, val season: String? = null,
    val playable: BrowserChannel? = null) {
    val key get() = "$route:$kind:$category:${entry?.id}:$season"
}
data class LibraryRow(val key: String, val label: String, val entry: LibraryEntry? = null,
    val channel: BrowserChannel? = null, val category: LibraryCategory? = null, val season: String? = null,
    val progress: WatchProgress = WatchProgress())
data class LibraryBookmark(val focus: String? = null, val first: Int = 0, val offset: Int = 0)
data class LibraryPage(val location: LibraryLocation = LibraryLocation(), val rows: List<LibraryRow> = emptyList(),
    val details: LibraryDetails? = null, val progress: WatchProgress = WatchProgress(), val loading: Boolean = false,
    val failed: Boolean = false, val page: Int = 0, val more: Boolean = false,
    val sort: LibrarySort = LibrarySort.PROVIDER, val unwatched: Boolean = false, val bookmark: LibraryBookmark = LibraryBookmark(),
    val nextEpisode: LibraryEntry? = null)

@HiltViewModel
class LibraryViewModel @Inject constructor(private val repository: LibraryRepository,
    private val recents: RecentlyWatchedManager, private val preferences: BrowserPreferences) : ViewModel() {
    private val mutable = MutableStateFlow(LibraryPage())
    val state = mutable.asStateFlow()
    private val parents = mutableListOf<LibraryPage>()
    private var job: Job? = null
    private var filter = true
    private var account: LibraryAccount? = null
    var externalReturn = false
        private set
    private var externalKey: String? = null
    fun activate(filterAdult: Boolean) {
        val active = runCatching { repository.account() }.getOrNull() ?: return
        if (account?.cache != active.cache) {
            job?.cancel(); parents.clear(); externalReturn = false
            mutable.value = LibraryPage()
            account = active
        }
        val changed = filter != filterAdult
        filter = filterAdult
        if (changed && mutable.value.location.entry?.let { hidden(active, it) } == true) root()
        else load()
    }
    private fun hidden(account: LibraryAccount, entry: LibraryEntry) = filter &&
        repository.resolve(account, entry).channel.groupTitle.contains("adult", true)
    fun root() { job?.cancel(); parents.clear(); externalReturn = false; mutable.value = LibraryPage(); load() }
    fun openRoot(route: LibraryRoute, kind: LibraryKind = LibraryKind.MOVIE) = navigate(LibraryLocation(route, kind))
    fun fallback() = navigate(LibraryLocation(LibraryRoute.PLAYLIST))
    fun external(entry: LibraryEntry, filterAdult: Boolean) {
        activate(filterAdult)
        val a = account ?: return
        if (hidden(a, entry)) return
        externalReturn = true
        val target = LibraryLocation(LibraryRoute.DETAIL, entry.kind, label = entry.name, entry = entry)
        externalKey = target.key
        navigate(target)
    }
    fun back(): Boolean {
        if (parents.isEmpty()) return false
        job?.cancel(); mutable.value = parents.removeAt(parents.lastIndex); load(); return true
    }
    fun consumeExternalReturn(): Boolean {
        if (!externalReturn || mutable.value.location.key != externalKey) return false
        externalReturn = false; back(); return true
    }
    private fun navigate(location: LibraryLocation) {
        parents.add(mutable.value)
        job?.cancel(); mutable.value = LibraryPage(location); load()
    }
    fun open(row: LibraryRow) {
        val location = state.value.location
        when {
            row.category != null -> navigate(LibraryLocation(LibraryRoute.TITLES, location.kind,
                category = row.category.id.takeIf { it.isNotEmpty() }, label = row.category.name))
            row.season != null -> navigate(location.copy(route = LibraryRoute.EPISODES, season = row.season, label = row.label))
            row.entry != null -> navigate(LibraryLocation(LibraryRoute.DETAIL, row.entry.kind, label = row.entry.name, entry = row.entry))
            row.channel != null -> navigate(LibraryLocation(LibraryRoute.DETAIL, label = row.label,
                entry = LibraryEntry(LibraryKind.MOVIE, row.key, row.label, categoryName = row.channel.channel.groupTitle,
                    poster = row.channel.channel.logoUrl), playable = row.channel))
        }
    }
    fun seasons() { navigate(state.value.location.copy(route = LibraryRoute.SEASONS)) }
    fun focus(key: String) { mutable.value = state.value.copy(bookmark = state.value.bookmark.copy(focus = key)) }
    fun scroll(first: Int, offset: Int) { mutable.value = state.value.copy(bookmark = state.value.bookmark.copy(first = first, offset = offset)) }
    fun page(delta: Int) { mutable.value = state.value.copy(page = (state.value.page + delta).coerceAtLeast(0), bookmark = LibraryBookmark()); load() }
    fun sort() {
        val a = account ?: return
        val value = LibrarySort.values()[(state.value.sort.ordinal + 1) % LibrarySort.values().size]
        val kind = state.value.location.kind
        job?.cancel()
        mutable.value = state.value.copy(sort = value, page = 0)
        job = viewModelScope.launch { repository.local { if (repository.valid(a)) repository.database.saveSort(a.owner, kind, value) }; load() }
    }
    fun unwatched() { mutable.value = state.value.copy(unwatched = !state.value.unwatched, page = 0); load() }
    fun mark(channel: M3UChannel, watched: Boolean, key: String? = null, done: () -> Unit = {}) {
        val a = account ?: return
        viewModelScope.launch {
            repository.flush(); repository.reset(a, channel, watched, key)
            if (repository.valid(a)) { load(); done() }
        }
    }
    suspend fun watched(row: LibraryRow): Boolean? {
        val a = account ?: return null
        val resolved = channel(row) ?: return null
        repository.flush()
        return runCatching { repository.progress(a, resolved.channel, resolved.id).watched }.getOrNull()
            ?.takeIf { repository.valid(a) }
    }
    fun channel(row: LibraryRow): BrowserChannel? = row.channel ?: account?.let { a ->
        state.value.location.playable ?: row.entry?.takeIf { it.kind != LibraryKind.SERIES }?.let { repository.resolve(a, it) }
    }
    fun playbackKey(): String? {
        val location = state.value.location
        return location.playable?.id ?: account?.let { a -> location.entry?.let { repository.resolve(a, it).id } }
    }
    fun favorite(channel: BrowserChannel) {
        val a = account ?: return
        viewModelScope.launch {
            val saved = preferences.load()
            if (!repository.valid(a) || saved.account != a.owner) return@launch
            val aliases = repository.favoriteReferences(a, saved.favorites).filterValues { it.id == channel.id }.keys
            if (aliases.isEmpty()) { repository.retainPlayable(a, channel); preferences.toggle(channel.id, a.owner) }
            else aliases.forEach { preferences.toggle(it, a.owner) }
            load()
        }
    }
    fun nextEpisode() { state.value.nextEpisode?.let { open(LibraryRow(it.id, it.name, it)) } }
    fun play(restart: Boolean, action: (Int, List<M3UChannel>) -> Unit) {
        val a = account ?: return
        val entry = state.value.location.entry ?: return
        if (entry.kind == LibraryKind.SERIES) return
        val channel = (state.value.location.playable ?: repository.resolve(a, entry)).channel
        val key = playbackKey()
        if (filter && channel.groupTitle.contains("adult", true)) return
        viewModelScope.launch {
            repository.flush()
            if (state.value.location.entry != entry) return@launch
            if (restart || !state.value.progress.resumable) repository.reset(a, channel, legacyKey = key)
            if (repository.valid(a)) action(0, listOf(channel))
        }
    }
    fun refresh() = load(force = true)
    fun load(force: Boolean = false) {
        val a = account ?: return
        val requested = state.value
        val location = requested.location
        job?.cancel()
        if (location.route == LibraryRoute.ROOT || location.route == LibraryRoute.PLAYLIST) return
        job = viewModelScope.launch {
          try {
            repository.flush()
            if (location.route == LibraryRoute.RECENT) {
                val references = recents.references(a.owner)
                val resolved = repository.supplemental(a, references)
                if (repository.valid(a)) recents.resolve(a.owner,
                    resolved.associate { a.key(it.first.kind, it.first.id) to it.second.channel })
            }
            fun publish(page: LibraryPage) { if (repository.valid(a) && state.value.location == location) mutable.value = page.copy(bookmark = state.value.bookmark) }
            suspend fun cached(loading: Boolean, failed: Boolean): LibraryPage = repository.local {
                val db = repository.database
                val sort = db.sort(a.owner, location.kind)
                val categoryPage = if (location.route == LibraryRoute.CATEGORIES) db.categories(a.cache, location.kind, filter,
                    requested.page * LibraryDatabase.PAGE_SIZE) else emptyList()
                val seasonPage = if (location.route == LibraryRoute.SEASONS) db.seasons(a, location.entry!!.id,
                    requested.page * LibraryDatabase.PAGE_SIZE) else emptyList()
                val entries = when (location.route) {
                    LibraryRoute.TITLES -> db.page(a, location.kind, location.category, filter = false, sort = sort,
                        unwatched = requested.unwatched, offset = requested.page * LibraryDatabase.PAGE_SIZE)
                    LibraryRoute.EPISODES -> db.page(a, LibraryKind.EPISODE, series = location.entry?.id,
                        season = location.season, filter = false, unwatched = requested.unwatched,
                        offset = requested.page * LibraryDatabase.PAGE_SIZE)
                    else -> emptyList()
                }
                repository.retainPageAliases(a, entries + listOfNotNull(location.entry?.takeIf { location.playable == null }))
                val rows = when (location.route) {
                    LibraryRoute.CATEGORIES -> (if (requested.page == 0) listOf(LibraryRow("all", "", category = LibraryCategory("", ""))) else emptyList()) +
                        categoryPage.map { LibraryRow("category:${it.id}", it.name, category = it) }
                    LibraryRoute.SEASONS -> seasonPage.map { LibraryRow("season:$it", it, season = it) }
                    LibraryRoute.CONTINUE -> db.continuing(a.owner, filter, requested.page * LibraryDatabase.PAGE_SIZE).mapNotNull { (key, channel) ->
                        val restored = repository.restored(a, key, channel)
                        if (filter && restored.channel.groupTitle.contains("adult", true)) return@mapNotNull null
                        val route = repository.identity(a).route(restored.channel)
                        val entry = route?.let { db.find(a, it.first, it.second) } ?: repository.retainedEntry(a, key)
                        LibraryRow(key, restored.channel.name, entry, restored, progress = db.progress(a.owner, key))
                    }
                    LibraryRoute.RECENT -> recents.recentChannels.value.filter { it.isVod && (!filter || !it.groupTitle.contains("adult", true)) }.map { channel ->
                        val route = repository.identity(a).route(channel)
                        val entry = route?.let { db.find(a, it.first, it.second) }
                        LibraryRow(repository.key(a, channel), channel.name, entry, BrowserChannel(repository.key(a, channel), channel))
                    }
                    else -> entries.mapNotNull { entry ->
                        val resolved = repository.resolve(a, entry)
                        val progress = if (entry.kind != LibraryKind.SERIES) db.progress(a.owner, repository.key(a, resolved.channel, resolved.id)) else WatchProgress()
                        if (hidden(a, entry) || (requested.unwatched && progress.watched)) null else LibraryRow(resolved.id, entry.name, entry, resolved, progress = progress)
                    }
                }
                val selected = location.entry
                val progress = selected?.takeIf { it.kind != LibraryKind.SERIES }?.let {
                    val resolved = location.playable ?: repository.resolve(a, it)
                    db.progress(a.owner, repository.key(a, resolved.channel, resolved.id))
                } ?: WatchProgress()
                val details = selected?.let { db.details(a, it, System.currentTimeMillis())?.first }
                requested.copy(rows = rows, sort = sort, details = details, progress = progress,
                    loading = loading, failed = failed, nextEpisode = selected?.takeIf { it.kind == LibraryKind.EPISODE && progress.watched }?.let { db.nextEpisode(a, it) },
                    more = entries.size == LibraryDatabase.PAGE_SIZE || categoryPage.size == LibraryDatabase.PAGE_SIZE || seasonPage.size == LibraryDatabase.PAGE_SIZE ||
                        (location.route == LibraryRoute.CONTINUE && rows.size == LibraryDatabase.PAGE_SIZE))
            }
            val needsListing = location.route == LibraryRoute.CATEGORIES || location.route == LibraryRoute.TITLES
            val needsDetail = location.route == LibraryRoute.DETAIL && location.entry?.kind != LibraryKind.EPISODE && location.playable == null
            publish(cached(needsListing || needsDetail, false))
            val success = when {
                needsListing -> repository.refresh(a, location.kind, force)
                needsDetail -> repository.details(a, location.entry!!, force)
                else -> true
            }
            publish(cached(false, !success))
          } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) {
              if (repository.valid(a) && state.value.location == location) mutable.value = state.value.copy(loading = false, failed = true)
          }
        }
    }
}
