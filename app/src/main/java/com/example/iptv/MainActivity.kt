package com.example.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NonInteractiveSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.iptv.ui.home.HomeScreen
import com.example.iptv.ui.login.LoginScreen
import com.example.iptv.ui.player.PlayerScreen
import com.example.iptv.ui.SessionViewModel
import com.example.iptv.ui.theme.IPTVTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val session: SessionViewModel by viewModels()

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IPTVTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by session.state.collectAsStateWithLifecycle()
                    val selectedChannel = state.selected

                    if (selectedChannel != null) {
                        PlayerScreen(
                            channel = selectedChannel,
                            selectionKey = state.selectedIndex,
                            onBackPressed = session::browse,
                            onNextChannel = { session.step(1) },
                            onPreviousChannel = { session.step(-1) }
                        )
                    } else if (state.channels.isNotEmpty()) {
                        ExitConfirmDialog(onExit = { finish() })

                        HomeScreen(
                            channels = state.channels,
                            onChannelClick = session::select
                        )
                    } else {
                        LoginScreen(onLoginSuccess = { loadedChannels ->
                            session.login(loadedChannels)
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
                    Text(text = stringResource(R.string.exit_app), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        Button(onClick = { showExitDialog = false }) { Text(stringResource(R.string.cancel)) }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(onClick = onExit) { Text(stringResource(R.string.exit)) }
                    }
                }
            }
        }
    }
}
