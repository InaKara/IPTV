package com.example.iptv.ui.login

import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.R
import com.example.iptv.ui.theme.IPTVTheme

@Composable
fun LoginScreen(onLoginSuccess: (List<M3UChannel>) -> Unit, viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoginContent(viewModel.serverUrl, viewModel.username, viewModel.password, state,
        { viewModel.serverUrl = it }, { viewModel.username = it }, { viewModel.password = it },
        viewModel::onConnect)
    LaunchedEffect(state) {
        (state as? LoginUiState.Success)?.let { onLoginSuccess(it.channels) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
internal fun LoginContent(
    server: String, username: String, password: String, state: LoginUiState,
    onServer: (String) -> Unit, onUsername: (String) -> Unit, onPassword: (String) -> Unit,
    onConnect: () -> Unit
) {
    val (serverFocus, userFocus, passwordFocus, buttonFocus) = remember { FocusRequester.createRefs() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { keyboard?.hide(); buttonFocus.requestFocus() }
    val loading = state is LoginUiState.Loading

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.app_logo), null, Modifier.width(120.dp).height(120.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))
        TvLoginField(server, onServer, R.string.server_url, serverFocus, buttonFocus, userFocus)
        TvLoginField(username, onUsername, R.string.username, userFocus, serverFocus, passwordFocus)
        TvLoginField(password, onPassword, R.string.password, passwordFocus, userFocus, buttonFocus, password = true)
        if (server.trim().startsWith("http://", ignoreCase = true)) {
            Text(stringResource(R.string.http_warning), color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(400.dp))
        }
        Spacer(Modifier.height(16.dp))
        // Keep a stable, focusable target while loading; the ViewModel also enforces single-flight.
        Button(onClick = { if (!loading) { keyboard?.hide(); onConnect() } },
            modifier = Modifier.focusRequester(buttonFocus).focusProperties { up = passwordFocus; down = serverFocus }) {
            Text(stringResource(if (loading) R.string.connecting else R.string.connect))
        }
        if (loading) CircularProgressIndicator(Modifier.padding(12.dp), color = MaterialTheme.colorScheme.primary)
        (state as? LoginUiState.Error)?.let {
            Text(stringResource(it.messageRes), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.width(400.dp).padding(top = 12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun TvLoginField(
    value: String, onChange: (String) -> Unit, label: Int,
    focus: FocusRequester, up: FocusRequester, down: FocusRequester, password: Boolean = false
) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        label = { androidx.compose.material3.Text(stringResource(label)) },
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.width(400.dp).padding(bottom = 12.dp).focusRequester(focus).onPreviewKeyEvent {
            val key = it.nativeKeyEvent
            when (key.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0) keyboard?.show()
                    true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (key.action == KeyEvent.ACTION_DOWN) {
                        keyboard?.hide()
                        (if (key.keyCode == KeyEvent.KEYCODE_DPAD_UP) up else down).requestFocus()
                        true
                    } else false
                }
                else -> false
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = if (password) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(
            onNext = { down.requestFocus() },
            onDone = { keyboard?.hide(); down.requestFocus() }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Preview(device = "id:tv_1080p")
@Composable
private fun LoginPreview() {
    IPTVTheme {
        Surface(Modifier.fillMaxSize()) {
            LoginContent("https://provider.example", "demo", "", LoginUiState.Idle, {}, {}, {}, {})
        }
    }
}
