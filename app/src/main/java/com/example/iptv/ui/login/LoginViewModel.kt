package com.example.iptv.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.repository.ChannelRepository
import com.example.iptv.data.storage.CredentialsManager
import com.example.iptv.data.storage.RecentlyWatchedManager
import com.example.iptv.R
import com.example.iptv.diagnostics.StartupTimings
import com.example.iptv.diagnostics.StartupTimings.Outcome
import com.example.iptv.diagnostics.StartupTimings.Phase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: ChannelRepository,
    private val credentialsManager: CredentialsManager,
    private val recents: RecentlyWatchedManager,
    private val startup: StartupTimings = StartupTimings()
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    private var startupAttempt: StartupTimings.Attempt? = null

    var serverUrl by mutableStateOf(credentialsManager.serverUrl)
    var username by mutableStateOf(credentialsManager.username)
    var password by mutableStateOf(credentialsManager.password)

    fun onConnect() {
        if (_uiState.value is LoginUiState.Loading) return
        val submittedServer = serverUrl
        val submittedUser = username
        val submittedPassword = password
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error(R.string.login_missing_fields)
            return
        }

        _uiState.value = LoginUiState.Loading
        val timing = startup.begin().also { startupAttempt = it }

        viewModelScope.launch {
            try {
                val result = repository.getChannels(submittedServer, submittedUser, submittedPassword)
                result.onSuccess { channels ->
                    if (channels.isEmpty()) {
                        timing?.finish(Outcome.FAILED)
                        _uiState.value = LoginUiState.Error(R.string.login_empty_playlist)
                    } else {
                        timing?.playlist = channels
                        val historyStarted = timing?.now() ?: 0L
                        recents.activate(submittedServer, submittedUser, channels)
                        timing?.record(Phase.HISTORY, historyStarted)
                        credentialsManager.save(submittedServer, submittedUser, submittedPassword)
                        _uiState.value = LoginUiState.Success(channels)
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    timing?.finish(Outcome.FAILED)
                    _uiState.value = LoginUiState.Error(R.string.login_failed)
                }
            } catch (e: Exception) {
                timing?.finish(if (e is CancellationException) Outcome.CANCELLED else Outcome.FAILED)
                if (e is CancellationException) throw e
                _uiState.value = LoginUiState.Error(R.string.login_failed)
            }
        }
    }

    override fun onCleared() {
        startupAttempt?.finish(Outcome.CANCELLED)
        super.onCleared()
    }
}

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val channels: List<M3UChannel>) : LoginUiState()
    data class Error(val messageRes: Int) : LoginUiState()
}
