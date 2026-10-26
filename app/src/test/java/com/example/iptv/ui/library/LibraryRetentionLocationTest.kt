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
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class LibraryRetentionLocationTest {
    private lateinit var db: LibraryDatabase
    private lateinit var repo: LibraryRepository
    private lateinit var vm: LibraryViewModel
    private lateinit var prefs: BrowserPreferences
    private lateinit var account: LibraryAccount
    private val store = ViewModelStore()
    private val server = MockWebServer()
    private val client = OkHttpClient()
    private val show = LibraryEntry(LibraryKind.SERIES, "9", "A show", "s", "Series")
    private fun movie(id: Int) = LibraryEntry(LibraryKind.MOVIE, "$id", "Film %03d".format(id), "m", "Films", ordinal = id)
    private fun episode(id: Int) = LibraryEntry(LibraryKind.EPISODE, "${1000+id}", "Episode $id", "s", "Series", ordinal = id,
        seriesId = show.id, season = "2", episode = "$id")
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        app.noBackupFilesDir.resolve("library.db").delete(); server.start()
        val credentials = CredentialsManager(app).apply { save(server.url("/").toString(), "demo", "synthetic") }
        prefs = BrowserPreferences(app, credentials); db = LibraryDatabase(app); repo = LibraryRepository(db, credentials, client)
        account = repo.account(); repo.activate(account, BrowserCatalog.create(emptyList()))
        db.transaction { sql ->
            db.category(sql, account.cache, LibraryKind.MOVIE, LibraryCategory("m", "Films"), 0)
            db.category(sql, account.cache, LibraryKind.SERIES, LibraryCategory("s", "Series"), 0)
            (0..130).forEach { db.entry(sql, account.cache, movie(it)); db.entry(sql, account.cache, episode(it)) }
            db.entry(sql, account.cache, show)
            db.entry(sql, account.cache, movie(200).copy(category = "a"))
            db.category(sql, account.cache, LibraryKind.MOVIE, LibraryCategory("a", "ADULT films"), 1)
            for (kind in listOf(LibraryKind.MOVIE, LibraryKind.SERIES)) db.fresh(sql, account.cache, kind, System.currentTimeMillis())
            db.putDetails(sql, account, show, LibraryDetails("Series plot"), System.currentTimeMillis(), 500)
        }
        vm = LibraryViewModel(repo, RecentlyWatchedManager(app), prefs)
        store.put("library", vm); vm.activate(true)
    }
    @After fun close() {
        store.clear(); db.close(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown(); server.close(); Dispatchers.resetMain()
    }
    private suspend fun ready(predicate: (LibraryPage) -> Boolean) = withTimeout(5000) {
        vm.state.first { !it.loading && !it.preparing && predicate(it) }
    }
    private suspend fun jump(entry: LibraryEntry): Boolean {
        val done = CompletableDeferred<Boolean>()
        vm.browseLocation(entry, true) { done.complete(it) }
        return withTimeout(5000) { done.await() }
    }
    @Test fun exactMovieAndEpisodeJumpsLocateDistantPagesAndRetainDurableState() = runBlocking<Unit> {
        prefs.load()
        prefs.toggle(account.key(LibraryKind.MOVIE, "129"), account.owner)
        repo.record(account, repo.resolve(account, movie(129)).channel, 99_000, 100_000, true)
        db.saveSort(account.owner, LibraryKind.MOVIE, LibrarySort.NAME)
        assertTrue(jump(movie(129)))
        var page = ready { it.location.route == LibraryRoute.TITLES && it.page == 2 }
        assertEquals("m", page.location.category); assertFalse(page.unwatched)
        assertEquals(account.key(LibraryKind.MOVIE, "129"), page.bookmark.focus)
        assertTrue(page.rows.any { it.entry?.id == "129" })
        assertEquals(LibrarySort.NAME, db.sort(account.owner, LibraryKind.MOVIE))
        assertTrue(repo.progress(account, repo.resolve(account, movie(129)).channel).watched)
        assertTrue(account.key(LibraryKind.MOVIE, "129") in prefs.load().favorites)
        assertTrue(vm.consumeExternalReturn())
        assertTrue(jump(episode(127)))
        page = ready { it.location.route == LibraryRoute.EPISODES && it.page == 2 }
        assertEquals(show.id, page.location.entry?.id); assertEquals("2", page.location.season)
        assertEquals(account.key(LibraryKind.EPISODE, "1127"), page.bookmark.focus)
        assertTrue(page.rows.any { it.entry?.id == "1127" })
        val location = page.location
        assertFalse(jump(movie(200))); assertEquals(location, vm.state.value.location)
        assertFalse(jump(movie(999))); assertEquals(location, vm.state.value.location)
        assertEquals(0, server.requestCount)
    }
    @Test fun nestedPayloadsAreBoundedAndEvictedBookmarksRestoreFromDiskAcrossSections() = runBlocking<Unit> {
        vm.openRoot(LibraryRoute.CATEGORIES, LibraryKind.SERIES)
        var page = ready { it.rows.size == 2 }
        vm.focus("category:s"); vm.scroll(0, 11); vm.open(page.rows.last())
        page = ready { it.location.route == LibraryRoute.TITLES && it.rows.size == 1 }
        vm.open(page.rows.single()); ready { it.details != null }
        vm.seasons(); page = ready { it.location.route == LibraryRoute.SEASONS && it.rows.size == 1 }
        vm.open(page.rows.single()); page = ready { it.location.route == LibraryRoute.EPISODES && it.rows.size == 60 }
        vm.open(page.rows.first()); ready { it.location.route == LibraryRoute.DETAIL }
        assertTrue(vm.retainedParentPayloads <= 2)
        vm.openRoot(LibraryRoute.CATEGORIES, LibraryKind.MOVIE)
        ready { it.location.route == LibraryRoute.CATEGORIES && it.location.kind == LibraryKind.MOVIE }
        assertEquals("Opposite-section row/detail payloads must be released", 0, vm.retainedParentPayloads)
        assertTrue(vm.back()); ready { it.location.route == LibraryRoute.DETAIL }
        repeat(5) { assertTrue(vm.back()); ready { !it.preparing } }
        page = vm.state.value
        assertEquals(LibraryRoute.CATEGORIES, page.location.route)
        assertEquals("category:s", page.bookmark.focus); assertEquals(11, page.bookmark.offset)
        assertEquals(131, db.page(account, LibraryKind.MOVIE).size + db.page(account, LibraryKind.MOVIE, offset=60).size + db.page(account, LibraryKind.MOVIE, offset=120).size)
        assertEquals(0, server.requestCount)
    }
}
