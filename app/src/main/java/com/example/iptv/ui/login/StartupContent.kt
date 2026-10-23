package com.example.iptv.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.example.iptv.R

/** Shared geometry keeps the logo still across splash, cache, network and browser preparation. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun StartupBranding(statusRes: Int? = null, onSettings: (() -> Unit)? = null) {
    val settingsFocus = remember { FocusRequester() }
    val nameHeight = with(LocalDensity.current) { MaterialTheme.typography.displayMedium.lineHeight.toDp() }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).testTag("startup_branding")) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.app_logo), null, Modifier.size(200.dp).testTag("startup_logo"))
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium, color = Color.White)
        }
        Column(Modifier.align(Alignment.TopCenter).offset(y = maxHeight / 2 + (224.dp + nameHeight) / 2 + 12.dp)
            .widthIn(max = 520.dp).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            statusRes?.let { Text(stringResource(it), color = Color.White, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("startup_status")) }
            onSettings?.let { action ->
                LaunchedEffect(Unit) { withFrameNanos { }; settingsFocus.requestFocus() }
                Button(onClick = action, modifier = Modifier.focusRequester(settingsFocus)) {
                    Text(stringResource(R.string.open_settings))
                }
            }
        }
    }
}

/** Automatic loading never mounts the editable account form. Explicit setup/retry keeps it mounted. */
@Composable
internal fun StartupContent(state: LoginUiState, statusRes: Int, settings: @Composable () -> Unit) {
    var settingsRequested by rememberSaveable { mutableStateOf(state == LoginUiState.Setup) }
    LaunchedEffect(state) { if (state == LoginUiState.Setup) settingsRequested = true }
    if (settingsRequested || state == LoginUiState.Setup) settings()
    else StartupBranding((state as? LoginUiState.Error)?.messageRes ?: statusRes,
        onSettings = if (state is LoginUiState.Error) ({ settingsRequested = true }) else null)
}
