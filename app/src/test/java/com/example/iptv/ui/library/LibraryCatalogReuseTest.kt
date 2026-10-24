package com.example.iptv.ui.library

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.example.iptv.data.library.*
import com.example.iptv.data.model.M3UChannel
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
class LibraryCatalogReuseTest {
    @Test fun fullCatalogIsReusedAcrossCategoriesPagesSortFilterAndBackUntilExpired() = runBlocking<Unit> {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        app.noBackupFilesDir.resolve("library.db").delete()
        val server = MockWebServer().apply { start() }
        val credentials = CredentialsManager(app).apply { save(server.url("/").toString(), "demo", "synthetic") }
        val db = LibraryDatabase(app); val client = OkHttpClient(); val repo = LibraryRepository(db, credentials, client)
        val account = repo.account()
        repo.activate(account, BrowserCatalog.create((1..150).map {
            M3UChannel("Film $it", "Films", null, account.stream(LibraryEntry(LibraryKind.MOVIE, "$it", "Film $it")), null, null, null, true)
        }))
        db.writableDatabase.execSQL("CREATE TABLE alias_writes(value INTEGER)")
        db.writableDatabase.execSQL("INSERT INTO alias_writes VALUES(0)")
        db.writableDatabase.execSQL("CREATE TRIGGER count_alias_writes AFTER INSERT ON aliases BEGIN UPDATE alias_writes SET value=value+1; END")
        fun writes() = db.readableDatabase.rawQuery("SELECT value FROM alias_writes", null).use { it.moveToFirst(); it.getInt(0) }
        val vm = LibraryViewModel(repo, RecentlyWatchedManager(app), BrowserPreferences(app, credentials))
        val store = ViewModelStore().apply { put("library", vm) }
        val categories = """[{"category_id":"1","category_name":"First"},{"category_id":"2","category_name":"Second"}]"""
        val movies = (1..150).joinToString(",", "[", "]") { """{"stream_id":"$it","name":"Film $it","category_id":"${if (it <= 75) 1 else 2}"}""" }
        suspend fun ready(predicate: (LibraryPage) -> Boolean) = withTimeout(5000) {
            vm.state.first { !it.loading && !it.preparing && predicate(it) }
        }
        try {
            vm.activate(true); assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setBody(categories)); server.enqueue(MockResponse().setBody(movies))
            vm.openRoot(LibraryRoute.CATEGORIES)
            var page = ready { it.rows.size == 3 }
            assertEquals(2, server.requestCount)
            repeat(2) { assertNull(server.takeRequest().requestUrl!!.queryParameter("category_id")) }
            assertEquals(150, db.page(account, LibraryKind.MOVIE, limit = 60).size + db.page(account, LibraryKind.MOVIE, offset = 60).size + db.page(account, LibraryKind.MOVIE, offset = 120).size)
            assertTrue(repo.listingKnownFresh(account, LibraryKind.MOVIE))
            vm.open(page.rows[1]); assertFalse(vm.state.value.loading)
            page = ready { it.location.route == LibraryRoute.TITLES && it.rows.size == 60 }
            assertTrue(page.more)
            vm.page(1); assertFalse(vm.state.value.loading); ready { it.page == 1 && it.rows.size == 15 }
            assertEquals(75, writes())
            vm.sort(); assertFalse(vm.state.value.loading); ready { it.page == 0 && it.sort == LibrarySort.NAME }
            vm.unwatched(); assertFalse(vm.state.value.loading); ready { it.unwatched && it.rows.size == 60 }
            assertEquals("Already prepared pages must not rewrite their aliases", 75, writes())
            assertTrue(vm.back()); page = ready { it.location.route == LibraryRoute.CATEGORIES }
            vm.open(page.rows[2]); assertFalse(vm.state.value.loading); ready { it.rows.size == 60 }
            assertEquals(2, server.requestCount)
            vm.root(); vm.openRoot(LibraryRoute.CATEGORIES); ready { it.rows.size == 3 }
            assertEquals(2, server.requestCount)
            // Expiry triggers one real refresh, not a request per category/page.
            val old = System.currentTimeMillis() - LibraryRepository.LIST_AGE - 1
            db.transaction { db.fresh(it, account.cache, LibraryKind.MOVIE, old) }
            // A new repository represents process restart: freshness comes from disk, not a permanent hint.
            val restarted = LibraryRepository(db, credentials, client)
            server.enqueue(MockResponse().setBody(categories)); server.enqueue(MockResponse().setBody(movies))
            assertTrue(restarted.refresh(restarted.account(), LibraryKind.MOVIE)); assertEquals(4, server.requestCount)
            credentials.password = "rotated"
            val rotated = repo.account()
            assertFalse(repo.listingKnownFresh(rotated, LibraryKind.MOVIE))
            assertFalse(repo.valid(account))
        } finally {
            store.clear(); db.close(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown(); server.close(); Dispatchers.resetMain()
        }
    }
}
