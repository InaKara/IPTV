package com.example.iptv.ui.login

import com.example.iptv.R
import android.view.KeyEvent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.ui.theme.IPTVTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (List<M3UChannel>) -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    var server by remember { mutableStateOf(viewModel.serverUrl) }
    var username by remember { mutableStateOf(viewModel.username) }
    var password by remember { mutableStateOf(viewModel.password) }

    val focusManager = LocalFocusManager.current
    val (serverFocus, userFocus, passwordFocus, buttonFocus) = remember { FocusRequester.createRefs() }

    // Start with button focused
    LaunchedEffect(Unit) {
        buttonFocus.requestFocus()
    }

    // Shared color config
    val fieldColors = TextFieldDefaults.outlinedTextFieldColors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier.width(120.dp).height(120.dp).padding(bottom = 16.dp)
        )
        Text(
            text = "Aypi Tivi",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Server URL
        OutlinedTextField(
            value = server,
            onValueChange = { server = it; viewModel.serverUrl = it },
            label = { androidx.compose.material3.Text("Server URL") },
            singleLine = true,
            modifier = Modifier
                .width(400.dp)
                .focusRequester(serverFocus)
                .dpadNavigation(up = buttonFocus, down = userFocus,
                    onSelect = { keyboardController?.show() }),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { userFocus.requestFocus() }),
            colors = fieldColors
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Username
        OutlinedTextField(
            value = username,
            onValueChange = { username = it; viewModel.username = it },
            label = { androidx.compose.material3.Text("Username") },
            singleLine = true,
            modifier = Modifier
                .width(400.dp)
                .focusRequester(userFocus)
                .dpadNavigation(up = serverFocus, down = passwordFocus,
                    onSelect = { keyboardController?.show() }),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            colors = fieldColors
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; viewModel.password = it },
            label = { androidx.compose.material3.Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .width(400.dp)
                .focusRequester(passwordFocus)
                .dpadNavigation(up = userFocus, down = buttonFocus,
                    onSelect = { keyboardController?.show() }),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                viewModel.onConnect()
            }),
            colors = fieldColors
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (uiState is LoginUiState.Loading) {
            LoadingSpinner()
            Spacer(modifier = Modifier.height(16.dp))
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.width(32.dp).height(32.dp).padding(bottom = 8.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Loading Aypi Tivi...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        } else {
            Button(
                onClick = { viewModel.onConnect() },
                modifier = Modifier.focusRequester(buttonFocus)
            ) {
                Text(text = "Connect")
            }
        }

        if (uiState is LoginUiState.Error) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = (uiState as LoginUiState.Error).message,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (uiState is LoginUiState.Success) {
            val channels = (uiState as LoginUiState.Success).channels
            LaunchedEffect(Unit) { onLoginSuccess(channels) }
        }
    }
}

@Composable
private fun LoadingSpinner() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "loadingAngle"
    )

    Canvas(modifier = Modifier.width(48.dp).height(48.dp)) {
        drawArc(
            color = Color(0xFF2979FF),
            startAngle = angle,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = 4.dp.toPx())
        )
    }
}

private fun Modifier.dpadNavigation(
    up: FocusRequester? = null,
    down: FocusRequester? = null,
    onSelect: () -> Unit
): Modifier = this.onPreviewKeyEvent { event ->
    val keyCode = event.nativeKeyEvent.keyCode
    val isSelect = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
    if (isSelect) {
        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.nativeKeyEvent.repeatCount == 0) {
            onSelect()
        }
        // Consume both halves so the field does not also submit or invoke another click.
        true
    } else if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> { down?.requestFocus(); down != null }
            KeyEvent.KEYCODE_DPAD_UP -> { up?.requestFocus(); up != null }
            else -> false
        }
    } else false
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@androidx.compose.ui.tooling.preview.Preview(device = "id:tv_1080p")
@Composable
fun LoginScreenPreview() {
    IPTVTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            LoginScreen(onLoginSuccess = {})
        }
    }
}
