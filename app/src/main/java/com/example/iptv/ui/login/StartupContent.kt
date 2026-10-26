package com.example.iptv.ui.login

import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import androidx.compose.ui.viewinterop.AndroidView
import com.example.iptv.R

/** Draw the actual launch resource, so title/font/layout cannot change at the window handoff. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun StartupBranding(statusRes: Int? = null, onSettings: (() -> Unit)? = null) {
    val settingsFocus = remember { FocusRequester() }
    val name = stringResource(R.string.app_name)
    val titleOffset = dimensionResource(R.dimen.startup_title_top_inset) / 2
    val titleHeight = dimensionResource(R.dimen.startup_title_height)
    val statusGap = dimensionResource(R.dimen.startup_status_gap)
    val ink = colorResource(R.color.startup_ink)
    BoxWithConstraints(Modifier.fillMaxSize().testTag("startup_branding")) {
        AndroidView(factory = { context ->
            View(context).apply {
                setBackgroundResource(R.drawable.splash_background)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }, modifier = Modifier.fillMaxSize().testTag("startup_artwork").semantics { contentDescription = name })
        // Status/action content never participates in the branding's layout or font metrics.
        Column(Modifier.align(Alignment.TopCenter).offset(y = maxHeight / 2 + titleOffset + titleHeight / 2 + statusGap)
            .widthIn(max = 520.dp).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            statusRes?.let { Text(stringResource(it), color = ink, textAlign = TextAlign.Center,
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
