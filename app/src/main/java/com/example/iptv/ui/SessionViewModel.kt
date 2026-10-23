package com.example.iptv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.RecentlyWatchedManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal fun wrappedChannelIndex(index: Int, delta: Int, size: Int): Int? {
    if (size <= 0 || index !in 0 until size) return null
    return Math.floorMod(index + delta, size)
}

data class SessionState(
    val channels: List<M3UChannel> = emptyList(),
    val category: List<M3UChannel> = emptyList(),
    val selectedIndex: Int? = null
) {
    val selected: M3UChannel? get() = selectedIndex?.let { category.getOrNull(it) }
}

@HiltViewModel
class SessionViewModel @Inject constructor(private val recents: RecentlyWatchedManager) : ViewModel() {
    private val _state = MutableStateFlow(SessionState())
    val state = _state.asStateFlow()
    private var historyWrite: Job? = null
    private var accountKey: String? = null

    fun updateChannels(channels: List<M3UChannel>, account: String) {
        if (accountKey != account) {
            historyWrite?.cancel()
            _state.value = SessionState(channels = channels)
            accountKey = account
        } else {
            // Keep the active playback sequence stable until the viewer leaves the player.
            _state.value = _state.value.copy(channels = channels)
        }
    }

    fun select(index: Int, category: List<M3UChannel>) {
        val channel = category.getOrNull(index) ?: return
        _state.value = _state.value.copy(category = category, selectedIndex = index)
        val preceding = historyWrite
        historyWrite = viewModelScope.launch { preceding?.join(); recents.addChannel(channel) }
    }

    fun step(delta: Int) {
        val state = _state.value
        val next = wrappedChannelIndex(state.selectedIndex ?: return, delta, state.category.size) ?: return
        select(next, state.category)
    }

    fun browse() { _state.value = _state.value.copy(selectedIndex = null) }
}
