package com.example.iptv.ui

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.iptv.ui.login.*
import com.example.iptv.ui.home.*
import com.example.iptv.ui.browser.*
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.RecentlyWatchedManager
import com.example.iptv.ui.theme.IPTVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
class AccountSettingsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun fieldsOpenKeyboardOnlyOnSelectAndLoadingActionsStayFocusableWithoutDuplicateCalls() {
        var shows = 0
        val keyboard = object : SoftwareKeyboardController {
            override fun show() { shows++ }
            override fun hide() { }
        }
        var state: LoginUiState by mutableStateOf(LoginUiState.Idle)
        var saves = 0; var refreshes = 0
        compose.setContent {
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                IPTVTheme { AccountSettingsContent("https://provider.example", "demo", "synthetic", state,
                    {}, {}, {}, { saves++ }, { refreshes++ }) }
            }
        }
        compose.onNodeWithText("Password").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.runOnIdle { assertEquals(0, shows) }
        compose.onNodeWithText("Password").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, shows) }
        compose.onNodeWithText("Password").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Save and load").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, saves); state = LoginUiState.Loading }
        compose.onNodeWithText("Connecting…").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter); pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Refresh channel list").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, saves); assertEquals(0, refreshes); state = LoginUiState.Error(com.example.iptv.R.string.login_failed) }
        compose.onNodeWithText("Refresh channel list").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test fun browserSettingsConnectDensityAccountAndReturnFocusWithoutDiscardingCategory() {
        val home = HomeViewModel(RecentlyWatchedManager(RuntimeEnvironment.getApplication()))
        home.browser.open(com.example.iptv.ui.browser.BrowserDestination.CATEGORIES); home.browser.focusArea(com.example.iptv.ui.browser.BrowserFocus.SIDEBAR)
        val channels = listOf(M3UChannel("News", "Live", null, "https://provider.example/1", null, null, null, false))
        compose.setContent {
            IPTVTheme {
                BrowserScreen(channels, { _, _ -> }, home, accountSettings = { modifier, focus, leave ->
                    AccountSettingsContent("https://provider.example", "demo", "synthetic", LoginUiState.Success(channels),
                        {}, {}, {}, {}, {}, modifier, focus, requestInitialFocus = false, onLeave = leave)
                })
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Live").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Live").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("Live").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("News").assertIsFocused()
        compose.runOnIdle { home.browser.open(BrowserDestination.SETTINGS) }
        compose.onNodeWithTag("density_STANDARD").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Save and load").assertIsFocused().assertIsDisplayed().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("density_STANDARD").assertIsFocused()
        compose.runOnIdle { home.browser.back() }
        compose.onNodeWithText("News").assertIsFocused().assertIsDisplayed()
    }
}