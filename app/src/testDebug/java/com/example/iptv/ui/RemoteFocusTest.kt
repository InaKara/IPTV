package com.example.iptv.ui

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.RecentlyWatchedManager
import com.example.iptv.ui.home.HomeScreen
import com.example.iptv.ui.home.HomeViewModel
import com.example.iptv.ui.login.LoginContent
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
@Config(sdk = [28], application = Application::class)
class RemoteFocusTest {
    @get:Rule val compose = createComposeRule()
    private fun focus(text: String) = compose.onAllNodesWithText(text).onFirst()
        .performSemanticsAction(SemanticsActions.RequestFocus) { it() }

    @Test fun loadingLoginRetainsConnectFocusTarget() {
        compose.setContent {
            IPTVTheme { LoginContent("https://provider.example", "demo", "", LoginUiState.Loading, {}, {}, {}, {}) }
        }
        focus("Password").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Connecting…").assertIsFocused()
        compose.onNodeWithText("Connecting…").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Password").assertIsFocused().assertIsDisplayed()
    }

    @Test fun emptyRecentCategoryDoesNotRequestMissingGridFocus() {
        val vm = HomeViewModel(RecentlyWatchedManager(RuntimeEnvironment.getApplication()))
        val channels = listOf(M3UChannel("News", "Group", null, "https://provider.example/1", null, null, null, false))
        compose.setContent { IPTVTheme { HomeScreen(channels, { _, _ -> }, vm) } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Recently viewed").fetchSemanticsNodes().isNotEmpty() }
        focus("Recently viewed").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onAllNodesWithText("Recently viewed").onFirst().assertIsFocused()
    }

    @Test fun remoteCanReachGridAndSidebarTargetsAfterScrolling() {
        val vm = HomeViewModel(RecentlyWatchedManager(RuntimeEnvironment.getApplication()))
        val channels = (0 until 100).map { M3UChannel("Channel$it", "Group000", null, "https://provider.example/$it", null, null, null, false) } +
            (1..40).map { M3UChannel("Other$it", "Group%03d".format(it), null, "https://provider.example/other/$it", null, null, null, false) }
        compose.setContent { IPTVTheme { HomeScreen(channels, { _, _ -> }, vm) } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Group000").fetchSemanticsNodes().isNotEmpty() }
        focus("Group000").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Channel0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("channel_grid").performScrollToIndex(80)
        focus("Group000").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Channel0").assertIsFocused()
        compose.onNodeWithTag("category_list").performScrollToIndex(35)
        compose.onNodeWithText("Channel0").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onAllNodesWithText("Group000").onFirst().assertIsFocused()
    }
}
