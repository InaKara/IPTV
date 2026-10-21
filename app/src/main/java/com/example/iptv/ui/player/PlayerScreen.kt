
package com.example.iptv.ui.player

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NonInteractiveSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeekOverlay(seekText: String?) {
    var visible by remember(seekText) { mutableStateOf(seekText != null) }
    LaunchedEffect(seekText) {
        if (seekText != null) {
            visible = true
            delay(3000)
            visible = false
        }
    }
    if (seekText != null && visible) {
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 120.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                colors = NonInteractiveSurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                ),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = seekText,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    streamUrl: String,
    channelName: String,
    isVod: Boolean,
    onBackPressed: () -> Unit,
    onNextChannel: () -> Unit,
    onPreviousChannel: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val focusRequester = remember { FocusRequester() }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(streamUrl) { viewModel.initializePlayer(streamUrl) }
    DisposableEffect(Unit) { onDispose { viewModel.releasePlayer() } }
    BackHandler { onBackPressed() }

    var seekOverlayText by remember { mutableStateOf<String?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent {
                if (it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && it.nativeKeyEvent.repeatCount == 0) {
                    when (it.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                            onNextChannel(); true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            onPreviousChannel(); true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            // Play/pause toggle for both VOD and Live
                            viewModel.player?.let { player ->
                                if (player.isPlaying) player.pause() else player.play()
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (isVod) {
                                viewModel.seekBy(30_000)
                                seekOverlayText = "+30 s"
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (isVod) {
                                viewModel.seekBy(-30_000)
                                seekOverlayText = "-30 s"
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            viewModel.player?.let { player ->
                                if (player.isPlaying) player.pause() else player.play()
                            }
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            viewModel.player?.play()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            viewModel.player?.pause()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            viewModel.player?.let { player ->
                                val newPosition = (player.currentPosition - 10000).coerceAtLeast(0)
                                player.seekTo(newPosition)
                            }
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            viewModel.player?.let { player ->
                                val newPosition = (player.currentPosition + 10000).coerceAtMost(player.duration)
                                player.seekTo(newPosition)
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Video player view
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.player
                    useController = true
                    controllerAutoShow = true
                    controllerHideOnTouch = false
                    controllerShowTimeoutMs = 3000
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { it.player = viewModel.player }
        )

        // Channel name overlay — auto-hides after 3 seconds
        ChannelNameOverlay(channelName)
        SeekOverlay(seekOverlayText)



@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeekOverlay(seekText: String?) {
    var visible by remember(seekText) { mutableStateOf(seekText != null) }
    LaunchedEffect(seekText) {
        if (seekText != null) {
            visible = true
            delay(3000)
            visible = false
        }
    }
    if (seekText != null && visible) {
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 120.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                colors = NonInteractiveSurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                ),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = seekText,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}

        // Error overlay
        if (uiState is PlayerUiState.Error) {
            ErrorOverlay(
                message = (uiState as PlayerUiState.Error).message,
                onBack = onBackPressed
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelNameOverlay(channelName: String) {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(channelName) {
        visible = true
        delay(3000)
        visible = false
    }

    if (visible) {
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 48.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                colors = NonInteractiveSurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                ),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = channelName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorOverlay(message: String, onBack: () -> Unit) {
    Surface(
        colors = NonInteractiveSurfaceDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Playback Error",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Red
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = message, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onBack) { Text("Go Back") }
        }
    }
}
