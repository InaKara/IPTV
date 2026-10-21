package com.example.iptv.ui.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
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
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val focus = remember { FocusRequester() }
    val player by viewModel.player.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val failed = state == PlayerUiState.Error || state == PlayerUiState.Unsupported
    val errorFocus = remember { FocusRequester() }
    var notice by remember(channel, selectionKey) { mutableStateOf<SeekNotice?>(null) }
    LifecycleStartEffect(channel, viewModel) {
        viewModel.start(channel)
        onStopOrDispose { viewModel.suspendPlayback() }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(failed) { if (failed) errorFocus.requestFocus() }
    BackHandler(onBack = onBackPressed)

    fun seek(offset: Long): Boolean {
        if (viewModel.seekBy(offset)) notice = SeekNotice((notice?.sequence ?: 0) + 1, offset / 1000)
        return true // Consume unsupported seeks rather than passing them to the native view.
    }

    Box(Modifier.fillMaxSize().focusRequester(focus).focusable().onPreviewKeyEvent {
        if (failed || it.nativeKeyEvent.action != KeyEvent.ACTION_DOWN || it.nativeKeyEvent.repeatCount != 0) false
        else when (it.nativeKeyEvent.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { onNextChannel(); true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { onPreviousChannel(); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                player?.let { p -> if (p.playWhenReady) p.pause() else p.play() }; true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { player?.play(); true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { player?.pause(); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> seek(-PlayerViewModel.DPAD_SEEK_MS)
            KeyEvent.KEYCODE_DPAD_RIGHT -> seek(PlayerViewModel.DPAD_SEEK_MS)
            KeyEvent.KEYCODE_MEDIA_REWIND -> seek(-PlayerViewModel.DEDICATED_SEEK_MS)
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seek(PlayerViewModel.DEDICATED_SEEK_MS)
            else -> false
        }
    }) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    useController = true
                    controllerShowTimeoutMs = PlayerViewModel.OVERLAY_MS.toInt()
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null }
        )
        TimedOverlay(channel.streamUrl to selectionKey, channel.name, 48)
        notice?.let {
            TimedOverlay(it, stringResource(R.string.seek_seconds, if (it.seconds > 0) "+" else "", it.seconds), 120)
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
private fun TimedOverlay(event: Any, text: String, bottomPadding: Int) {
    var visible by remember(event) { mutableStateOf(true) }
    LaunchedEffect(event) { delay(PlayerViewModel.OVERLAY_MS); visible = false }
    if (visible) {
        Box(Modifier.fillMaxSize().padding(bottom = bottomPadding.dp), contentAlignment = Alignment.BottomCenter) {
            Surface(
                colors = NonInteractiveSurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.7f)),
                shape = MaterialTheme.shapes.small
            ) { Text(text, Modifier.padding(horizontal = 24.dp, vertical = 12.dp), color = Color.White, style = MaterialTheme.typography.titleLarge) }
        }
    }
}
