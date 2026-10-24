package com.example.iptv.data.library

import com.example.iptv.data.model.M3UChannel
import com.example.iptv.ui.browser.BrowserChannel
import com.example.iptv.ui.browser.privateReference
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class LibraryKind(val route: String) { MOVIE("movie"), SERIES("series"), EPISODE("series") }
enum class LibrarySort { PROVIDER, NAME, RATING, RECENT }
data class LibraryCategory(val id: String, val name: String)
data class LibraryEntry(
    val kind: LibraryKind, val id: String, val name: String, val category: String = "",
    val categoryName: String = "", val poster: String? = null, val rating: Double? = null,
    val timestamp: Long = 0, val extension: String = "mp4", val ordinal: Int = 0,
    val seriesId: String = "", val season: String = "", val episode: String = "",
    val runtime: String = ""
)
data class LibraryDetails(val synopsis: String = "", val year: String = "", val runtime: String = "",
    val genre: String = "", val cast: String = "", val director: String = "")
data class SeasonFavorite(val seriesId: String, val season: String, val name: String,
    val category: String = "", val categoryName: String = "", val poster: String? = null) {
    fun key(account: LibraryAccount) = "season:${account.owner}:$seriesId:${privateReference(season)}"
    fun series() = LibraryEntry(LibraryKind.SERIES, seriesId, name, category, categoryName, poster)
}
data class WatchProgress(val position: Long = 0, val duration: Long = 0, val watched: Boolean = false,
    val seekable: Boolean = false, val updated: Long = 0) {
    val resumable get() = !watched && seekable && duration > 0 && position >= 30_000
    fun resumePosition(currentDuration: Long, canSeek: Boolean): Long =
        if (resumable && canSeek && currentDuration > 0) position.coerceIn(0, currentDuration) else 0
    companion object {
        fun record(old: WatchProgress, position: Long, duration: Long, seekable: Boolean,
            ended: Boolean, now: Long): WatchProgress {
            val validDuration = duration.coerceAtLeast(0)
            val actual = if (validDuration > 0) position.coerceIn(0, validDuration) else position.coerceAtLeast(0)
            return WatchProgress(actual, validDuration, old.watched || ended ||
                (validDuration > 0 && actual.toDouble() / validDuration >= .95), seekable && validDuration > 0, now)
        }
    }
}

/** Cache ownership includes the password; durable user state deliberately does not. */
class LibraryAccount(val base: HttpUrl, val user: String, val password: String, val generation: Long = 0) {
    val owner = privateReference("$base\n$user")
    val cache = privateReference("$owner\n$password")
    fun key(kind: LibraryKind, id: String) = "library:$owner:${kind.name}:$id"
    fun api(action: String, parameter: Pair<String, String>? = null): HttpUrl = base.newBuilder()
        .addPathSegment("player_api.php").addQueryParameter("username", user)
        .addQueryParameter("password", password).addQueryParameter("action", action).apply {
            parameter?.let { addQueryParameter(it.first, it.second) }
        }.build()
    fun stream(entry: LibraryEntry): String {
        require(entry.kind != LibraryKind.SERIES && entry.id.matches(Regex("[0-9]+")))
        val extension = entry.extension.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "mp4"
        return base.newBuilder().addPathSegment(entry.kind.route).addPathSegment(user)
            .addPathSegment(password).addPathSegment("${entry.id}.$extension").build().toString()
    }
    override fun toString() = "LibraryAccount(redacted)"
    companion object {
        fun create(server: String, user: String, password: String): LibraryAccount {
            val normalized = server.trim().trimEnd('/').let { if ("://" in it) it else "https://$it" }
            val base = requireNotNull(normalized.toHttpUrlOrNull())
            require(base.username.isEmpty() && base.password.isEmpty() && base.query == null && base.fragment == null)
            require(user.isNotBlank() && password.isNotBlank())
            return LibraryAccount(base, user, password)
        }
    }
}

/** Exact origin/account/type/id routes only. Duplicate occurrences remain separate legacy items. */
class LibraryIdentity(private val account: LibraryAccount, private val channels: List<BrowserChannel>) {
    private data class Match(val entry: BrowserChannel?, val ambiguous: Boolean = false)
    private val matches = object : LinkedHashMap<Pair<LibraryKind, String>, Match>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<LibraryKind, String>, Match>?) = size > 256
    }
    // Construction is constant-time. Scan only for an item actually requested, outside shared locks.
    private fun match(key: Pair<LibraryKind, String>): Match {
        synchronized(matches) { matches[key]?.let { return it } }
        var found: BrowserChannel? = null
        var ambiguous = false
        for (entry in channels) {
            if (!entry.channel.isVod || !candidate(entry.channel.streamUrl, key.second) || route(entry.channel) != key) continue
            if (found != null) { ambiguous = true; found = null; break }
            found = entry
        }
        return Match(found, ambiguous).also { synchronized(matches) { matches[key] = it } }
    }
    private fun candidate(url: String, id: String): Boolean {
        val query = url.indexOf('?').let { if (it < 0) url.length else it }
        val fragment = url.indexOf('#').let { if (it < 0) url.length else it }
        val end = minOf(query, fragment)
        val start = url.lastIndexOf('/', end - 1) + 1
        val dot = url.indexOf('.', start).let { if (it < 0 || it > end) end else it }
        // Rare encoded numeric path segments still get full URL validation.
        return (dot - start == id.length && url.regionMatches(start, id, 0, id.length)) ||
            url.indexOf('%', start).let { it >= start && it < end }
    }
    fun route(channel: M3UChannel): Pair<LibraryKind, String>? {
        val url = channel.streamUrl.toHttpUrlOrNull() ?: return null
        if (url.scheme != account.base.scheme || url.host != account.base.host || url.port != account.base.port) return null
        val prefix = account.base.pathSegments.filter { it.isNotEmpty() }
        val path = url.pathSegments
        if (path.take(prefix.size) != prefix || path.size != prefix.size + 4) return null
        val tail = path.drop(prefix.size)
        if (tail[1] != account.user || tail[2] != account.password) return null
        val kind = when (tail[0]) { "movie" -> LibraryKind.MOVIE; "series" -> LibraryKind.EPISODE; else -> return null }
        val id = tail[3].substringBefore('.').takeIf { it.matches(Regex("[0-9]+")) } ?: return null
        return kind to id
    }
    fun legacy(entry: LibraryEntry): BrowserChannel? =
        if (entry.kind == LibraryKind.SERIES) null else match(entry.kind to entry.id).entry
    fun resolve(entry: LibraryEntry): BrowserChannel = legacy(entry) ?: BrowserChannel(account.key(entry.kind, entry.id),
        M3UChannel(name = entry.name, groupTitle = entry.categoryName, logoUrl = entry.poster,
            streamUrl = if (entry.kind == LibraryKind.SERIES) "" else account.stream(entry),
            userAgent = null, tvgId = null, tvgName = null, isVod = true))
    fun legacyId(key: String) = channels.firstOrNull { it.id == key }
    fun aliases(ids: Set<String>): List<Triple<String, LibraryKind, String>> = ids.mapNotNull { id ->
        val entry = legacyId(id) ?: return@mapNotNull null
        val key = route(entry.channel) ?: return@mapNotNull null
        match(key).entry?.takeIf { it.id == id }?.let { Triple(id, key.first, key.second) }
    }
    fun progressKey(channel: M3UChannel, legacyKey: String): String {
        val route = route(channel)
        return if (route != null && !match(route).ambiguous)
            account.key(route.first, route.second) else legacyKey
    }
}
