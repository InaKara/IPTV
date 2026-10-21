package com.example.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NonInteractiveSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.RecentlyWatchedManager
import com.example.iptv.ui.home.HomeScreen
import com.example.iptv.ui.login.LoginScreen
import com.example.iptv.ui.player.PlayerScreen
import com.example.iptv.ui.theme.IPTVTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var recentlyWatchedManager: RecentlyWatchedManager

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IPTVTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var isLoggedIn by remember { mutableStateOf(false) }
                    var channels by remember { mutableStateOf(emptyList<M3UChannel>()) }
                    var selectedChannel by remember { mutableStateOf<M3UChannel?>(null) }
                    var categoryChannels by remember { mutableStateOf(emptyList<M3UChannel>()) }

                    if (selectedChannel != null) {
                        // Track channel view
                        LaunchedEffect(selectedChannel) {
                            selectedChannel?.let { recentlyWatchedManager.addChannel(it) }
                        }

                        val currentIndex = categoryChannels.indexOf(selectedChannel)
                        PlayerScreen(
                            streamUrl = selectedChannel!!.streamUrl,
                            channelName = selectedChannel!!.name,
                            isVod = selectedChannel!!.isVod,
                            onBackPressed = { selectedChannel = null },
                            onNextChannel = {
                                if (categoryChannels.isNotEmpty()) {
                                    val nextIndex = (currentIndex + 1) % categoryChannels.size
                                    selectedChannel = categoryChannels[nextIndex]
                                }
                            },
                            onPreviousChannel = {
                                if (categoryChannels.isNotEmpty()) {
                                    val prevIndex = if (currentIndex - 1 < 0) categoryChannels.size - 1 else currentIndex - 1
                                    selectedChannel = categoryChannels[prevIndex]
                                }
                            }
                        )
                    } else if (isLoggedIn) {
                        ExitConfirmDialog(onExit = { finish() })

                        HomeScreen(
                            channels = channels,
                            onChannelClick = { channel, channelsInCategory ->
                                selectedChannel = channel
                                categoryChannels = channelsInCategory
                            }
                        )
                    } else {
                        LoginScreen(onLoginSuccess = { loadedChannels ->
                            channels = loadedChannels
                            isLoggedIn = true
                        })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ExitConfirmDialog(onExit: () -> Unit) {
    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler { showExitDialog = true }

    if (showExitDialog) {
        Dialog(onDismissRequest = { showExitDialog = false }) {
            Surface(
                modifier = Modifier.padding(16.dp),
                shape = MaterialTheme.shapes.medium,
                colors = NonInteractiveSurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Exit App?", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        Button(onClick = { showExitDialog = false }) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(onClick = onExit) { Text("Exit") }
                    }
                }
            }
        }
    }
}
