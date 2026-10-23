package com.example.iptv.ui

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.iptv.R
import com.example.iptv.ui.login.*
import com.example.iptv.ui.theme.IPTVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
@OptIn(ExperimentalTestApi::class)
class StartupPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun automaticPhasesKeepLogoStillAndFailureOpensExplicitSettingsRetry() {
        var state: LoginUiState by mutableStateOf(LoginUiState.Loading)
        var status by mutableStateOf(R.string.reading_saved_channels)
        compose.setContent {
            IPTVTheme { StartupContent(state, status) {
                AccountSettingsContent("https://provider.example", "demo", "synthetic", state,
                    {}, {}, {}, { state = LoginUiState.Loading }, {})
            } }
        }
        val logo = compose.onNodeWithTag("startup_logo").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        for ((resource, text) in listOf(R.string.reading_saved_channels to "Reading saved channels…",
            R.string.connecting to "Connecting…", R.string.loading_channels to "Loading channels…")) {
            compose.runOnIdle { status = resource }
            assertEquals(logo, compose.onNodeWithTag("startup_logo").fetchSemanticsNode().boundsInRoot)
            val line = compose.onNodeWithText(text).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(line.top > logo.bottom)
            compose.onNodeWithText("Save and load").assertDoesNotExist()
            compose.onNodeWithText("Server URL").assertDoesNotExist()
        }
        compose.runOnIdle { state = LoginUiState.Error(R.string.login_failed) }
        compose.onNodeWithTag("startup_status").assertIsDisplayed()
        compose.onNodeWithText("Open Settings").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        try {
            compose.waitUntil(5000) { compose.onAllNodesWithText("Save and load").fetchSemanticsNodes().isNotEmpty() }
        } catch (error: Throwable) { throw AssertionError(compose.onRoot(useUnmergedTree = true).printToString(), error) }
        compose.onNodeWithText("Save and load").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Server URL").assertIsDisplayed()
        compose.onNodeWithTag("startup_branding").assertDoesNotExist()
        // Explicit setup stays mounted during its request rather than flashing back to startup.
        compose.onNodeWithText("Connecting…").assertIsFocused()
    }

    @Test fun missingAccountStartsInSettingsWithoutExposingPassword() {
        compose.setContent { IPTVTheme { StartupContent(LoginUiState.Setup, R.string.reading_saved_channels) {
            AccountSettingsContent("", "", "synthetic", LoginUiState.Setup, {}, {}, {}, {}, {})
        } } }
        compose.onNodeWithText("Save and load").assertIsFocused()
        compose.onNodeWithText("Refresh channel list").assertIsDisplayed()
        compose.onNodeWithTag("startup_branding").assertDoesNotExist()
        compose.onNodeWithText("synthetic").assertDoesNotExist()
    }
}
