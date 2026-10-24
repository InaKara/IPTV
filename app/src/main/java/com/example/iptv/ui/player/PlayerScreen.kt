package com.example.iptv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NonInteractiveSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.R
import kotlinx.coroutines.delay

private data class SeekNotice(val sequence: Long, val seconds: Long)

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    channel: M3UChannel,
    selectionKey: Int?,
    onBackPressed: () -> Unit,
    onNextChannel: () -> Unit,
    onPreviousChannel: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
    progressKey: String? = null
) {
    var nativeView by remember { mutableStateOf<TvPlayerView?>(null) }
    var controlsVisible by remember { mutableStateOf(false) }
    val player by viewModel.player.collectAsStateWithLifecycle()
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, view, lifecycle) {
        val display = player?.let { PlaybackDisplay(view, it, lifecycle) }
        onDispose { display?.close() }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recoveryNotice by viewModel.liveRecoveryNotice.collectAsStateWithLifecycle()
    val failed = state == PlayerUiState.Error || state == PlayerUiState.Unsupported
    val errorFocus = remember { FocusRequester() }
    var notice by remember(channel, selectionKey) { mutableStateOf<SeekNotice?>(null) }
    LaunchedEffect(recoveryNotice) { if (recoveryNotice > 0) notice = null }
    LifecycleStartEffect(channel, viewModel, progressKey) {
        viewModel.start(channel, progressKey)
        onStopOrDispose { viewModel.suspendPlayback() }
    }
    LaunchedEffect(failed) { if (failed) errorFocus.requestFocus() }
    LaunchedEffect(nativeView, failed) { if (!failed) nativeView?.requestFocus() }
    BackHandler { if (failed || nativeView?.closeControls() != true) onBackPressed() }

    fun seek(offset: Long): Boolean {
        if (viewModel.seekBy(offset)) notice = SeekNotice((notice?.sequence ?: 0) + 1, offset / 1000)
        return true // Consume unsupported seeks rather than passing them to the native view.
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                TvPlayerView(context).also {
                    it.onControlsVisibilityChanged = { visible -> controlsVisible = visible }
                    nativeView = it
                }
            },
            update = {
                it.player = player
                it.isLiveChannel = !channel.isVod
                it.channel = channel
                it.nextChannel = onNextChannel
                it.previousChannel = onPreviousChannel
                it.seek = { offset -> seek(offset) }
            },
            onRelease = {
                it.onControlsVisibilityChanged = {}
                it.player = null
                nativeView = null
                controlsVisible = false
            }
        )
        TimedOverlay(channel.streamUrl to selectionKey, channel.name, 48, controlsVisible)
        notice?.let {
            TimedOverlay(it, stringResource(R.string.seek_seconds, if (it.seconds > 0) "+" else "", it.seconds), 120, controlsVisible)
        }
        if (recoveryNotice > 0) {
            TimedOverlay(channel.streamUrl to recoveryNotice, stringResource(R.string.live_window_recovered), 120, controlsVisible)
        }
        if (failed) {
            Surface(Modifier.fillMaxSize(), colors = NonInteractiveSurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.8f))) {
                Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(if (state == PlayerUiState.Unsupported) R.string.unsupported_stream else R.string.playback_error))
                    Button(onClick = onBackPressed, modifier = Modifier.focusRequester(errorFocus)) { Text(stringResource(R.string.go_back)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TimedOverlay(event: Any, text: String, bottomPadding: Int, controlsVisible: Boolean = false) {
    var visible by remember(event) { mutableStateOf(true) }
    LaunchedEffect(event) { delay(PlayerViewModel.OVERLAY_MS); visible = false }
    if (visible) {
        // Lift both notice rows equally, retaining their separation and event-based timeout.
        val inset = bottomPadding + if (controlsVisible) TvPlayerView.CONTROLS_PANEL_DP + 8 - 48 else 0
        Box(Modifier.fillMaxSize().padding(bottom = inset.dp), contentAlignment = Alignment.BottomCenter) {
            Surface(
                colors = NonInteractiveSurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.7f)),
                shape = MaterialTheme.shapes.small
            ) { Text(text, Modifier.padding(horizontal = 24.dp, vertical = 12.dp), color = Color.White, style = MaterialTheme.typography.titleLarge) }
        }
    }
}
