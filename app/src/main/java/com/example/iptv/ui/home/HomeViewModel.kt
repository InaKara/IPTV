package com.example.iptv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iptv.data.model.ChannelGroup
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.RecentlyWatchedManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class HomeViewModel @Inject constructor(private val recents: RecentlyWatchedManager) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var source: List<M3UChannel>? = null
    private var regularGroups = emptyList<ChannelGroup>()
    private var refresh: Job? = null
    init {
        viewModelScope.launch {
            recents.recentChannels.collect { channels -> if (source != null) updateGroups(channels) }
        }
    }

    private fun updateGroups(recent: List<M3UChannel>) {
        val groups = listOf(ChannelGroup("Recently viewed", recent)) + regularGroups
        val selectedName = (_uiState.value as? HomeUiState.Success)?.selectedGroup?.name
        _uiState.value = HomeUiState.Success(groups, groups.find { it.name == selectedName } ?: groups.first())
    }

    fun loadChannels(channels: List<M3UChannel>) {
        refresh?.cancel()
        refresh = viewModelScope.launch {
            if (source !== channels) {
                regularGroups = withContext(Dispatchers.Default) {
                    channels.groupBy { it.groupTitle }.map { (name, entries) -> ChannelGroup(name, entries) }
                        .sortedWith(compareBy({ !it.name.startsWith("TR", ignoreCase = true) }, { it.name }))
                }
                source = channels
            }
            updateGroups(recents.recentChannels.value)
        }
    }

    fun selectGroup(group: ChannelGroup) {
        val current = _uiState.value as? HomeUiState.Success ?: return
        _uiState.value = current.copy(selectedGroup = group)
    }
}

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val groups: List<ChannelGroup>, val selectedGroup: ChannelGroup) : HomeUiState()
}
