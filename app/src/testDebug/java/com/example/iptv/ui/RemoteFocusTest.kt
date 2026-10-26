package com.example.iptv.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.iptv.ui.player.TimedOverlay
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.RecentlyWatchedManager
import com.example.iptv.ui.home.BrowserScreen
import com.example.iptv.ui.home.HomeViewModel
import com.example.iptv.ui.login.AccountSettingsContent
import com.example.iptv.ui.login.LoginUiState
import com.example.iptv.ui.theme.IPTVTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
class RemoteFocusTest {
    @get:Rule val compose = createComposeRule()
    private fun focus(text: String) = compose.onAllNodesWithText(text).onFirst()
        .performSemanticsAction(SemanticsActions.RequestFocus) { it() }

    @Test fun settingsStartsOnSaveAndMasksPassword() {
        compose.setContent {
            IPTVTheme { AccountSettingsContent("https://provider.example", "demo", "synthetic", LoginUiState.Idle, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("Save and load").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithText("Account").assertExists()
        compose.onNodeWithText("Password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithText("synthetic").assertDoesNotExist()
    }

    @Test fun explicitHttpDisplaysWarningAndHttpsDoesNot() {
        var server by androidx.compose.runtime.mutableStateOf("http://provider.example")
        compose.setContent {
            IPTVTheme { AccountSettingsContent(server, "demo", "", LoginUiState.Idle, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("HTTP sends account details without encryption. Use HTTPS if your provider supports it.").assertExists()
        compose.runOnIdle { server = "https://provider.example" }
        compose.onNodeWithText("HTTP sends account details without encryption. Use HTTPS if your provider supports it.").assertDoesNotExist()
    }

    @Test fun overlayHidesAndRepeatedEqualTextRestartsItsTimer() {
        var event by androidx.compose.runtime.mutableStateOf(1)
        compose.mainClock.autoAdvance = false
        compose.setContent { IPTVTheme { TimedOverlay(event, "+30 s", 120) } }
        compose.onNodeWithText("+30 s").assertExists()
        compose.mainClock.advanceTimeBy(3100)
        compose.onNodeWithText("+30 s").assertDoesNotExist()
        compose.runOnIdle { event++ }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("+30 s").assertExists()
    }

    @Test fun playbackNoticesClearVisiblePanelWithoutRestartingTheirTimer() {
        var controlsVisible by androidx.compose.runtime.mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IPTVTheme {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().testTag(if (controlsVisible) "controls_visible" else "controls_hidden")) {
                    TimedOverlay(1, "Channel notice", 48, controlsVisible)
                    TimedOverlay(2, "+10 s", 120, controlsVisible)
                }
            }
        }
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val panelTop = root.bottom - com.example.iptv.ui.player.TvPlayerView.CONTROLS_PANEL_DP
        val original = compose.onNodeWithText("Channel notice").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("Initial notice $original should sit below panel top $panelTop within $root", original.bottom > panelTop)
        compose.mainClock.advanceTimeBy(2000)
        compose.runOnIdle { controlsVisible = true }
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5000) { compose.onAllNodesWithTag("controls_visible").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("controls_visible").assertExists()
        val channel = compose.onNodeWithText("Channel notice").fetchSemanticsNode().boundsInRoot
        val seek = compose.onNodeWithText("+10 s").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("Channel notice $channel must clear panel top $panelTop within $root", channel.bottom < panelTop)
        org.junit.Assert.assertTrue("Notice rows stay separate", seek.bottom < channel.top)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1100)
        compose.onNodeWithText("Channel notice").assertDoesNotExist()
        compose.onNodeWithText("+10 s").assertDoesNotExist()
    }

    @Test fun selectingCategoryAndChannelDeliversExactPositionAndList() {
        val vm = HomeViewModel(RecentlyWatchedManager(RuntimeEnvironment.getApplication()))
        vm.browser.open(com.example.iptv.ui.browser.BrowserDestination.CATEGORIES); vm.browser.focusArea(com.example.iptv.ui.browser.BrowserFocus.SIDEBAR)
        val channels = listOf(M3UChannel("News", "Group", null, "https://provider.example/1", null, null, null, false))
        var selected = -1
        var delivered = emptyList<M3UChannel>()
        compose.setContent { IPTVTheme { BrowserScreen(channels, { index, entries -> selected = index; delivered = entries }, vm) } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Group").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1 channel").assertIsDisplayed()
        focus("Group").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("News").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("News").assertIsFocused().assertIsDisplayed().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { org.junit.Assert.assertEquals(0, selected); org.junit.Assert.assertEquals(channels, delivered) }
    }

    @Test fun loadingSettingsRetainsSaveFocusTarget() {
        compose.setContent {
            IPTVTheme { AccountSettingsContent("https://provider.example", "demo", "", LoginUiState.Loading, {}, {}, {}, {}, {}) }
        }
        focus("Password").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Connecting…").assertIsFocused()
        compose.onNodeWithText("Connecting…").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Password").assertIsFocused().assertIsDisplayed()
    }

    @Test fun emptyRecentCategoryDoesNotRequestMissingGridFocus() {
        val vm = HomeViewModel(RecentlyWatchedManager(RuntimeEnvironment.getApplication()))
        vm.browser.open(com.example.iptv.ui.browser.BrowserDestination.CATEGORIES); vm.browser.focusArea(com.example.iptv.ui.browser.BrowserFocus.SIDEBAR)
        val channels = listOf(M3UChannel("News", "Group", null, "https://provider.example/1", null, null, null, false))
        compose.setContent { IPTVTheme { BrowserScreen(channels, { _, _ -> }, vm) } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Recently viewed").fetchSemanticsNodes().isNotEmpty() }
        focus("Recently viewed").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("sidebar_CATEGORIES").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("sidebar_CATEGORIES").assertIsFocused()
        compose.onNodeWithText("No channels in this category.").assertIsDisplayed()
    }

    @Test fun remoteCanReachGridAndSidebarTargetsAfterScrolling() {
        val vm = HomeViewModel(RecentlyWatchedManager(RuntimeEnvironment.getApplication()))
        vm.browser.open(com.example.iptv.ui.browser.BrowserDestination.CATEGORIES); vm.browser.focusArea(com.example.iptv.ui.browser.BrowserFocus.SIDEBAR)
        val channels = (0 until 100).map { M3UChannel("Channel$it", "Group000", null, "https://provider.example/$it", null, null, null, false) } +
            (1..40).map { M3UChannel("Other$it", "Group%03d".format(it), null, "https://provider.example/other/$it", null, null, null, false) }
        compose.setContent { IPTVTheme { BrowserScreen(channels, { _, _ -> }, vm) } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Group000").fetchSemanticsNodes().isNotEmpty() }
        focus("Group000").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Channel0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("browser_grid").performScrollToIndex(80)
        focus("Channel80").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("sidebar_CATEGORIES").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Channel80").assertIsFocused().assertIsDisplayed()
        compose.runOnIdle { vm.browser.back() }
        compose.onNodeWithText("Group000").assertIsFocused()
        compose.onNodeWithTag("browser_grid").performScrollToIndex(36)
        focus("Group035").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Other35").assertIsFocused()
        compose.runOnIdle { vm.browser.back() }
        compose.onNodeWithText("Group035").assertIsFocused().assertIsDisplayed()
    }
}
