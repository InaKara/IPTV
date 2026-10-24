package com.example.iptv.ui.library

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.example.iptv.data.library.*
import com.example.iptv.data.storage.*
import com.example.iptv.ui.browser.BrowserCatalog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class SeasonNavigationTest {
    @Test fun missingSeasonFetchesOnlySelectedSeriesAndRetainsUnavailableBookmarkAndAdultReversibility() = runBlocking<Unit> {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        app.noBackupFilesDir.resolve("library.db").delete()
        val server = MockWebServer().apply { start() }
        val credentials = CredentialsManager(app).apply { save(server.url("/").toString(), "demo", "synthetic") }
        val db = LibraryDatabase(app); val client = OkHttpClient(); val repo = LibraryRepository(db, credentials, client)
        val account = repo.account(); repo.activate(account, BrowserCatalog.create(emptyList()))
        val prefs = BrowserPreferences(app, credentials); prefs.load()
        val season = SeasonFavorite("9", "Specials", "Show", "1", "Drama")
        repo.saveSeason(account, season); prefs.toggle(season.key(account))
        val vm = LibraryViewModel(repo, RecentlyWatchedManager(app), prefs)
        val store = ViewModelStore().apply { put("vm", vm) }
        suspend fun ready(predicate: (LibraryPage) -> Boolean) = withTimeout(5000) { vm.state.first { !it.loading && predicate(it) } }
        try {
            vm.activate(true)
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setBody("""{"info":{"plot":"Story"},"episodes":{"Specials":[{"id":"10","title":"Pilot"}]}}"""))
            vm.externalSeason(season, true)
            var page = ready { it.rows.size == 1 }
            assertEquals("Pilot", page.rows.single().label); assertTrue(page.favorite)
            val request = server.takeRequest()
            assertEquals("get_series_info", request.requestUrl!!.queryParameter("action"))
            assertEquals("9", request.requestUrl!!.queryParameter("series_id")); assertEquals(1, server.requestCount)
            vm.open(page.rows.single()); ready { it.location.route == LibraryRoute.DETAIL }
            assertFalse(vm.consumeExternalReturn()); assertTrue(vm.back())
            ready { it.location.route == LibraryRoute.EPISODES }
            assertTrue(vm.consumeExternalReturn())
            server.enqueue(MockResponse().setBody("""{"info":{"plot":"Unavailable"},"episodes":{}}"""))
            vm.externalSeason(season, true); ready { it.rows.isNotEmpty() }; vm.refresh()
            page = ready { it.unavailable }
            assertTrue(page.favorite); assertTrue(season.key(account) in prefs.load().favorites)
            vm.toggleFavorite(); ready { !it.favorite && it.unavailable }
            val adult = season.copy(categoryName = "Drama ADULT")
            vm.root(); vm.externalSeason(adult, true)
            assertEquals(LibraryRoute.ROOT, vm.state.value.location.route)
            vm.externalSeason(adult, false); ready { it.location.route == LibraryRoute.EPISODES }
            vm.activate(true); assertEquals(LibraryRoute.ROOT, vm.state.value.location.route)
            assertEquals(2, server.requestCount)
        } finally {
            store.clear(); db.close(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown(); server.close(); Dispatchers.resetMain()
        }
    }
}
