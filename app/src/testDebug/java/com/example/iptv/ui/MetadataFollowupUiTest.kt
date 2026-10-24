package com.example.iptv.ui

import android.app.Application
import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.example.iptv.data.library.*
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.*
import com.example.iptv.ui.browser.*
import com.example.iptv.ui.home.*
import com.example.iptv.ui.library.LibraryViewModel
import com.example.iptv.ui.theme.IPTVTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.abs
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import com.example.iptv.diagnostics.VerboseLogging

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
class MetadataFollowupUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: LibraryDatabase
    private lateinit var repo: LibraryRepository
    private lateinit var account: LibraryAccount
    private lateinit var prefs: BrowserPreferences
    private lateinit var client: OkHttpClient
    private lateinit var server: MockWebServer
    private lateinit var home: HomeViewModel
    private lateinit var library: LibraryViewModel
    private lateinit var source: List<M3UChannel>
    private lateinit var back: OnBackPressedDispatcher
    private val store = ViewModelStore()
    private val film = LibraryEntry(LibraryKind.MOVIE, "42", "Sample film", "1", "Films")

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        app.noBackupFilesDir.resolve("library.db").delete()
        server = MockWebServer().apply { start() }
        val credentials = CredentialsManager(app).apply { save(server.url("/").toString(), "demo", "synthetic") }
        db = LibraryDatabase(app); client = OkHttpClient(); repo = LibraryRepository(db, credentials, client); account = repo.account()
        source = listOf(M3UChannel("News", "Live", null, server.url("/live/1.ts").toString(), null, null, null, false),
            M3UChannel(film.name, "Films", null, account.stream(film), null, null, null, true))
        val recents = RecentlyWatchedManager(app)
        runBlocking { recents.activate(credentials.serverUrl, credentials.username, source) }
        prefs = BrowserPreferences(app, credentials)
        home = HomeViewModel(recents, preferences = prefs, library = repo, logging = VerboseLogging(app))
        library = LibraryViewModel(repo, recents, prefs)
        store.put("home", home); store.put("library", library)
    }
    @After fun close() {
        compose.runOnIdle { store.clear() }
        db.close(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown(); server.close(); Dispatchers.resetMain()
    }
    private fun render() {
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            IPTVTheme { BrowserScreen(source, { _, _ -> fail("No title may autoplay") }, home, library = library) }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("browser").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun focus(tag: String) = compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
    private fun vod() { compose.onNodeWithTag("sidebar_VOD").performClick(); compose.onNodeWithTag("library_root_0").assertExists() }
    private fun waitText(text: String) { compose.waitUntil(5000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun noNestedShortcuts() {
        listOf("library_back", "library_refresh", "library_fallback").forEach { compose.onNodeWithTag(it).assertDoesNotExist() }
        compose.onNodeWithText("Playlist categories").assertDoesNotExist()
    }
    @Test fun pendingListsShowLoadingThenSelectableUpdatingCacheAndBackCancels() {
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                release.await(8, TimeUnit.SECONDS)
                return MockResponse().setBody(if (request.requestUrl!!.queryParameter("action")!!.endsWith("categories"))
                    """[{"category_id":"1","category_name":"Films"}]""" else "[]")
            }
        }
        try {
            render(); vod(); compose.onNodeWithTag("library_root_0").performClick()
            waitText("Loading movies…")
            compose.onNodeWithTag("library_loading").assertIsDisplayed()
            compose.onNodeWithText("No channels in this category.").assertDoesNotExist()
            compose.onNodeWithText("All movies").assertDoesNotExist()
            compose.runOnIdle { back.onBackPressed() }
            compose.onNodeWithTag("library_root_0").assertIsFocused()
            release.countDown()
            db.transaction { sql ->
                db.category(sql, account.cache, LibraryKind.MOVIE, LibraryCategory("1", "Films"), 0)
                db.fresh(sql, account.cache, LibraryKind.MOVIE, System.currentTimeMillis())
            }
            compose.onNodeWithTag("library_root_0").performClick(); waitText("Films")
            val update = CountDownLatch(1)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    update.await(8, TimeUnit.SECONDS)
                    return MockResponse().setResponseCode(503)
                }
            }
            try {
                compose.runOnIdle { library.refresh() }; waitText("Updating movies…")
                compose.onNodeWithText("Films").assertHasClickAction().assertIsDisplayed()
                compose.onNodeWithText("Films").performClick()
                compose.onNodeWithTag("browser_heading").assertTextEquals("Films")
                compose.onNodeWithTag("sidebar_CATEGORIES").performClick()
                compose.onNodeWithTag("library_loading").assertDoesNotExist()
            } finally { update.countDown() }
        } finally { release.countDown() }
    }
    @Test fun seasonHeartRoutesGlobalFavoriteWithoutBulkEpisodeFavoritesAndSettingsLogToggleIsRemoteUsable() {
        val show = LibraryEntry(LibraryKind.SERIES, "9", "A show", "2", "Drama")
        db.transaction { sql ->
            db.entry(sql, account.cache, show)
            db.entry(sql, account.cache, LibraryEntry(LibraryKind.EPISODE, "10", "Pilot", "2", "Drama", seriesId = "9", season = "Specials"))
            db.putDetails(sql, account, show, LibraryDetails("Story"), System.currentTimeMillis(), 100)
        }
        render(); vod()
        listOf("MOVIES", "SERIES", "CONTINUE", "HISTORY", "FOLDER").forEach {
            compose.onNodeWithTag("library_icon_$it", useUnmergedTree = true).assertExists()
        }
        compose.runOnIdle { library.externalSeason(SeasonFavorite("9", "Specials", "A show", "2", "Drama"), true) }
        waitText("Pilot")
        compose.onNodeWithTag("library_favorite").assertContentDescriptionEquals("Add season to Favorites").performClick()
        compose.waitUntil(5000) { library.state.value.favorite }
        assertEquals(1, runBlocking { prefs.load() }.favorites.size)
        compose.onNodeWithTag("sidebar_FAVORITES").performClick(); waitText("A show · Specials")
        compose.onNodeWithText("A show · Specials").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performKeyInput { pressKey(Key.DirectionCenter) }; waitText("Pilot")
        compose.onNodeWithTag("browser_heading").assertTextEquals("Season Specials")
        compose.runOnIdle { back.onBackPressed() }; waitText("A show · Specials")
        compose.onNodeWithText("A show · Specials").assertIsFocused().performClick(); waitText("Pilot")
        compose.onNodeWithTag("library_favorite").performClick()
        compose.waitUntil(5000) { !library.state.value.favorite }
        assertTrue(runBlocking { prefs.load() }.favorites.isEmpty())
        compose.onNodeWithTag("sidebar_SETTINGS").performClick()
        focus("adult_filter").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("verbose_logging").assertIsFocused().assertIsOff().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("verbose_logging").assertIsOn()
        assertTrue(VerboseLogging(RuntimeEnvironment.getApplication()).enabled.value)
        compose.onNodeWithTag("verbose_logging").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("adult_filter").assertIsFocused().assertIsOn()
    }
    @Test fun freshMovieCategoriesReuseCatalogWithoutFullCatalogSpinner() {
        db.transaction { sql ->
            db.category(sql, account.cache, LibraryKind.MOVIE, LibraryCategory("1", "Films"), 0)
            db.category(sql, account.cache, LibraryKind.MOVIE, LibraryCategory("2", "Other films"), 1)
            db.entry(sql, account.cache, film)
            db.entry(sql, account.cache, film.copy(id = "43", name = "Other film", category = "2"))
            db.fresh(sql, account.cache, LibraryKind.MOVIE, System.currentTimeMillis())
        }
        render(); vod(); compose.onNodeWithTag("library_root_0").performClick(); waitText("Films")
        compose.waitUntil(5000) { !library.state.value.loading && !library.state.value.preparing }
        listOf("Films" to film.name, "Other films" to "Other film").forEach { (category, name) ->
            compose.onNodeWithText(category).performClick(); waitText(name)
            compose.onNodeWithTag("library_loading").assertDoesNotExist()
            compose.onNodeWithText("No channels in this category.").assertDoesNotExist()
            compose.runOnIdle { back.onBackPressed() }; waitText(category)
            compose.onNodeWithTag("library_loading").assertDoesNotExist()
        }
        assertEquals(0, server.requestCount)
    }
    @Test fun rootTilesMatchBrowserAtEveryDensityAndEmptyReturnsRestoreFocus() {
        render()
        for ((densityIndex, density) in GridDensity.values().withIndex()) {
            compose.runOnIdle { home.setDensity(density) }
            compose.waitUntil(5000) { home.browser.state.value.density == density }
            compose.onNodeWithTag("sidebar_CATEGORIES").performClick()
            val normal = compose.onNodeWithTag("tile_0").getUnclippedBoundsInRoot()
            vod()
            if (densityIndex == 0) assertFalse("Opening the five root tiles must not open metadata storage",
                RuntimeEnvironment.getApplication().noBackupFilesDir.resolve("library.db").exists())
            repeat(5) { index ->
                val tile = compose.onNodeWithTag("library_root_$index").getUnclippedBoundsInRoot()
                assertTrue("$density width", abs((tile.right - tile.left).value - (normal.right - normal.left).value) <= 1)
                assertTrue("$density height", abs((tile.bottom - tile.top).value - (normal.bottom - normal.top).value) <= 1)
            }
            focus("library_root_0").performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNodeWithTag("library_root_1").assertIsFocused()
            focus("library_root_2").performKeyInput { pressKey(Key.DirectionCenter) }
            waitText("Continue watching"); noNestedShortcuts()
            compose.waitUntil(5000) { !library.state.value.loading && !library.state.value.preparing }
            compose.onNodeWithTag("sidebar_VOD").assertIsFocused()
            compose.runOnIdle { back.onBackPressed() }
            compose.onNodeWithTag("library_root_2").assertIsFocused()
        }
        assertEquals("No provider API is needed for root/empty navigation", 0, server.requestCount)
    }
    @Test fun failureOnlyRetryAndSingleActualWatchedActionWorkAcrossAllEntryPaths() {
        db.transaction { sql ->
            db.category(sql, account.cache, LibraryKind.MOVIE, LibraryCategory("1", "Films"), 0)
            db.entry(sql, account.cache, film); db.fresh(sql, account.cache, LibraryKind.MOVIE, System.currentTimeMillis())
        }
        server.enqueue(MockResponse().setResponseCode(503))
        render(); vod(); compose.onNodeWithTag("library_root_0").performClick()
        waitText("All movies"); compose.onNodeWithText("All movies").performClick()
        waitText(film.name); compose.onNodeWithText(film.name).performClick()
        waitText("Retry loading details"); noNestedShortcuts()
        compose.onNodeWithTag("library_play").assertExists()
        server.enqueue(MockResponse().setBody("""{"info":{"plot":"Restored details"}}"""))
        compose.onNodeWithTag("library_retry").performClick(); waitText("Restored details")
        compose.onNodeWithTag("library_retry").assertDoesNotExist()
        compose.onNodeWithTag("library_actions").assertDoesNotExist()
        compose.onNodeWithTag("library_watch").assertTextEquals("Unwatched").assertContentDescriptionEquals("Mark watched").performClick()
        waitText("Watched")
        compose.onNodeWithTag("library_watch").assertContentDescriptionEquals("Mark unwatched").performClick(); waitText("Unwatched")
        assertFalse(runBlocking { repo.progress(account, source[1]) }.watched)
        val legacyId = BrowserCatalog.create(source).channels[1].id
        runBlocking { prefs.load(); prefs.toggle(legacyId) }
        fun globalMenu(expected: String, other: String) {
            compose.onNodeWithText(film.name).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                .performKeyInput { pressKey(Key.Menu) }
            waitText(expected); compose.onAllNodesWithText(expected).assertCountEquals(1)
            compose.onNodeWithText(other).assertDoesNotExist(); compose.onNodeWithText(expected).performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("favorite_action").fetchSemanticsNodes().isEmpty() }
        }
        repeat(3) { route ->
            when (route) {
                0 -> { compose.onNodeWithTag("sidebar_FAVORITES").performClick(); waitText(film.name) }
                1 -> { compose.onNodeWithTag("sidebar_SEARCH").performClick(); compose.runOnIdle { home.search("Sample") }; waitText(film.name) }
                else -> { vod(); compose.onNodeWithTag("library_root_4").performClick(); compose.onNodeWithText("Films").performClick(); waitText(film.name) }
            }
            globalMenu("Mark watched", "Mark unwatched")
            globalMenu("Mark unwatched", "Mark watched")
            assertEquals(0L, runBlocking { repo.progress(account, source[1]) }.position)
        }
        server.enqueue(MockResponse().setResponseCode(503))
        vod(); compose.onNodeWithTag("library_root_1").performClick()
        waitText("Retry loading series"); noNestedShortcuts()
    }
}
