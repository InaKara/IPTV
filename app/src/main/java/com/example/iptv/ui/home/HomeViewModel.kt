package com.example.iptv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iptv.data.model.ChannelGroup
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.RecentlyWatchedManager
import com.example.iptv.data.storage.BrowserPreferences
import com.example.iptv.ui.browser.BrowserCatalog
import com.example.iptv.ui.browser.BrowserController
import com.example.iptv.ui.browser.GridDensity
import com.example.iptv.ui.browser.BrowserDestination
import com.example.iptv.ui.browser.BrowserChannel
import com.example.iptv.diagnostics.StartupTimings
import com.example.iptv.diagnostics.StartupTimings.Outcome
import com.example.iptv.diagnostics.StartupTimings.Phase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recents: RecentlyWatchedManager,
    private val startup: StartupTimings = StartupTimings(),
    private val preferences: BrowserPreferences? = null,
    private val library: com.example.iptv.data.library.LibraryRepository? = null,
    private val logging: com.example.iptv.diagnostics.VerboseLogging? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState = _uiState.asStateFlow()
    val verboseLogging = logging?.enabled ?: MutableStateFlow(false).asStateFlow()
    fun setVerboseLogging(value: Boolean) { logging?.setEnabled(value) }
    private var source: List<M3UChannel>? = null
    private var regularGroups = emptyList<ChannelGroup>()
    private var refresh: Job? = null
    val browser = BrowserController()
    private var searchJob: Job? = null
    private var preferenceWrite: Job? = null
    private var browserAccount: String? = null
    private var playbackIds = emptyList<String>()
    private var playbackCollection: List<M3UChannel>? = null
    private var favoriteAliases: Map<String, Set<String>> = emptyMap()
    private val firstLayout = MutableStateFlow(false)
    init {
        viewModelScope.launch {
            recents.recentChannels.collect { channels -> if (source != null) updateGroups(channels) }
        }
    }

    private fun updateGroups(recent: List<M3UChannel>) {
        val groups = listOf(ChannelGroup("Recently viewed", recent)) + regularGroups
        _uiState.value = HomeUiState.Success(groups)
        browser.updateRecent(recent)
    }

    fun loadChannels(channels: List<M3UChannel>) {
        refresh?.cancel()
        refresh = viewModelScope.launch {
            if (source !== channels) {
                val timing = startup.forPlaylist(channels)
                val groupingStarted = timing?.now() ?: 0L
                val index = withContext(Dispatchers.Default) { BrowserCatalog.create(channels) }
                library?.let { repo -> runCatching { repo.account() }.getOrNull()?.let { repo.activate(it, index) } }
                regularGroups = index.categories.map { category -> ChannelGroup(category.name, category.channels.map { it.channel }) }
                preferences?.load()?.let { saved ->
                    if (browserAccount != null && browserAccount != saved.account) {
                        browser.reset(); favoriteAliases = emptyMap(); firstLayout.value = false
                    }
                    browserAccount = saved.account
                    browser.preferences(saved.density, saved.favorites, saved.filterAdult)
                }
                searchJob?.cancel()
                browser.load(index, recents.recentChannels.value)
                source = channels
                timing?.record(Phase.GROUPING, groupingStarted)
            }
            updateGroups(recents.recentChannels.value)
        }
    }

    fun onBrowseReady() {
        source?.let { startup.forPlaylist(it)?.finish(Outcome.BROWSE_READY) }
        if (source != null) firstLayout.value = true
    }
    suspend fun awaitBrowseReady() { firstLayout.first { it } }

    fun search(query: String) {
        searchJob?.cancel()
        browser.query(query)
        searchJob = viewModelScope.launch {
            val results = withContext(Dispatchers.Default) { browser.search(query) }
            browser.searchResults(query, results)
            syncLibrary(query)
        }
    }

    fun toggleFavorite(id: String) {
        val preceding = preferenceWrite
        preferenceWrite = viewModelScope.launch {
            preceding?.join()
            if (preferences == null) {
                val current = browser.state.value
                browser.preferences(current.density, if (id in current.favorites) current.favorites - id else current.favorites + id)
            } else {
                val ids = favoriteAliases[id].orEmpty().ifEmpty { setOf(id) }
                ids.forEach { key -> preferences.toggle(key, browserAccount).let { browser.preferences(it.density, it.favorites, it.filterAdult) } }
                val repo = library
                val account = repo?.let { runCatching { it.account() }.getOrNull() }
                if (repo != null && account != null) browser.state.value.items.find { it.id == id }?.entry?.let { repo.retainPlayable(account, it) }
                syncLibrary()
            }
        }
    }

    fun setDensity(density: GridDensity) {
        val preceding = preferenceWrite
        preferenceWrite = viewModelScope.launch {
            preceding?.join()
            if (preferences == null) browser.preferences(density, browser.state.value.favorites)
            else preferences.density(density).let { browser.preferences(it.density, it.favorites, it.filterAdult) }
        }
    }

    fun toggleAdultFilter() {
        val preceding = preferenceWrite
        preferenceWrite = viewModelScope.launch {
            preceding?.join()
            val current = browser.state.value
            if (preferences == null) browser.preferences(current.density, current.favorites, !current.filterAdult)
            else preferences.adultFilter(!current.filterAdult).let { browser.preferences(it.density, it.favorites, it.filterAdult) }
        }
    }

    fun preparePlayback(index: Int, items: List<com.example.iptv.ui.browser.BrowserTile> = browser.state.value.items): List<M3UChannel> {
        playbackIds = items.map { it.id }
        playbackIds.getOrNull(index)?.let(browser::focusGrid)
        return items.mapNotNull { it.entry?.channel }.also { playbackCollection = it }
    }
    fun prepareLibraryPlayback(channels: List<M3UChannel>, key: String?) {
        playbackCollection = channels; playbackIds = listOfNotNull(key)
    }
    fun playbackIdentities(channels: List<M3UChannel>): List<String> = if (playbackCollection === channels) playbackIds else emptyList()

    fun returnFromPlayback(index: Int?) {
        index?.let { playbackIds.getOrNull(it) }?.let(browser::focusGrid)
    }

    suspend fun syncLibrary(query: String? = browser.state.value.query) {
        if (library == null) return
        val destination = browser.state.value.location.destination
        if (destination !in setOf(BrowserDestination.VOD, BrowserDestination.FAVORITES, BrowserDestination.SEARCH)) return
        awaitBrowseReady()
        if (browser.state.value.location.destination != destination) return
        try { syncLibraryData(query) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Keep the legacy browser usable if optional metadata storage is unavailable. */ }
    }
    suspend fun watched(entry: BrowserChannel): Boolean? {
        val repo = library ?: return null
        val account = runCatching { repo.account() }.getOrNull() ?: return null
        repo.flush()
        return runCatching { repo.progress(account, entry.channel, entry.id).watched }.getOrNull()
            ?.takeIf { repo.valid(account) }
    }
    fun markWatched(entry: BrowserChannel, watched: Boolean, done: () -> Unit) {
        val repo = library ?: return
        val account = runCatching { repo.account() }.getOrNull() ?: return
        viewModelScope.launch {
            repo.flush()
            repo.reset(account, entry.channel, watched, entry.id)
            if (repo.valid(account)) done()
        }
    }
    private suspend fun syncLibraryData(query: String?) {
        val repo = library ?: return
        val account = runCatching { repo.account() }.getOrNull() ?: return
        val saved = preferences?.load() ?: return
        val references = recents.references(account.owner)
        repo.local { repo.ensureAliases(account, saved.favorites + references) }
        saved.favorites.forEach { id -> repo.local { repo.identity(account).legacyId(id) }?.let { repo.retainPlayable(account, it) } }
        val extra = repo.supplemental(account, saved.favorites + references)
        val favoriteReferences = repo.favoriteReferences(account, saved.favorites)
        if (!repo.valid(account)) return
        recents.resolve(account.owner, extra.associate { account.key(it.first.kind, it.first.id) to it.second.channel })
        favoriteAliases = favoriteReferences.entries.groupBy({ it.value.id }, { it.key }).mapValues { it.value.toSet() }
        browser.preferences(saved.density, saved.favorites + favoriteReferences.values.map { it.id }, saved.filterAdult)
        val search = query?.takeIf { it.isNotBlank() }?.let { repo.supplemental(account, emptySet(), it) }.orEmpty()
        if (repo.valid(account) && browser.state.value.query == query.orEmpty()) browser.supplemental(
            favoriteReferences.values.toList(),
            search.map { it.second })
    }

}

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val groups: List<ChannelGroup>) : HomeUiState()
}
