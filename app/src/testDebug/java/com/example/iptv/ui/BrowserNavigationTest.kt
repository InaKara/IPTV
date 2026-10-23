package com.example.iptv.ui

import android.app.Application
import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.BrowserPreferences
import com.example.iptv.data.storage.CredentialsManager
import com.example.iptv.data.storage.RecentlyWatchedManager
import com.example.iptv.ui.browser.BrowserDestination
import com.example.iptv.ui.browser.GridDensity
import com.example.iptv.ui.home.BrowserScreen
import com.example.iptv.ui.home.HomeViewModel
import com.example.iptv.ui.theme.IPTVTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class, ExperimentalTvMaterial3Api::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
class BrowserNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private lateinit var back: OnBackPressedDispatcher
    private lateinit var vm: HomeViewModel
    private var browsing by mutableStateOf(true)
    private var selected = -1
    private var delivered = emptyList<M3UChannel>()
    private val channels = (0..31).map {
        M3UChannel(if (it == 0) "Local News" else "Channel $it", "Live", null,
            "https://provider.example/$it", null, null, null, false, if (it == 0) "101" else null)
    }

    @Before fun clearPreferences() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        RuntimeEnvironment.getApplication().getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @After fun close() { compose.runOnIdle { store.clear() }; Dispatchers.resetMain() }

    private fun render(source: List<M3UChannel> = channels) {
        val app = RuntimeEnvironment.getApplication()
        val credentials = CredentialsManager(app).apply { save("https://provider.example", "demo", "synthetic") }
        vm = HomeViewModel(RecentlyWatchedManager(app), preferences = BrowserPreferences(app, credentials))
        store.put("browser", vm)
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            IPTVTheme {
                if (browsing) BrowserScreen(source, { index, entries -> selected = index; delivered = entries; browsing = false }, vm)
                else Text("Playback destination")
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("browser").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun focusTag(tag: String) = compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
    private fun openChannels() {
        compose.onNodeWithText("Live").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("tile_0").assertIsFocused()
    }

    @Test fun liveVodSidebarOrderDefaultAndFilteredPlaybackReturnUseCorrectSection() {
        val mixed = (0..47).map { M3UChannel("Item $it", "Mixed", null,
            "https://provider.example/item/$it", null, null, null, it % 2 == 1) }
        render(mixed)
        compose.onNodeWithTag("sidebar_CATEGORIES").assertIsFocused().assertIsSelected()
        compose.onNodeWithTag("browser_heading").assertTextEquals("Live TV")
        val names = listOf("CATEGORIES", "VOD", "FAVORITES", "SEARCH", "SETTINGS")
        val positions = names.map { compose.onNodeWithTag("sidebar_$it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top }
        assertEquals(positions.sorted(), positions)
        compose.onNodeWithContentDescription("Live TV").assertExists()
        compose.onNodeWithContentDescription("VOD").assertExists()
        names.forEachIndexed { index, name ->
            compose.onNodeWithTag("sidebar_$name").assertIsFocused()
            if (index < names.lastIndex) compose.onNodeWithTag("sidebar_$name").performKeyInput { pressKey(Key.DirectionDown) }
        }
        compose.onNodeWithTag("sidebar_SETTINGS").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("sidebar_SETTINGS").assertIsFocused()
        assertEquals(BrowserDestination.CATEGORIES, vm.browser.state.value.location.destination)
        assertEquals(-1, selected)
        focusTag("sidebar_VOD").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("browser_heading").assertTextEquals("VOD")
        compose.onNodeWithText("24 channels").assertIsDisplayed()
        compose.onNodeWithText("Mixed").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performKeyInput { pressKey(Key.DirectionCenter) }
        assertTrue(vm.browser.state.value.items.all { it.entry!!.channel.isVod })
        compose.onNodeWithTag("browser_grid").performScrollToIndex(22)
        focusTag("tile_22").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(mixed.filter { it.isVod }, delivered)
        assertEquals(22, selected)
        compose.runOnIdle { vm.returnFromPlayback(23); browsing = true }
        compose.onNodeWithTag("tile_23").assertIsFocused().assertIsDisplayed()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("browser_heading").assertTextEquals("VOD")
        compose.onNodeWithText("Mixed").assertIsFocused()
        focusTag("sidebar_CATEGORIES").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("browser_heading").assertTextEquals("Live TV")
        compose.onNodeWithText("Mixed").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performKeyInput { pressKey(Key.DirectionCenter) }
        assertTrue(vm.browser.state.value.items.all { it.entry!!.channel.isVod.not() })
    }

    @Test fun emptyVodRecentKeepsSidebarUsableAndReturnsToVodRoot() {
        render()
        compose.onNodeWithTag("sidebar_CATEGORIES").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("sidebar_CATEGORIES").assertIsFocused()
        focusTag("sidebar_VOD").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Live").assertDoesNotExist()
        compose.onNodeWithTag("tile_0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("sidebar_VOD").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("sidebar_VOD").assertIsFocused()
        compose.onNodeWithText("No channels in this category.").assertIsDisplayed()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("browser_heading").assertTextEquals("VOD")
        compose.onNodeWithTag("tile_0").assertIsFocused()
        compose.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test fun gridEdgesAndAllSidebarButtonsMoveFocusWithoutActivating() {
        render(); openChannels()
        compose.onNodeWithTag("tile_0").performKeyInput { repeat(4) { pressKey(Key.DirectionRight) }; pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("tile_4").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("tile_9").assertIsFocused().performKeyInput { repeat(4) { pressKey(Key.DirectionLeft) }; pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("sidebar_CATEGORIES").assertIsFocused().performKeyInput { repeat(4) { pressKey(Key.DirectionDown) } }
        compose.onNodeWithTag("sidebar_SETTINGS").assertIsFocused()
        assertEquals(BrowserDestination.CATEGORIES, vm.browser.state.value.location.destination)
        assertEquals(-1, selected)
        compose.onNodeWithTag("sidebar_SETTINGS").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("density_STANDARD").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionCenter) }
        compose.waitUntil(5000) { vm.browser.state.value.density == GridDensity.COMPACT }
        compose.onNodeWithTag("density_COMPACT").assertIsFocused().assertIsSelected()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("sidebar_SETTINGS").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("tile_5").assertIsFocused()
        compose.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test fun menuFavoritesAndBackRestorePriorCategoryWithoutPlayback() {
        render(); openChannels()
        compose.onNodeWithTag("tile_0").performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithTag("favorite_action").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(5000) { vm.browser.state.value.favorites.size == 1 }
        focusTag("sidebar_FAVORITES").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("tile_0").assertIsFocused()
        compose.onNodeWithText("101   Local News").assertIsDisplayed()
        compose.onNodeWithTag("tile_0").performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Remove from Favorites").assertIsDisplayed()
        compose.onNodeWithTag("favorite_action").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(5000) { vm.browser.state.value.favorites.isEmpty() }
        compose.onNodeWithTag("sidebar_FAVORITES").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("sidebar_FAVORITES").assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        assertEquals("group:Live", vm.browser.state.value.location.categoryId)
        compose.onNodeWithTag("sidebar_FAVORITES").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("tile_0").assertIsFocused()
        assertEquals(-1, selected)
    }

    @Test fun searchKeyboardQueryAndResultFocusSurvivePlaybackAndAuxiliaryReturn() {
        render()
        focusTag("sidebar_SEARCH").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("search_input").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("search_editor").performTextInput("nEwS")
        compose.onNodeWithTag("search_editor").assertTextEquals("nEwS")
        assertEquals("nEwS", vm.browser.state.value.query)
        compose.waitUntil(5000) { vm.browser.state.value.items.size == 1 }
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("search_input").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("tile_0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(0, selected)
        compose.onNodeWithText("Playback destination").assertIsDisplayed()
        compose.runOnIdle { vm.returnFromPlayback(selected); browsing = true }
        compose.onNodeWithTag("tile_0").assertIsFocused()
        compose.onNodeWithTag("search_input").assertTextEquals("nEwS")
        focusTag("sidebar_SETTINGS").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("sidebar_SETTINGS").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("tile_0").assertIsFocused()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithText("Recently viewed").assertIsDisplayed()
        assertEquals(BrowserDestination.CATEGORIES, vm.browser.state.value.location.destination)
    }

    @Test fun returningFromPlaybackTracksSwitchedChannelAndKeepsCategoryScroll() {
        render(); openChannels()
        compose.onNodeWithTag("browser_grid").performScrollToIndex(25)
        focusTag("tile_25").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(25, selected)
        compose.runOnIdle { vm.returnFromPlayback(30); browsing = true }
        compose.onNodeWithTag("tile_30").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithTag("tile_30").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("sidebar_CATEGORIES").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("tile_30").assertIsFocused()
    }
}
