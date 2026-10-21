package com.example.iptv.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow

internal fun seekTarget(current: Long, offset: Long, duration: Long, allowed: Boolean): Long? {
    if (!allowed || duration <= 0) return null
    // Saturating addition avoids overflow as well as negative/out-of-range positions.
    val position = current.coerceIn(0, duration)
    return if (offset >= 0) position + offset.coerceAtMost(duration - position)
    else position + offset.coerceAtLeast(-position)
}

@androidx.annotation.OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(private val application: Application) : AndroidViewModel(application) {
    private var engine: ExoPlayer? = null
    private val _player = MutableStateFlow<Player?>(null)
    val player = _player.asStateFlow()
    private val _uiState = MutableStateFlow(PlayerUiState.Idle)
    val uiState = _uiState.asStateFlow()
    private var current: M3UChannel? = null
    private var resumePosition = 0L
    private var resumePlaying = true

    fun start(channel: M3UChannel) {
        if (engine != null && current == channel) return
        if (current?.streamUrl != channel.streamUrl || current?.userAgent != channel.userAgent) {
            resumePosition = 0
            resumePlaying = true
        }
        releasePlayer()
        current = channel
        if (!channel.streamUrl.startsWith("https://", true) && !channel.streamUrl.startsWith("http://", true)) {
            _uiState.value = PlayerUiState.Unsupported
            return
        }
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(false)
            .setUserAgent(channel.userAgent?.takeIf { it.isNotBlank() } ?: "AypiTivi")
        val mediaSources = DefaultMediaSourceFactory(DefaultDataSource.Factory(application, http))
        val exo = ExoPlayer.Builder(application).setMediaSourceFactory(mediaSources).build()
        engine = exo
        exo.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        exo.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                _uiState.value = when {
                    player.playerError != null -> PlayerUiState.Error
                    player.playbackState == Player.STATE_BUFFERING -> PlayerUiState.Buffering
                    player.playbackState == Player.STATE_ENDED -> PlayerUiState.Ended
                    player.isPlaying -> PlayerUiState.Playing
                    player.playbackState == Player.STATE_READY -> PlayerUiState.Paused
                    else -> PlayerUiState.Idle
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                // Fixed Media3 error names are useful diagnostics; provider messages/causes are private.
                if (BuildConfig.DEBUG) android.util.Log.w("AypiTivi", "Playback failed: ${error.errorCodeName}")
                _uiState.value = PlayerUiState.Error
            }
        })
        // The native time bar and its buttons go through the same policy as remote seeks.
        _player.value = object : ForwardingPlayer(exo) {
            override fun seekTo(positionMs: Long) { seekAbsolute(positionMs) }
            override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
                if (mediaItemIndex == 0) seekAbsolute(positionMs)
            }
            override fun seekBack() { seekBy(-DEDICATED_SEEK_MS) }
            override fun seekForward() { seekBy(DEDICATED_SEEK_MS) }
        }
        exo.setMediaItem(MediaItem.fromUri(channel.streamUrl), if (channel.isVod) resumePosition else 0)
        exo.prepare()
        exo.playWhenReady = resumePlaying
    }

    private fun canSeek(p: Player) = current?.isVod == true && p.isCurrentMediaItemSeekable &&
        p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)

    fun seekBy(offsetMs: Long): Boolean {
        val p = engine ?: return false
        val target = seekTarget(p.currentPosition, offsetMs, p.duration, canSeek(p)) ?: return false
        p.seekTo(target)
        return true
    }

    private fun seekAbsolute(position: Long) {
        val p = engine ?: return
        val target = seekTarget(0, position, p.duration, canSeek(p)) ?: return
        p.seekTo(target)
    }

    fun suspendPlayback() {
        engine?.let {
            resumePosition = if (current?.isVod == true) it.currentPosition.coerceAtLeast(0) else 0
            resumePlaying = it.playWhenReady
        }
        releasePlayer()
    }

    fun releasePlayer() {
        _player.value = null
        engine?.release()
        engine = null
        _uiState.value = PlayerUiState.Idle
    }

    override fun onCleared() { releasePlayer() }

    companion object {
        const val DPAD_SEEK_MS = 30_000L
        const val DEDICATED_SEEK_MS = 10_000L
        const val OVERLAY_MS = 3_000L
    }
}

enum class PlayerUiState { Idle, Buffering, Playing, Paused, Ended, Error, Unsupported }
