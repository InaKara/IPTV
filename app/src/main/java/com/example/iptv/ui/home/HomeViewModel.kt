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
import com.example.iptv.diagnostics.StartupTimings
import com.example.iptv.diagnostics.StartupTimings.Outcome
import com.example.iptv.diagnostics.StartupTimings.Phase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recents: RecentlyWatchedManager,
    private val startup: StartupTimings = StartupTimings(),
    private val preferences: BrowserPreferences? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var source: List<M3UChannel>? = null
    private var regularGroups = emptyList<ChannelGroup>()
    private var refresh: Job? = null
    val browser = BrowserController()
    private var searchJob: Job? = null
    private var preferenceWrite: Job? = null
    private var browserAccount: String? = null
    private var playbackIds = emptyList<String>()
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
                regularGroups = index.categories.map { category -> ChannelGroup(category.name, category.channels.map { it.channel }) }
                preferences?.load()?.let { saved ->
                    if (browserAccount != null && browserAccount != saved.account) browser.reset()
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
    }

    fun search(query: String) {
        searchJob?.cancel()
        browser.query(query)
        searchJob = viewModelScope.launch {
            val results = withContext(Dispatchers.Default) { browser.search(query) }
            browser.searchResults(query, results)
        }
    }

    fun toggleFavorite(id: String) {
        val preceding = preferenceWrite
        preferenceWrite = viewModelScope.launch {
            preceding?.join()
            if (preferences == null) {
                val current = browser.state.value
                browser.preferences(current.density, if (id in current.favorites) current.favorites - id else current.favorites + id)
            } else preferences.toggle(id).let { browser.preferences(it.density, it.favorites, it.filterAdult) }
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

    fun preparePlayback(index: Int) {
        playbackIds = browser.state.value.items.map { it.id }
        playbackIds.getOrNull(index)?.let(browser::focusGrid)
    }

    fun returnFromPlayback(index: Int?) {
        index?.let { playbackIds.getOrNull(it) }?.let(browser::focusGrid)
    }

}

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val groups: List<ChannelGroup>) : HomeUiState()
}
