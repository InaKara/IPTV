package com.example.iptv.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.repository.ChannelRepository
import com.example.iptv.data.storage.ChannelAccount
import com.example.iptv.data.storage.ChannelSnapshot
import com.example.iptv.data.storage.ChannelSnapshots
import com.example.iptv.data.storage.CredentialsManager
import com.example.iptv.data.storage.RecentlyWatchedManager
import com.example.iptv.R
import com.example.iptv.diagnostics.StartupTimings
import com.example.iptv.diagnostics.StartupTimings.Outcome
import com.example.iptv.diagnostics.StartupTimings.Phase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Owns startup/refresh and the editable account draft; successful settings are committed together. */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: ChannelRepository,
    private val credentialsManager: CredentialsManager,
    private val recents: RecentlyWatchedManager,
    private val startup: StartupTimings = StartupTimings(),
    private val snapshots: ChannelSnapshots? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState = _uiState.asStateFlow()
    var startupStatusRes by mutableStateOf(R.string.reading_saved_channels)
        private set
    private var startupAttempt: StartupTimings.Attempt? = null
    private var operation: Job? = null
    private var operationKey: String? = null
    private var editingAccount = false
    private var initialized = false
    private var generation = 0
    private var refreshAfterBrowse = false
    private var currentSnapshot: ChannelSnapshot? = null
    internal var now: () -> Long = System::currentTimeMillis

    var serverUrl by mutableStateOf(credentialsManager.serverUrl)
    var username by mutableStateOf(credentialsManager.username)
    var password by mutableStateOf(credentialsManager.password)
    private fun savedAccount() = ChannelAccount(credentialsManager.serverUrl, credentialsManager.username, credentialsManager.password)

    fun onForeground() {
        if (operation?.isActive == true) return
        if (initialized) {
            if (_uiState.value is LoginUiState.Success && currentSnapshot?.stale(now()) == true) refresh()
            return
        }
        initialized = true
        val account = savedAccount()
        if (!account.complete) { _uiState.value = LoginUiState.Setup; return }
        operationKey = try { account.key } catch (_: IllegalArgumentException) {
            _uiState.value = LoginUiState.Error(R.string.login_failed); return
        }
        val request = ++generation
        _uiState.value = LoginUiState.Loading
        startupStatusRes = R.string.reading_saved_channels
        operation = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val timing = startup.begin().also { startupAttempt = it }
            try {
                val started = timing?.now() ?: 0L
                val cached = snapshots?.read(account)
                currentCoroutineContext().ensureActive()
                if (request != generation) return@launch
                timing?.record(Phase.CACHE, started)
                if (cached != null) {
                    activate(account, cached, timing)
                    currentCoroutineContext().ensureActive()
                    if (request != generation) return@launch
                    currentSnapshot = cached
                    refreshAfterBrowse = cached.stale(now())
                    _uiState.value = LoginUiState.Success(cached.channels, account.key, cached.downloadedAt)
                } else {
                    operation = null
                    load(account, accountEdit = false, startupTiming = timing)
                }
            } catch (error: Exception) {
                timing?.finish(if (error is CancellationException) Outcome.CANCELLED else Outcome.FAILED)
                if (error is CancellationException) throw error
                if (request == generation) _uiState.value = LoginUiState.Error(R.string.login_failed)
            }
        }
        operation?.start()
    }

    // Called after the cached list is laid out, keeping its startup trace separate from background I/O.
    fun onBrowseReady() {
        if (refreshAfterBrowse) {
            refreshAfterBrowse = false
            refresh()
        }
    }

    fun refresh() {
        if (operation?.isActive == true) return
        val account = savedAccount()
        if (!account.complete) { _uiState.value = LoginUiState.Setup; return }
        load(account, accountEdit = false)
    }

    fun onConnect() {
        val submitted = ChannelAccount(serverUrl, username, password)
        if (!submitted.complete) { showError(R.string.login_missing_fields); return }
        val key = try { submitted.key } catch (_: IllegalArgumentException) { showError(R.string.login_failed); return }
        if (operation?.isActive == true) {
            if (operationKey == key || operationKey == null) return
            operation?.cancel()
        }
        load(submitted, accountEdit = true)
    }

    fun cancelAccountEdit() {
        if (editingAccount && operation?.isActive == true) {
            generation++
            operation?.cancel()
            startupAttempt?.finish(Outcome.CANCELLED)
            val current = _uiState.value as? LoginUiState.Success
            _uiState.value = current?.copy(refreshing = false, messageRes = null) ?: LoginUiState.Setup
        }
        serverUrl = credentialsManager.serverUrl
        username = credentialsManager.username
        password = credentialsManager.password
    }

    private fun showError(message: Int) {
        _uiState.value = (_uiState.value as? LoginUiState.Success)?.copy(refreshing = false, messageRes = message)
            ?: LoginUiState.Error(message)
    }

    private fun load(account: ChannelAccount, accountEdit: Boolean, startupTiming: StartupTimings.Attempt? = null) {
        val key = try { account.key } catch (_: IllegalArgumentException) { showError(R.string.login_failed); return }
        val previous = (_uiState.value as? LoginUiState.Success)?.copy(refreshing = false, messageRes = null)
        operationKey = key
        editingAccount = accountEdit
        val request = ++generation
        _uiState.value = previous?.copy(refreshing = true) ?: LoginUiState.Loading
        if (previous == null) startupStatusRes = R.string.connecting
        val timing = startupTiming ?: if (previous == null) startup.begin().also { startupAttempt = it } else null
        val preceding = operation
        operation = viewModelScope.launch(start = CoroutineStart.LAZY) {
            var committed = false
            try {
                preceding?.join()
                val channels = repository.getChannels(account.server, account.username, account.password).getOrThrow()
                if (request == generation && previous == null) startupStatusRes = R.string.loading_channels
                currentCoroutineContext().ensureActive()
                if (request != generation) return@launch
                if (channels.isEmpty()) {
                    timing?.finish(Outcome.FAILED)
                    showError(R.string.login_empty_playlist)
                    return@launch
                }
                val snapshot = ChannelSnapshot(channels, now())
                snapshots?.write(account, snapshot)
                currentCoroutineContext().ensureActive()
                if (request != generation) return@launch
                activate(account, snapshot, timing)
                currentCoroutineContext().ensureActive()
                if (request != generation) return@launch
                credentialsManager.save(account.server, account.username, account.password)
                committed = true
                editingAccount = false
                currentSnapshot = snapshot
                _uiState.value = LoginUiState.Success(channels, key, snapshot.downloadedAt)
            } catch (error: Exception) {
                timing?.finish(if (error is CancellationException) Outcome.CANCELLED else Outcome.FAILED)
                if (error is CancellationException) throw error
                if (request == generation) _uiState.value = previous?.copy(messageRes = R.string.login_failed)
                    ?: LoginUiState.Error(R.string.login_failed)
            } finally {
                // A cancelled edit may have reached history activation; restore the working owner
                // before a replacement request (which joins this job) is allowed to proceed.
                if (accountEdit && !committed) currentSnapshot?.let { working ->
                    withContext(NonCancellable) { activate(savedAccount(), working, null) }
                }
            }
        }
        operation?.start()
    }

    private suspend fun activate(account: ChannelAccount, snapshot: ChannelSnapshot, timing: StartupTimings.Attempt?) {
        timing?.playlist = snapshot.channels
        val started = timing?.now() ?: 0L
        recents.activate(account.server, account.username, snapshot.channels)
        timing?.record(Phase.HISTORY, started)
    }

    override fun onCleared() {
        startupAttempt?.finish(Outcome.CANCELLED)
        super.onCleared()
    }
}

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Setup : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val channels: List<M3UChannel>, val accountKey: String = "", val downloadedAt: Long = 0,
        val refreshing: Boolean = false, val messageRes: Int? = null) : LoginUiState()
    data class Error(val messageRes: Int) : LoginUiState()
}
