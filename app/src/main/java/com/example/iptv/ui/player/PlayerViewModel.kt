package com.example.iptv.ui.player

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val application: Application
) : AndroidViewModel(application) {
    fun seekBy(offsetMs: Long) {
        player?.let { p ->
            val duration = p.duration.takeIf { it > 0 } ?: return
            val newPosition = (p.currentPosition + offsetMs).coerceIn(0, duration)
            p.seekTo(newPosition)
        }
    }

    private var _player: ExoPlayer? = null
    val player: ExoPlayer? get() = _player

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Idle)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    fun initializePlayer(streamUrl: String) {
        if (streamUrl.isBlank()) {
            Log.e(TAG, "Stream URL is empty!")
            _uiState.value = PlayerUiState.Error("Stream URL is empty")
            return
        }

        // Always reset error state when switching channels
        _uiState.value = PlayerUiState.Idle

        Log.d(TAG, "Initializing player")

        if (_player == null) {
            _player = ExoPlayer.Builder(application).build().apply {
                val audioAttrs = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build()

                setAudioAttributes(audioAttrs, true)
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "Player Error: ${error.message}", error)
                        _uiState.value = PlayerUiState.Error("Playback Error: ${error.message}")
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> Log.d(TAG, "State: BUFFERING")
                            Player.STATE_READY -> {
                                Log.d(TAG, "State: READY")
                                _uiState.value = PlayerUiState.Playing
                            }
                            Player.STATE_ENDED -> Log.d(TAG, "State: ENDED")
                            Player.STATE_IDLE -> Log.d(TAG, "State: IDLE")
                        }
                    }
                })
            }
        }

        _player?.setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
        _player?.prepare()
        _player?.play()
    }

    fun releasePlayer() {
        _player?.release()
        _player = null
        _uiState.value = PlayerUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }

    companion object {
        private const val TAG = "PlayerViewModel"
    }
}

sealed class PlayerUiState {
    object Idle : PlayerUiState()
    object Playing : PlayerUiState()
    data class Error(val message: String) : PlayerUiState()
}
