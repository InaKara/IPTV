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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.example.iptv.ui.home.BrowserScreen
import com.example.iptv.ui.home.HomeViewModel
import com.example.iptv.ui.login.AccountSettingsContent
import com.example.iptv.ui.login.LoginViewModel
import com.example.iptv.ui.login.LoginUiState
import com.example.iptv.ui.login.StartupContent
import com.example.iptv.ui.player.PlayerScreen
import com.example.iptv.ui.SessionViewModel
import com.example.iptv.ui.theme.IPTVTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val session: SessionViewModel by viewModels()
    private val browser: HomeViewModel by viewModels()
    private val connection: LoginViewModel by viewModels()
    private val library: com.example.iptv.ui.library.LibraryViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        connection.onForeground()
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IPTVTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by session.state.collectAsStateWithLifecycle()
                    val loading by connection.uiState.collectAsStateWithLifecycle()
                    val directory = loading as? LoginUiState.Success
                    LaunchedEffect(directory?.channels, directory?.accountKey) {
                        directory?.let { session.updateChannels(it.channels, it.accountKey) }
                    }
                    val selectedChannel = state.selected

                    if (selectedChannel != null) {
                        PlayerScreen(
                            channel = selectedChannel,
                            selectionKey = state.selectedIndex,
                            progressKey = state.selectedIndex?.let { state.identities.getOrNull(it) },
                            onBackPressed = { browser.returnFromPlayback(state.selectedIndex); session.browse() },
                            onNextChannel = { session.step(1) },
                            onPreviousChannel = { session.step(-1) }
                        )
                    } else if (directory != null) {
                        ExitConfirmDialog(onExit = { finish() })

                        BrowserScreen(
                            channels = directory.channels,
                            onChannelClick = { index, entries -> session.select(index, entries, browser.playbackIdentities(entries)) },
                            viewModel = browser,
                            library = library,
                            onBrowseReady = connection::onBrowseReady,
                            accountSettings = { modifier, focus, leave ->
                                DisposableEffect(Unit) { onDispose { connection.cancelAccountEdit() } }
                                AccountSettingsContent(connection.serverUrl, connection.username, connection.password, loading,
                                    { connection.serverUrl = it }, { connection.username = it }, { connection.password = it },
                                    connection::onConnect, connection::refresh, modifier, focus,
                                    requestInitialFocus = false, onLeave = leave)
                            }
                        )
                    } else {
                        ExitConfirmDialog(onExit = { finish() })
                        StartupContent(loading, connection.startupStatusRes) {
                          DisposableEffect(Unit) { onDispose { connection.cancelAccountEdit() } }
                          Column(Modifier.fillMaxSize().padding(32.dp)) {
                            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
                            AccountSettingsContent(connection.serverUrl, connection.username, connection.password, loading,
                                { connection.serverUrl = it }, { connection.username = it }, { connection.password = it },
                                connection::onConnect, connection::refresh, Modifier.width(520.dp))
                          }
                        }
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
