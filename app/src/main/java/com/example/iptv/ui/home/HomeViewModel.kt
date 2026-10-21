package com.example.iptv.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iptv.data.model.ChannelGroup
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.RecentlyWatchedManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recentlyWatchedManager: RecentlyWatchedManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Store the raw channels to allow refreshing the "Recently viewed" group
    private var allChannels: List<M3UChannel> = emptyList()

    fun loadChannels(channels: List<M3UChannel>) {
        allChannels = channels
        refreshContent()
    }

    fun refreshContent() {
        if (allChannels.isEmpty()) return

        viewModelScope.launch {
            // Keep loading state only for initial load
            if (_uiState.value !is HomeUiState.Success) {
                _uiState.value = HomeUiState.Loading
            }

            val groups = withContext(Dispatchers.Default) {
                val regularGroups = allChannels.groupBy { it.groupTitle }
                    .map { (title, groupChannels) -> ChannelGroup(title, groupChannels) }
                    .sortedWith(compareBy(
                        { !it.name.startsWith("TR", ignoreCase = true) }, // TR first
                        { it.name }                                        // then alphabetical
                    ))

                // Always add "Recently viewed" group at the top
                val recentChannels = recentlyWatchedManager.getRecentChannels()
                val recentGroup = ChannelGroup("Recently viewed", recentChannels)
                listOf(recentGroup) + regularGroups
            }

            if (groups.isNotEmpty()) {
                val currentState = _uiState.value
                val selectedGroup = if (currentState is HomeUiState.Success) {
                    // Try to keep selection or update if it's the "Recently viewed" group
                    groups.find { it.name == currentState.selectedGroup.name } ?: groups.first()
                } else {
                    groups.first()
                }
                
                _uiState.value = HomeUiState.Success(groups, selectedGroup = selectedGroup)
            } else {
                _uiState.value = HomeUiState.Empty
            }
        }
    }

    fun selectGroup(group: ChannelGroup) {
        val currentState = _uiState.value
        if (currentState is HomeUiState.Success) {
            _uiState.value = currentState.copy(selectedGroup = group)
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}

sealed class HomeUiState {
    object Loading : HomeUiState()
    object Empty : HomeUiState()
    data class Success(
        val groups: List<ChannelGroup>,
        val selectedGroup: ChannelGroup
    ) : HomeUiState()
}
