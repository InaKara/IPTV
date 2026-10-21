package com.example.iptv.ui.login

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.repository.ChannelRepository
import com.example.iptv.data.storage.CredentialsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: ChannelRepository,
    private val credentialsManager: CredentialsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    var serverUrl by mutableStateOf(credentialsManager.serverUrl)
    var username by mutableStateOf(credentialsManager.username)
    var password by mutableStateOf(credentialsManager.password)

    fun onConnect() {
        Log.d(TAG, "Requesting playlist")
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Please fill all fields")
            return
        }

        _uiState.value = LoginUiState.Loading

        viewModelScope.launch {
            try {
                val result = repository.getChannels(serverUrl, username, password)
                result.onSuccess { channels ->
                    Log.d(TAG, "Success! Found ${channels.size} channels")
                    if (channels.isEmpty()) {
                        _uiState.value = LoginUiState.Error("No channels found in playlist")
                    } else {
                        credentialsManager.serverUrl = serverUrl
                        credentialsManager.username = username
                        credentialsManager.password = password
                        _uiState.value = LoginUiState.Success(channels)
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Login failed", error)
                    _uiState.value = LoginUiState.Error(error.message ?: "Network or Parsing error")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error", e)
                _uiState.value = LoginUiState.Error("Unexpected error: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "LoginViewModel"
    }
}

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val channels: List<M3UChannel>) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}
