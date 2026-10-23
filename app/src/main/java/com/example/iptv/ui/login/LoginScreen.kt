package com.example.iptv.ui.login

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.example.iptv.R
import java.text.DateFormat
import java.util.Date

/** The same account controls serve first-time setup and the browser's Settings destination. */
@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun AccountSettingsContent(
    server: String, username: String, password: String, state: LoginUiState,
    onServer: (String) -> Unit, onUsername: (String) -> Unit, onPassword: (String) -> Unit,
    onConnect: () -> Unit, onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    primaryFocus: FocusRequester = remember { FocusRequester() },
    requestInitialFocus: Boolean = true,
    onLeave: (() -> Unit)? = null
) {
    val (serverFocus, userFocus, passwordFocus, refreshFocus) = remember { FocusRequester.createRefs() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { keyboard?.hide(); if (requestInitialFocus) primaryFocus.requestFocus() }
    var textEditing by remember { mutableStateOf(false) }
    val success = state as? LoginUiState.Success
    val loading = state is LoginUiState.Loading || success?.refreshing == true
    Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp)
        .onPreviewKeyEvent {
            if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
                it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && onLeave != null && !textEditing) {
                keyboard?.hide(); onLeave(); true
            } else false
        }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.account), style = MaterialTheme.typography.titleMedium)
        TvAccountField(server, onServer, R.string.server_url, serverFocus, refreshFocus, userFocus, onEditing = { textEditing = it })
        TvAccountField(username, onUsername, R.string.username, userFocus, serverFocus, passwordFocus, onEditing = { textEditing = it })
        TvAccountField(password, onPassword, R.string.password, passwordFocus, userFocus, primaryFocus, password = true, onEditing = { textEditing = it })
        if (server.trim().startsWith("http://", ignoreCase = true)) {
            Text(stringResource(R.string.http_warning), style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = { if (!loading) { keyboard?.hide(); onConnect() } },
            modifier = Modifier.focusRequester(primaryFocus).focusProperties { up = passwordFocus; down = refreshFocus }) {
            Text(stringResource(if (loading) R.string.connecting else R.string.save_and_load))
        }
        Button(onClick = { if (!loading) { keyboard?.hide(); onRefresh() } },
            modifier = Modifier.focusRequester(refreshFocus).focusProperties { up = primaryFocus; down = serverFocus }) {
            Text(stringResource(R.string.refresh_channels))
        }
        Text(if (success != null) stringResource(R.string.last_download,
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(success.downloadedAt)))
            else stringResource(R.string.no_saved_list), style = MaterialTheme.typography.bodySmall)
        val message = (state as? LoginUiState.Error)?.messageRes ?: success?.messageRes
        message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TvAccountField(
    value: String, onChange: (String) -> Unit, label: Int,
    focus: FocusRequester, up: FocusRequester, down: FocusRequester, password: Boolean = false, onEditing: (Boolean) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(editing) { onEditing(editing); if (editing) keyboard?.show() }
    BackHandler(editing) { editing = false; keyboard?.hide() }
    OutlinedTextField(value, onChange, singleLine = true, readOnly = !editing,
        label = { androidx.compose.material3.Text(stringResource(label)) },
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("account_field_$label")
            .onFocusChanged { if (!it.hasFocus) editing = false }
            .onPreviewKeyEvent {
                val key = it.nativeKeyEvent
                when (key.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0) editing = true
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (key.action == KeyEvent.ACTION_DOWN) {
                            editing = false; keyboard?.hide()
                            (if (key.keyCode == KeyEvent.KEYCODE_DPAD_UP) up else down).requestFocus()
                        }
                        true
                    }
                    else -> false
                }
            },
        keyboardOptions = KeyboardOptions(imeAction = if (password) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { editing = false; keyboard?.hide(); down.requestFocus() },
            onDone = { editing = false; keyboard?.hide(); down.requestFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)))
}
