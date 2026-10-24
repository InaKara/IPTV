package com.example.iptv.ui.library

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.example.iptv.data.library.*
import com.example.iptv.data.storage.*
import com.example.iptv.ui.browser.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.OkHttpClient
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class LibraryNavigationTest {
    @Test fun nestedLibraryReturnsProgressAndFallbackRetainBookmarksAndNeverAutoplay() = runBlocking<Unit> {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        app.noBackupFilesDir.resolve("library.db").delete()
        val credentials = CredentialsManager(app).apply { save("https://provider.example", "demo", "synthetic") }
        val db = LibraryDatabase(app)
        val client = OkHttpClient()
        val repo = LibraryRepository(db, credentials, client)
        val account = repo.account()
        val movie = LibraryEntry(LibraryKind.MOVIE, "1", "Film", "a", "Movies")
        val series = LibraryEntry(LibraryKind.SERIES, "2", "Show", "s", "Series")
        db.transaction { sql ->
            db.category(sql, account.cache, movie.kind, LibraryCategory("a", "Movies"), 0)
            db.category(sql, account.cache, series.kind, LibraryCategory("s", "Series"), 0)
            db.entry(sql, account.cache, movie); db.entry(sql, account.cache, series)
            db.entry(sql, account.cache, LibraryEntry(LibraryKind.EPISODE, "3", "Episode", "s", seriesId = "2", season = "Specials"))
            db.entry(sql, account.cache, LibraryEntry(LibraryKind.EPISODE, "4", "Following", "s", ordinal = 1, seriesId = "2", season = "2"))
            db.fresh(sql, account.cache, movie.kind, System.currentTimeMillis()); db.fresh(sql, account.cache, series.kind, System.currentTimeMillis())
            db.putDetails(sql, account, movie, LibraryDetails("A film"), System.currentTimeMillis(), 500)
            db.putDetails(sql, account, series, LibraryDetails("A show"), System.currentTimeMillis(), 500)
        }
        repo.activate(account, BrowserCatalog.create(emptyList()))
        val recents = RecentlyWatchedManager(app)
        recents.activate(credentials.serverUrl, credentials.username, emptyList())
        val prefs = BrowserPreferences(app, credentials)
        val vm = LibraryViewModel(repo, recents, prefs)
        val store = ViewModelStore().apply { put("library", vm) }
        suspend fun ready(predicate: (LibraryPage) -> Boolean): LibraryPage = withTimeout(5000) { vm.state.first { !it.loading && predicate(it) } }
        try {
            vm.activate(true); vm.focus("root_0"); vm.openRoot(LibraryRoute.CATEGORIES)
            var page = ready { it.location.route == LibraryRoute.CATEGORIES && it.rows.size == 2 }
            vm.open(page.rows.first()); page = ready { it.location.route == LibraryRoute.TITLES && it.rows.isNotEmpty() }
            vm.focus(page.rows.single().key); vm.scroll(0, 8); vm.open(page.rows.single())
            page = ready { it.location.route == LibraryRoute.DETAIL && it.details != null }
            assertEquals("Film", page.location.label); assertFalse(page.progress.resumable)
            var plays = 0
            assertEquals(0, plays)
            vm.play(false) { _, items -> assertEquals("Film", items.single().name); plays++ }
            withTimeout(5000) { while (plays == 0) delay(5) }
            assertEquals(1, plays)
            assertTrue(vm.back()); page = ready { it.location.route == LibraryRoute.TITLES }
            assertEquals(8, page.bookmark.offset); assertEquals(account.key(LibraryKind.MOVIE, "1"), page.bookmark.focus)
            vm.root(); vm.openRoot(LibraryRoute.CATEGORIES, LibraryKind.SERIES)
            page = ready { it.location.route == LibraryRoute.CATEGORIES && it.rows.size == 2 }
            vm.open(page.rows.first()); page = ready { it.location.route == LibraryRoute.TITLES && it.rows.size == 1 }
            vm.open(page.rows.single()); ready { it.location.route == LibraryRoute.DETAIL && it.details != null }
            vm.seasons(); page = ready { it.location.route == LibraryRoute.SEASONS && it.rows.size == 2 }
            assertEquals("Specials", page.rows.first().season)
            vm.open(page.rows.first()); page = ready { it.location.route == LibraryRoute.EPISODES && it.rows.size == 1 }
            assertEquals("Episode", page.rows.single().label)
            vm.open(page.rows.single()); ready { it.location.entry?.id == "3" }
            assertEquals(1, plays)
            vm.mark(repo.resolve(account, page.rows.single().entry!!).channel, true)
            ready { it.location.entry?.id == "3" && it.nextEpisode?.id == "4" }
            assertEquals(1, plays)
            vm.nextEpisode(); ready { it.location.entry?.id == "4" }
            assertEquals(1, plays)
            assertTrue(vm.back()); ready { it.location.entry?.id == "3" }
            vm.fallback(); assertEquals(LibraryRoute.PLAYLIST, vm.state.value.location.route)
            assertTrue(vm.back()); assertEquals("3", vm.state.value.location.entry?.id)
            vm.root(); assertFalse(vm.back())
            vm.external(movie, true); assertTrue(vm.consumeExternalReturn()); assertFalse(vm.externalReturn)
            val channel = repo.resolve(account, movie).channel
            repo.record(account, channel, 40_000, 100_000, true)
            vm.external(movie, true); page = ready { it.location.entry?.id == "1" && it.progress.resumable }
            assertEquals(40_000, page.progress.position)
            vm.requestPlay { _, _ -> plays++ }
            withTimeout(5000) { vm.playChoice.first { it != null } }
            vm.cancelPlayChoice()
            assertEquals(1, plays)
            assertEquals(40_000, repo.progress(account, channel).position)
            vm.requestPlay { _, _ -> plays++ }
            withTimeout(5000) { vm.playChoice.first { it != null } }
            vm.play(true) { _, _ -> plays++ }
            withTimeout(5000) { while (plays != 2) delay(5) }
            assertEquals(0L, repo.progress(account, channel).position)
            vm.requestPlay { _, _ -> plays++ }
            withTimeout(5000) { while (plays != 3) delay(5) }
            assertNull(vm.playChoice.value)
            vm.mark(channel, true); ready { it.progress.watched }
            vm.mark(channel, false); ready { !it.progress.watched && it.progress.position == 0L }
            recents.addChannel(channel, account.key(LibraryKind.MOVIE, "1"), account.owner)
            recents.activate(credentials.serverUrl, credentials.username, emptyList())
            assertTrue(recents.recentChannels.value.isEmpty())
            vm.root(); vm.openRoot(LibraryRoute.RECENT)
            ready { it.location.route == LibraryRoute.RECENT && it.rows.any { row -> row.label == "Film" } }
            vm.root(); vm.external(series, true)
            ready { it.location.entry?.id == "2" && it.details != null }
            vm.seasons(); page = ready { it.location.route == LibraryRoute.SEASONS && it.rows.isNotEmpty() }
            vm.open(page.rows.first()); page = ready { it.location.route == LibraryRoute.EPISODES && it.rows.isNotEmpty() }
            vm.open(page.rows.first()); ready { it.location.entry?.id == "3" }
            assertFalse("An episode Back must unwind the series before returning to Search", vm.consumeExternalReturn())
            credentials.password = "replacement"
            vm.activate(true)
            assertEquals(LibraryRoute.ROOT, vm.state.value.location.route)
            assertFalse("Changed credentials must not leave an orphaned details Back route", vm.back())
        } finally { store.clear(); db.close(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll(); Dispatchers.resetMain() }
    }
    @Test fun globalLibraryDetailsReturnToOriginalSearchWithLegacyDedupAndAdultFilter() {
        val a = LibraryAccount.create("https://provider.example", "demo", "p")
        val entry = LibraryEntry(LibraryKind.MOVIE, "1", "Film")
        val legacy = com.example.iptv.data.model.M3UChannel("Film", "Films", null, a.stream(entry), "ua", null, null, true)
        val catalog = BrowserCatalog.create(listOf(legacy))
        val browser = BrowserController().apply { load(catalog, emptyList()); open(BrowserDestination.SEARCH); query("Film"); searchResults("Film", search("Film")) }
        val resolved = LibraryIdentity(a, catalog.channels).resolve(entry).copy(library = entry)
        browser.supplemental(listOf(resolved), listOf(resolved))
        assertEquals(1, browser.state.value.items.size); assertEquals(entry, browser.state.value.items.single().entry?.library)
        browser.focusGrid(resolved.id); browser.scroll(0, 7)
        browser.openLibraryDetails(); assertEquals(BrowserDestination.VOD, browser.state.value.location.destination)
        browser.closeLibraryDetails(); assertEquals(BrowserDestination.SEARCH, browser.state.value.location.destination)
        assertEquals(resolved.id, browser.state.value.bookmark.focusedId); assertEquals(7, browser.state.value.bookmark.scrollOffset)
        val hidden = resolved.copy(id = "hidden", channel = legacy.copy(groupTitle = "ADULT films"))
        browser.supplemental(emptyList(), listOf(hidden)); assertEquals(1, browser.state.value.items.size)
        browser.preferences(GridDensity.STANDARD, emptySet(), false); assertEquals(2, browser.state.value.items.size)
    }
}
