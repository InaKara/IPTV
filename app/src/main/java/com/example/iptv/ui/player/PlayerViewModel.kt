package com.example.iptv.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.BuildConfig
import com.example.iptv.data.storage.CredentialsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.example.iptv.data.library.LibraryRepository
import com.example.iptv.data.library.LibraryAccount
import com.example.iptv.data.library.WatchProgress

internal fun seekTarget(current: Long, offset: Long, duration: Long, allowed: Boolean): Long? {
    if (!allowed || duration <= 0) return null
    // Saturating addition avoids overflow as well as negative/out-of-range positions.
    val position = current.coerceIn(0, duration)
    return if (offset >= 0) position + offset.coerceAtMost(duration - position)
    else position + offset.coerceAtLeast(-position)
}

@androidx.annotation.OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(private val application: Application,
    private val credentials: CredentialsManager, private val library: LibraryRepository? = null,
    private val logging: com.example.iptv.diagnostics.VerboseLogging? = null) : AndroidViewModel(application) {
    private var engine: ExoPlayer? = null
    private val _player = MutableStateFlow<Player?>(null)
    val player = _player.asStateFlow()
    private val _uiState = MutableStateFlow(PlayerUiState.Idle)
    val uiState = _uiState.asStateFlow()
    private val _liveRecoveryNotice = MutableStateFlow(0L)
    val liveRecoveryNotice = _liveRecoveryNotice.asStateFlow()
    private var current: M3UChannel? = null
    private var resumePosition = 0L
    private var resumePlaying = true
    private var progressAccount: LibraryAccount? = null
    private var loadingProgress: Job? = null
    private var progressBinding: VodProgressBinding? = null
    private var pendingProgress: WatchProgress? = null
    private var currentProgressKey: String? = null

    fun start(channel: M3UChannel, progressKey: String? = null) {
        if (engine != null && current == channel && currentProgressKey == progressKey) return
        loadingProgress?.cancel()
        releasePlayer()
        if (current?.streamUrl != channel.streamUrl || current?.userAgent != channel.userAgent) {
            resumePosition = 0
            resumePlaying = true
        }
        _liveRecoveryNotice.value = 0
        current = channel
        currentProgressKey = progressKey
        progressAccount = library?.let { runCatching { it.account() }.getOrNull() }
        val account = progressAccount
        if (channel.isVod && library != null && account != null) {
            loadingProgress = viewModelScope.launch {
                try {
                    library.flush()
                    pendingProgress = library.progress(account, channel, progressKey)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { pendingProgress = null }
                resumePosition = 0
                if (library.valid(account) && current == channel) begin(channel)
            }
        } else begin(channel)
    }

    private fun begin(channel: M3UChannel) {
        logging?.gate?.event(com.example.iptv.diagnostics.DiagnosticEvent.PLAY_PREPARE)
        if (!channel.streamUrl.startsWith("https://", true) && !channel.streamUrl.startsWith("http://", true)) {
            _uiState.value = PlayerUiState.Unsupported
            return
        }
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(false)
            .setUserAgent(channel.userAgent?.takeIf { it.isNotBlank() } ?: "AypiTivi")
        val data = DefaultDataSource.Factory(application, http)
        val mediaSources = DefaultMediaSourceFactory(data)
        val route = PlaybackRoute(channel, liveHlsAlternative(channel, credentials.serverUrl,
            credentials.username, credentials.password))
        val recovery = LiveWindowRecovery()
        val exo = ExoPlayer.Builder(application, audioFallbackRenderers(application))
            .setLoadControl(playbackLoadControl(channel.isVod))
            .setSeekBackIncrementMs(DEDICATED_SEEK_MS).setSeekForwardIncrementMs(DEDICATED_SEEK_MS)
            .setMediaSourceFactory(mediaSources).build()
        engine = exo
        exo.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        if (BuildConfig.DEBUG) exo.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioCodecError(eventTime: AnalyticsListener.EventTime, audioCodecError: Exception) {
                logging?.gate?.event(com.example.iptv.diagnostics.DiagnosticEvent.AUDIO_CODEC_ERROR)
            }
            override fun onAudioSinkError(eventTime: AnalyticsListener.EventTime, audioSinkError: Exception) {
                logging?.gate?.event(com.example.iptv.diagnostics.DiagnosticEvent.AUDIO_SINK_ERROR)
            }
            override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String,
                initializedTimestampMs: Long, initializationDurationMs: Long) {
                logging?.gate?.event(com.example.iptv.diagnostics.DiagnosticEvent.AUDIO_INITIALIZED, initializationDurationMs)
            }
        })
        exo.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) recovery.paused()
            }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (route.rejectsTimeline(timeline, exo.currentMediaItemIndex)) {
                    route.fallback()?.let { exo.setMediaItem(it); exo.prepare() }
                }
            }
            override fun onTracksChanged(tracks: Tracks) {
                if (logging?.enabled?.value == true) audioTrackDiagnostics(tracks).forEach { logging.gate.audio(it) }
            }
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) logging?.gate?.event(com.example.iptv.diagnostics.DiagnosticEvent.PLAY_STATE, count = player.playbackState)
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
                if (handlePlaybackFailure(exo, error, route, recovery, android.os.SystemClock.elapsedRealtime()) {
                        _liveRecoveryNotice.value += 1
                    }) return
                // Fixed Media3 error names are useful diagnostics; provider messages/causes are private.
                if (BuildConfig.DEBUG) android.util.Log.w("AypiTivi", "Playback failed: ${error.errorCodeName}")
                _uiState.value = PlayerUiState.Error
            }
        })
        // The native time bar and its buttons go through the same policy as remote seeks.
        _player.value = guardedPlayback(exo, channel.isVod)
        if (route.usingAlternative) {
            // Media3 validates the manifest/timeline; a failed alternative falls back once.
            val hls = HlsMediaSource.Factory(data).setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(0))
            exo.setMediaSource(hls.createMediaSource(route.initialItem))
        } else if (channel.isVod) exo.setMediaItem(route.initialItem, resumePosition)
        else exo.setMediaItem(route.initialItem) // HLS starts at its safe default live position.
        val account = progressAccount
        if (channel.isVod && library != null && account != null) progressBinding = VodProgressBinding(exo, true, pendingProgress) { position, duration, seekable, ended ->
            library.enqueue(account, channel, position, duration, seekable, ended, currentProgressKey)
        }
        pendingProgress = null
        exo.prepare()
        exo.playWhenReady = resumePlaying
    }

    fun seekBy(offsetMs: Long): Boolean {
        val p = engine ?: return false
        return seekPlaybackBy(p, offsetMs, current?.isVod == true)
    }

    fun suspendPlayback() {
        loadingProgress?.cancel()
        engine?.let {
            resumePosition = if (current?.isVod == true) it.currentPosition.coerceAtLeast(0) else 0
            resumePlaying = it.playWhenReady
        }
        releasePlayer()
    }

    fun releasePlayer() {
        progressBinding?.close()
        progressBinding = null
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
