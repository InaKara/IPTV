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
import kotlinx.coroutines.runBlocking
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
import org.robolectric.annotation.GraphicsMode

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
    private var firstBrowseFilter: Boolean? = null
    private var firstBrowseDestination: BrowserDestination? = null
    private val channels = (0..31).map {
        M3UChannel(if (it == 0) "Local News" else "Channel $it", "Live", null,
            "https://provider.example/$it", null, null, null, false, if (it == 0) "101" else null)
    }

    @Before fun clearPreferences() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        RuntimeEnvironment.getApplication().getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @After fun close() { compose.runOnIdle { store.clear() }; Dispatchers.resetMain() }

    private fun render(source: List<M3UChannel> = channels, initialLive: Boolean = true) {
        val app = RuntimeEnvironment.getApplication()
        val credentials = CredentialsManager(app).apply { save("https://provider.example", "demo", "synthetic") }
        vm = HomeViewModel(RecentlyWatchedManager(app), preferences = BrowserPreferences(app, credentials))
        if (initialLive) { vm.browser.open(BrowserDestination.CATEGORIES); vm.browser.focusArea(com.example.iptv.ui.browser.BrowserFocus.SIDEBAR) }
        store.put("browser", vm)
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            IPTVTheme {
                if (browsing) BrowserScreen(source, { index, entries -> selected = index; delivered = entries; browsing = false }, vm,
                    onBrowseReady = {
                        if (firstBrowseFilter == null) firstBrowseFilter = vm.browser.state.value.filterAdult
                        if (firstBrowseDestination == null) firstBrowseDestination = vm.browser.state.value.location.destination
                    })
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

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun headingsFollowOpenedCategoriesDestinationsReturnsRefreshAndLongBlankNames() {
        val longName = "International films and documentaries ".repeat(12)
        val source = listOf(channels[0], channels[1].copy(groupTitle = longName, isVod = true),
            channels[2].copy(groupTitle = "   "))
        render(source)
        openChannels()
        compose.onNodeWithTag("browser_heading").assertTextEquals("Live")
        focusTag("sidebar_FAVORITES")
        compose.onNodeWithTag("browser_heading").assertTextEquals("Live")
        compose.onNodeWithTag("sidebar_FAVORITES").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("browser_heading").assertTextEquals("Favorites")
        focusTag("sidebar_SEARCH").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("browser_heading").assertTextEquals("Search")
        focusTag("sidebar_SETTINGS").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("browser_heading").assertTextEquals("Settings")
        repeat(3) { compose.runOnIdle { back.onBackPressed() } }
        compose.onNodeWithTag("browser_heading").assertTextEquals("Live")
        focusTag("tile_0").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { vm.returnFromPlayback(0); browsing = true }
        compose.onNodeWithTag("browser_heading").assertTextEquals("Live")
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("browser_heading").assertTextEquals("Live TV")
        compose.onNodeWithText("Recently viewed").performClick()
        compose.onNodeWithTag("browser_heading").assertTextEquals("Recently viewed")
        focusTag("sidebar_VOD").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("browser_heading").assertTextEquals("VOD")
        compose.onNodeWithText(longName).performClick()
        val heading = compose.onNodeWithTag("browser_heading").assertTextEquals(longName)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        heading.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        assertTrue(layouts.single().isLineEllipsized(0))
        val bounds = heading.fetchSemanticsNode().boundsInRoot
        val grid = compose.onNodeWithTag("browser_grid").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.bottom <= grid.top)
        assertTrue(bounds.left >= grid.left && bounds.right <= grid.right + 1)
        compose.runOnIdle { vm.loadChannels(listOf(source[0], source[2])) }
        compose.waitUntil(5000) { vm.browser.state.value.location.categoryId == null }
        compose.onNodeWithTag("browser_heading").assertTextEquals("VOD")
        focusTag("sidebar_CATEGORIES").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("   ").performClick()
        compose.onNodeWithTag("browser_heading").assertTextEquals("Channels")
    }

    @Test fun adultSwitchUsesRemoteFocusAndRetainsHiddenCategoryFavoritesSearchAndPlaybackRules() {
        val safe = channels[0]
        val hidden = channels[1].copy(groupTitle = "Adult films", isVod = true)
        render(listOf(safe, hidden))
        assertTrue(vm.browser.state.value.filterAdult)
        assertEquals(true, firstBrowseFilter)
        focusTag("sidebar_SETTINGS").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("density_STANDARD").performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("adult_filter").assertIsFocused().assertIsOn().assertIsDisplayed()
        assertTrue(vm.browser.state.value.filterAdult)
        compose.onNodeWithTag("adult_filter").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(5000) { !vm.browser.state.value.filterAdult }
        compose.onNodeWithTag("adult_filter").assertIsFocused().assertIsOff()
        compose.onNodeWithText("Filtering inactive", substring = true).assertIsDisplayed()
        focusTag("sidebar_VOD").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Adult films").performClick()
        compose.onNodeWithTag("browser_heading").assertTextEquals("Adult films")
        compose.onNodeWithTag("tile_0").performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithTag("favorite_action").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(5000) { vm.browser.state.value.favorites.size == 1 }
        val favorites = vm.browser.state.value.favorites
        focusTag("sidebar_SETTINGS").performKeyInput { pressKey(Key.DirectionCenter) }
        focusTag("sidebar_SETTINGS").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("density_STANDARD").performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("adult_filter").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(5000) { vm.browser.state.value.filterAdult }
        compose.onNodeWithTag("adult_filter").assertIsFocused().assertIsOn()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("browser_heading").assertTextEquals("VOD")
        compose.onNodeWithTag("tile_0").assertIsFocused()
        compose.onNodeWithText("Adult films").assertDoesNotExist()
        focusTag("sidebar_FAVORITES").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("No favorite channels available. Press Menu on a channel to add it.").assertIsDisplayed()
        assertEquals(favorites, vm.browser.state.value.favorites)
        focusTag("sidebar_SEARCH").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { vm.search("Channel") }
        compose.waitUntil(5000) { vm.browser.state.value.query == "Channel" }
        compose.onNodeWithText("No matching channels.").assertIsDisplayed()
        focusTag("sidebar_SETTINGS").performKeyInput { pressKey(Key.DirectionCenter) }
        focusTag("sidebar_SETTINGS").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("density_STANDARD").performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("adult_filter").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("density_COMPACT").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("adult_filter").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(5000) { !vm.browser.state.value.filterAdult }
        compose.runOnIdle { back.onBackPressed() }
        compose.waitUntil(5000) { vm.browser.state.value.items.size == 1 }
        focusTag("tile_0").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(listOf(hidden), delivered)
        compose.runOnIdle { vm.returnFromPlayback(0); browsing = true; vm.toggleAdultFilter() }
        compose.waitUntil(5000) { vm.browser.state.value.filterAdult && vm.browser.state.value.items.isEmpty() }
        focusTag("sidebar_CATEGORIES").performKeyInput { pressKey(Key.DirectionCenter) }
        openChannels()
        compose.onNodeWithTag("tile_0").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(listOf(safe), delivered)
    }

    @Test fun savedFilterChoiceIsAppliedBeforeFirstBrowserLayoutAndSurvivesAccountReload() {
        RuntimeEnvironment.getApplication().getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("filter_adult", false).commit()
        val source = listOf(channels[0], channels[1].copy(groupTitle = "ADULT"))
        render(source)
        assertFalse(vm.browser.state.value.filterAdult)
        assertEquals(false, firstBrowseFilter)
        compose.onNodeWithText("ADULT").assertIsDisplayed()
        val credentials = CredentialsManager(RuntimeEnvironment.getApplication())
        compose.runOnIdle {
            credentials.save("https://provider.example", "other", "synthetic")
            vm.loadChannels(source + channels[2])
        }
        compose.waitUntil(5000) { vm.browser.state.value.categories.find { it.name == "Live" }?.channels?.size == 2 }
        assertFalse(vm.browser.state.value.filterAdult)
        compose.onNodeWithTag("browser_heading").assertTextEquals("Favorites")
        compose.onNodeWithTag("sidebar_FAVORITES").assertIsFocused()
        focusTag("sidebar_CATEGORIES").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("ADULT").assertIsDisplayed()
        compose.runOnIdle { vm.toggleAdultFilter() }
        compose.waitUntil(5000) { vm.browser.state.value.filterAdult }
        compose.onNodeWithText("ADULT").assertDoesNotExist()
        compose.runOnIdle { vm.loadChannels(source.toList()) }
        compose.waitUntil(5000) { vm.browser.state.value.items.size == 2 }
        compose.onNodeWithText("ADULT").assertDoesNotExist()
    }

    @Test fun liveVodSidebarOrderDefaultAndFilteredPlaybackReturnUseCorrectSection() {
        val mixed = (0..47).map { M3UChannel("Item $it", "Mixed", null,
            "https://provider.example/item/$it", null, null, null, it % 2 == 1) }
        render(mixed, initialLive = false)
        compose.onNodeWithTag("sidebar_FAVORITES").assertIsFocused().assertIsSelected()
        compose.onNodeWithTag("browser_heading").assertTextEquals("Favorites")
        val names = listOf("CATEGORIES", "VOD", "FAVORITES", "SEARCH", "SETTINGS")
        val positions = names.map { compose.onNodeWithTag("sidebar_$it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top }
        assertEquals(positions.sorted(), positions)
        compose.onNodeWithContentDescription("Live TV").assertExists()
        compose.onNodeWithContentDescription("VOD").assertExists()
        focusTag("sidebar_CATEGORIES").performKeyInput { pressKey(Key.DirectionUp) }
        names.forEachIndexed { index, name ->
            compose.onNodeWithTag("sidebar_$name").assertIsFocused()
            if (index < names.lastIndex) compose.onNodeWithTag("sidebar_$name").performKeyInput { pressKey(Key.DirectionDown) }
        }
        compose.onNodeWithTag("sidebar_SETTINGS").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("sidebar_SETTINGS").assertIsFocused()
        assertEquals(BrowserDestination.FAVORITES, vm.browser.state.value.location.destination)
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
        assertEquals("", vm.browser.state.value.query)
        assertTrue(vm.browser.state.value.items.isEmpty())
        compose.onNodeWithTag("search_editor").performImeAction()
        assertEquals("nEwS", vm.browser.state.value.query)
        compose.waitUntil(5000) { vm.browser.state.value.items.size == 1 }
        compose.onNodeWithTag("search_submit").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
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

    @Test fun searchDraftCancelAndRemoteSubmitBrowseLocationRestoreExactResult() {
        render()
        focusTag("sidebar_SEARCH").performClick()
        compose.onNodeWithTag("search_input").performClick()
        compose.onNodeWithTag("search_editor").performTextInput("Channel 25")
        assertEquals("", vm.browser.state.value.query)
        compose.runOnIdle { back.onBackPressed() }
        assertEquals("", vm.browser.state.value.draftQuery)
        compose.onNodeWithTag("search_input").performClick()
        compose.onNodeWithTag("search_editor").performTextInput("Channel 25")
        compose.onNodeWithTag("search_submit").performClick()
        compose.waitUntil(5000) { vm.browser.state.value.items.size == 1 && !vm.browser.state.value.searching }
        compose.onNodeWithTag("search_submit").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("tile_0").assertIsFocused().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithTag("browse_location").performClick()
        assertEquals("group:Live", vm.browser.state.value.location.categoryId)
        compose.onNodeWithTag("tile_25").assertIsFocused()
        assertEquals(-1, selected)
        compose.runOnIdle { back.onBackPressed() }
        assertEquals(BrowserDestination.SEARCH, vm.browser.state.value.location.destination)
        compose.onNodeWithTag("search_input").assertTextEquals("Channel 25")
        compose.onNodeWithTag("tile_0").assertIsFocused()
    }

    @Test fun savedFavoritesOpenOnFirstBrowseWithHeartFocusAndRetainPlaybackAndNestedReturns() {
        val app = RuntimeEnvironment.getApplication()
        val credentials = CredentialsManager(app).apply { save("https://provider.example", "demo", "synthetic") }
        val saved = BrowserPreferences(app, credentials)
        val index = com.example.iptv.ui.browser.BrowserCatalog.create(channels)
        runBlocking { saved.load(); saved.toggle(index.channels[25].id); saved.toggle(index.channels[0].id) }
        render(initialLive = false)
        compose.onNodeWithTag("browser_heading").assertTextEquals("Favorites")
        compose.onNodeWithTag("sidebar_FAVORITES").assertIsFocused().assertIsSelected()
        assertEquals(BrowserDestination.FAVORITES, firstBrowseDestination)
        assertEquals(listOf(channels[0], channels[25]), vm.browser.state.value.items.map { it.entry!!.channel })
        compose.onNodeWithTag("sidebar_FAVORITES").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("tile_0").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("tile_1").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(listOf(channels[0], channels[25]), delivered)
        compose.runOnIdle { vm.returnFromPlayback(1); browsing = true }
        compose.onNodeWithTag("tile_1").assertIsFocused()
        focusTag("sidebar_SEARCH").performKeyInput { pressKey(Key.DirectionCenter) }
        focusTag("sidebar_SETTINGS").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("browser_heading").assertTextEquals("Search")
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("browser_heading").assertTextEquals("Favorites")
        assertEquals(index.channels[25].id, vm.browser.state.value.bookmark.focusedId)
        compose.onNodeWithTag("sidebar_SEARCH").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("tile_1").assertIsFocused()
        assertFalse(vm.browser.state.value.canGoBack)
    }

    @Test fun emptyAndFilteredFavoritesKeepInitialHeartFocusAndRootBack() {
        val source = listOf(channels[0], channels[1].copy(name = "Hidden favorite", groupTitle = "Adult films"))
        val app = RuntimeEnvironment.getApplication()
        val credentials = CredentialsManager(app).apply { save("https://provider.example", "demo", "synthetic") }
        val saved = BrowserPreferences(app, credentials)
        val hidden = com.example.iptv.ui.browser.BrowserCatalog.create(source).channels[1].id
        runBlocking { saved.load(); saved.toggle(hidden) }
        render(source, initialLive = false)
        compose.onNodeWithTag("sidebar_FAVORITES").assertIsFocused().assertIsSelected()
        compose.onNodeWithTag("browser_heading").assertTextEquals("Favorites")
        compose.onNodeWithText("Hidden favorite").assertDoesNotExist()
        compose.onNodeWithText(app.getString(com.example.iptv.R.string.favorites_empty)).assertIsDisplayed()
        assertEquals(true, firstBrowseFilter); assertEquals(BrowserDestination.FAVORITES, firstBrowseDestination)
        assertFalse(vm.browser.state.value.canGoBack); assertFalse(vm.browser.back())
        compose.runOnIdle { vm.toggleFavorite(hidden) }
        compose.waitUntil(5000) { vm.browser.state.value.favorites.isEmpty() }
        compose.onNodeWithTag("sidebar_FAVORITES").assertIsFocused()
        compose.runOnIdle { vm.toggleFavorite(hidden) }
        compose.waitUntil(5000) { hidden in vm.browser.state.value.favorites }
        compose.runOnIdle { vm.toggleAdultFilter() }
        compose.waitUntil(5000) { !vm.browser.state.value.filterAdult }
        compose.onNodeWithText("Hidden favorite").assertIsDisplayed()
        compose.onNodeWithTag("sidebar_FAVORITES").assertIsFocused()
        compose.runOnIdle { vm.toggleAdultFilter() }
        compose.waitUntil(5000) { vm.browser.state.value.filterAdult }
        compose.onNodeWithText("Hidden favorite").assertDoesNotExist()
        assertFalse(vm.browser.state.value.canGoBack)
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
