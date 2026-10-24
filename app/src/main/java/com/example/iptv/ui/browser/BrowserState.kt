package com.example.iptv.ui.browser

import com.example.iptv.data.model.M3UChannel
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GridDensity(val columns: Int, val rows: Int) {
    LARGE(4, 3), STANDARD(5, 4), COMPACT(6, 4)
}

enum class BrowserDestination {
    CATEGORIES, VOD, FAVORITES, SEARCH, SETTINGS;

    val isCategorySection: Boolean get() = this == CATEGORIES || this == VOD
}
enum class BrowserFocus { GRID, SIDEBAR, QUERY, DENSITY, ACCOUNT, ADULT_FILTER }

data class BrowserLocation(
    val destination: BrowserDestination = BrowserDestination.CATEGORIES,
    val categoryId: String? = null
) {
    val key: String get() = if (destination.isCategorySection) "${destination.name}:${categoryId ?: "root"}" else destination.name
}

data class GridBookmark(
    val focusedId: String? = null,
    val focusedIndex: Int = 0,
    val firstVisibleId: String? = null,
    val firstVisibleIndex: Int = 0,
    val scrollOffset: Int = 0,
    val area: BrowserFocus = BrowserFocus.GRID,
    val sidebarIndex: Int = 0
)

data class BrowserChannel(val id: String, val channel: M3UChannel,
    val library: com.example.iptv.data.library.LibraryEntry? = null)
data class BrowserCategory(val id: String, val name: String, val channels: List<BrowserChannel>)
data class BrowserTile(val id: String, val category: BrowserCategory? = null, val entry: BrowserChannel? = null)

private fun BrowserChannel.visible(filterAdult: Boolean) = !filterAdult || !channel.groupTitle.contains("adult", ignoreCase = true)

internal fun privateReference(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    val hex = "0123456789abcdef"
    return buildString(bytes.size * 2) {
        bytes.forEach { byte -> val unsigned = byte.toInt() and 255; append(hex[unsigned ushr 4]); append(hex[unsigned and 15]) }
    }
}

/** Session-only catalog. Public state keys never contain an authenticated stream address. */
class BrowserCatalog private constructor(
    val channels: List<BrowserChannel>,
    val categories: List<BrowserCategory>,
    private val liveCategories: List<BrowserCategory>,
    private val vodCategories: List<BrowserCategory>,
    private val byStream: Map<String, BrowserChannel>,
    private val duplicateStreams: Map<String, List<BrowserChannel>>
) {
    fun withRecent(recent: List<M3UChannel>, section: BrowserDestination = BrowserDestination.CATEGORIES,
                   filterAdult: Boolean = true): List<BrowserCategory> {
        val vod = section == BrowserDestination.VOD
        val resolved = recent.mapNotNull { item ->
            duplicateStreams[item.streamUrl]?.lastOrNull {
                it.channel.groupTitle == item.groupTitle && it.channel.userAgent == item.userAgent
            } ?: byStream[item.streamUrl]
        }.filter { it.channel.isVod == vod && it.visible(filterAdult) }
        val groups = if (vod) vodCategories else liveCategories
        return listOf(BrowserCategory("recent", "Recently viewed", resolved)) +
            groups.filter { !filterAdult || !it.name.contains("adult", ignoreCase = true) }
    }

    fun search(query: String): List<BrowserChannel> = if (query.isBlank()) emptyList()
        else channels.filter { it.channel.name.contains(query.trim(), ignoreCase = true) }

    companion object {
        fun create(source: List<M3UChannel>): BrowserCatalog {
            val occurrences = mutableMapOf<String, Int>()
            // Hash once per distinct stream/group identity, not once per focus movement.
            val entries = source.map { channel ->
                val identity = listOf(channel.streamUrl, channel.userAgent.orEmpty(), channel.groupTitle)
                    .joinToString("") { "${it.length}:$it" }
                val occurrence = occurrences[identity] ?: 0
                occurrences[identity] = occurrence + 1
                BrowserChannel(privateReference(identity) + ":$occurrence", channel)
            }
            val groups = entries.groupBy { it.channel.groupTitle }.map { (name, values) ->
                BrowserCategory("group:$name", name, values)
            }.sortedWith(compareBy({ !it.name.startsWith("TR", ignoreCase = true) }, { it.name }))
            val live = mutableListOf<BrowserCategory>()
            val vod = mutableListOf<BrowserCategory>()
            groups.forEach { group ->
                val (onDemand, television) = group.channels.partition { it.channel.isVod }
                if (television.isNotEmpty()) live.add(group.copy(channels = television))
                if (onDemand.isNotEmpty()) vod.add(group.copy(channels = onDemand))
            }
            val byStream = mutableMapOf<String, BrowserChannel>()
            val duplicates = mutableMapOf<String, MutableList<BrowserChannel>>()
            entries.forEach { entry ->
                byStream.put(entry.channel.streamUrl, entry)?.let { previous ->
                    duplicates.getOrPut(entry.channel.streamUrl) { mutableListOf(previous) }.add(entry)
                }
            }
            return BrowserCatalog(entries, groups, live, vod, byStream, duplicates)
        }
    }
}

data class BrowserState(
    val location: BrowserLocation = BrowserLocation(),
    val items: List<BrowserTile> = emptyList(),
    val categories: List<BrowserCategory> = emptyList(),
    val bookmarks: Map<String, GridBookmark> = mapOf(BrowserLocation().key to GridBookmark(area = BrowserFocus.SIDEBAR)),
    val query: String = "",
    val density: GridDensity = GridDensity.STANDARD,
    val favorites: Set<String> = emptySet(),
    val filterAdult: Boolean = true
) {
    val bookmark: GridBookmark get() = bookmarks[location.key] ?: GridBookmark(area = when (location.destination) {
        BrowserDestination.SEARCH -> BrowserFocus.QUERY
        BrowserDestination.SETTINGS -> BrowserFocus.DENSITY
        else -> BrowserFocus.GRID
    })
    val activeSidebar: Int get() = location.destination.ordinal
    val canGoBack: Boolean get() = !location.destination.isCategorySection || location.categoryId != null
}

/** Retained by the activity's ViewModel, never serialized into Android saved-instance bundles. */
class BrowserController {
    private val mutableState = MutableStateFlow(BrowserState())
    val state = mutableState.asStateFlow()
    private var catalog: BrowserCatalog? = null
    private var recent: List<M3UChannel> = emptyList()
    private var favoritesReturn = BrowserLocation()
    private var lastCategories = BrowserLocation()
    private val auxiliaryReturns = mutableListOf<BrowserLocation>()
    private var searchResults = emptyList<BrowserChannel>()
    private var extraFavorites = emptyList<BrowserChannel>()
    private var extraSearch = emptyList<BrowserChannel>()
    private var libraryReturn: BrowserLocation? = null

    fun openLibraryDetails() { libraryReturn = state.value.location; navigate(BrowserLocation(BrowserDestination.VOD)) }
    fun closeLibraryDetails() { val target = libraryReturn ?: return; libraryReturn = null; navigate(target) }

    fun supplemental(favorites: List<BrowserChannel>, search: List<BrowserChannel> = emptyList()) {
        extraFavorites = favorites; extraSearch = search; render()
    }

    fun reset() {
        catalog = null
        libraryReturn = null
        extraFavorites = emptyList(); extraSearch = emptyList()
        auxiliaryReturns.clear()
        favoritesReturn = BrowserLocation()
        lastCategories = BrowserLocation()
        searchResults = emptyList()
        recent = emptyList()
        mutableState.value = BrowserState()
    }

    fun load(catalog: BrowserCatalog, recent: List<M3UChannel>) {
        this.catalog = catalog
        this.recent = recent
        // Search against the new directory, retaining the current query and its bookmark.
        searchResults = catalog.search(state.value.query)
        render()
    }

    fun updateRecent(recent: List<M3UChannel>) {
        if (catalog == null) return
        this.recent = recent
        render()
    }

    fun preferences(density: GridDensity, favorites: Set<String>, filterAdult: Boolean = state.value.filterAdult) {
        mutableState.value = state.value.copy(density = density, favorites = favorites, filterAdult = filterAdult)
        render()
    }

    fun openCategory(id: String) {
        val current = state.value
        if (!current.location.destination.isCategorySection || current.categories.none { it.id == id }) return
        navigate(BrowserLocation(current.location.destination, id))
    }

    fun open(destination: BrowserDestination) {
        val current = state.value.location
        if (destination == current.destination && (!destination.isCategorySection || current.categoryId == null)) return
        when (destination) {
            BrowserDestination.CATEGORIES, BrowserDestination.VOD -> auxiliaryReturns.clear()
            BrowserDestination.FAVORITES -> {
                favoritesReturn = if (current.destination.isCategorySection) current else lastCategories
                auxiliaryReturns.clear()
            }
            BrowserDestination.SEARCH, BrowserDestination.SETTINGS -> auxiliaryReturns.add(current)
        }
        navigate(BrowserLocation(destination))
    }

    fun back(): Boolean {
        val current = state.value.location
        val target = when (current.destination) {
            BrowserDestination.CATEGORIES, BrowserDestination.VOD ->
                if (current.categoryId == null) return false else BrowserLocation(current.destination)
            BrowserDestination.FAVORITES -> favoritesReturn
            else -> if (auxiliaryReturns.isEmpty()) BrowserLocation() else auxiliaryReturns.removeAt(auxiliaryReturns.lastIndex)
        }
        navigate(target)
        return true
    }

    private fun navigate(location: BrowserLocation) {
        if (location.destination.isCategorySection) lastCategories = location
        mutableState.value = state.value.copy(location = location)
        render()
    }

    fun query(value: String) {
        extraSearch = emptyList()
        searchResults = emptyList()
        mutableState.value = state.value.copy(query = value)
        // A new query starts a new result collection, while its field retains focus.
        saveBookmark("SEARCH", GridBookmark(area = BrowserFocus.QUERY))
        render()
    }

    // Keep worker results reversible; render applies the latest preference, even to late results.
    fun search(query: String): List<BrowserChannel> = catalog?.search(query).orEmpty()

    fun searchResults(query: String, results: List<BrowserChannel>) {
        if (state.value.query != query) return
        searchResults = results
        render()
    }

    fun focusGrid(id: String) {
        val index = state.value.items.indexOfFirst { it.id == id }
        if (index >= 0) bookmark(state.value.bookmark.copy(focusedId = id, focusedIndex = index, area = BrowserFocus.GRID))
    }

    fun focusArea(area: BrowserFocus, sidebar: Int = state.value.activeSidebar, id: String? = null) {
        bookmark(state.value.bookmark.copy(area = area, sidebarIndex = sidebar,
            focusedId = if (area == BrowserFocus.DENSITY) id else state.value.bookmark.focusedId))
    }

    fun scroll(firstIndex: Int, offset: Int) {
        if (firstIndex < 0) return
        bookmark(state.value.bookmark.copy(firstVisibleId = state.value.items.getOrNull(firstIndex)?.id,
            firstVisibleIndex = firstIndex, scrollOffset = offset))
    }

    private fun bookmark(value: GridBookmark) = saveBookmark(state.value.location.key, value)
    private fun saveBookmark(key: String, value: GridBookmark) {
        if (state.value.bookmarks[key] != value) mutableState.value = state.value.copy(bookmarks = state.value.bookmarks + (key to value))
    }

    private fun render() {
        var current = state.value
        val section = if (current.location.destination.isCategorySection) current.location.destination else lastCategories.destination
        current = current.copy(categories = catalog?.withRecent(recent, section, current.filterAdult).orEmpty())
        if (current.location.destination.isCategorySection && current.location.categoryId != null &&
            current.categories.none { it.id == current.location.categoryId }) {
            current = current.copy(location = BrowserLocation(current.location.destination))
            lastCategories = current.location
        }
        val items = when (current.location.destination) {
            BrowserDestination.CATEGORIES, BrowserDestination.VOD -> if (current.location.categoryId == null) {
                current.categories.map { BrowserTile(it.id, category = it) }
            } else current.categories.find { it.id == current.location.categoryId }?.channels.orEmpty().map { BrowserTile(it.id, entry = it) }
            BrowserDestination.FAVORITES -> (catalog?.channels.orEmpty().filter { it.id in current.favorites }.map { old -> extraFavorites.find { it.id == old.id } ?: old } + extraFavorites)
                .distinctBy { it.id }.filter { it.visible(current.filterAdult) }.map { BrowserTile(it.id, entry = it) }
            BrowserDestination.SEARCH -> (searchResults.map { old -> extraSearch.find { it.id == old.id } ?: old } + extraSearch).distinctBy { it.id }
                .filter { it.visible(current.filterAdult) }.map { BrowserTile(it.id, entry = it) }
            BrowserDestination.SETTINGS -> emptyList()
        }
        val saved = current.bookmark
        val reconciled = if (saved.area != BrowserFocus.GRID) saved else if (items.isEmpty()) {
            saved.copy(area = if (current.location.destination == BrowserDestination.SEARCH) BrowserFocus.QUERY else BrowserFocus.SIDEBAR,
                sidebarIndex = current.activeSidebar, focusedId = null, focusedIndex = 0, firstVisibleId = null, firstVisibleIndex = 0, scrollOffset = 0)
        } else {
            val focus = restoredIndex(items, saved.focusedId, saved.focusedIndex)
            val first = restoredIndex(items, saved.firstVisibleId, saved.firstVisibleIndex)
            saved.copy(focusedId = items[focus].id, focusedIndex = focus, firstVisibleId = items[first].id, firstVisibleIndex = first)
        }
        mutableState.value = current.copy(items = items, bookmarks = current.bookmarks + (current.location.key to reconciled))
    }
}

internal fun restoredIndex(items: List<BrowserTile>, id: String?, fallback: Int): Int =
    items.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: fallback.coerceIn(0, (items.size - 1).coerceAtLeast(0))

internal fun gridTarget(index: Int, size: Int, columns: Int, horizontal: Int = 0, vertical: Int = 0): Int? {
    if (index !in 0 until size) return null
    if (horizontal != 0 && index / columns != (index + horizontal) / columns) return null
    if (horizontal < 0 && index % columns == 0) return null
    return (index + horizontal + vertical * columns).takeIf { it in 0 until size }
}

internal fun firstRowForTarget(target: Int, first: Int, columns: Int, rows: Int): Int {
    val row = target / columns
    val firstRow = first / columns
    return when {
        row < firstRow -> row * columns
        row >= firstRow + rows -> (row - rows + 1) * columns
        else -> firstRow * columns
    }
}
