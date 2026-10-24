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
    val selectedIndex: Int? = null,
    val identities: List<String> = emptyList()
) {
    val selected: M3UChannel? get() = selectedIndex?.let { category.getOrNull(it) }
}

@HiltViewModel
class SessionViewModel @Inject constructor(private val recents: RecentlyWatchedManager,
    private val library: com.example.iptv.data.library.LibraryRepository? = null) : ViewModel() {
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

    fun select(index: Int, category: List<M3UChannel>, identities: List<String> = emptyList()) {
        val channel = category.getOrNull(index) ?: return
        _state.value = _state.value.copy(category = category, selectedIndex = index, identities = identities)
        val preceding = historyWrite
        val account = library?.let { runCatching { it.account() }.getOrNull() }
        historyWrite = viewModelScope.launch {
            preceding?.join()
            val reference = if (channel.isVod && account != null) library?.local { library.key(account, channel, identities.getOrNull(index)) } else null
            recents.addChannel(channel, reference, account?.owner)
        }
    }

    fun step(delta: Int) {
        val state = _state.value
        val next = wrappedChannelIndex(state.selectedIndex ?: return, delta, state.category.size) ?: return
        select(next, state.category, state.identities)
    }

    fun browse() { _state.value = _state.value.copy(selectedIndex = null) }
}
