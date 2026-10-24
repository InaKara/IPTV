package com.example.iptv.data.library

import android.app.Application
import com.example.iptv.data.model.M3UChannel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class LibraryDatabaseTest {
    private lateinit var db: LibraryDatabase
    private val account = LibraryAccount.create("https://provider.example", "demo", "secret")
    @Before fun setup() { val app = RuntimeEnvironment.getApplication(); app.noBackupFilesDir.resolve("library.db").delete(); db = LibraryDatabase(app) }
    @After fun close() { db.close() }
    private fun channel(id: String, category: String = "Films") = M3UChannel("Title$id", category, null,
        account.stream(LibraryEntry(LibraryKind.MOVIE, id, "Title$id")), null, null, null, true)
    private fun populate(count: Int = 125) = db.transaction { sql ->
        db.category(sql, account.cache, LibraryKind.MOVIE, LibraryCategory("normal", "Films"), 0)
        db.category(sql, account.cache, LibraryKind.MOVIE, LibraryCategory("hidden", "Premium ADULT"), 1)
        repeat(count) { i -> db.entry(sql, account.cache, LibraryEntry(LibraryKind.MOVIE, "$i", "Title${i.toString().padStart(3, '0')}",
            if (i == 124) "hidden" else "normal", rating = if (i % 3 == 0) null else (i % 10).toDouble(), timestamp = i.toLong(), ordinal = i)) }
        db.fresh(sql, account.cache, LibraryKind.MOVIE, 123)
    }
    @Test fun pagesStableSortUnknownRatingsAndAdultVisibilityDoNotChangeStoredRows() {
        populate()
        val page = db.page(account, LibraryKind.MOVIE)
        assertEquals(60, page.size); assertEquals("0", page.first().id)
        assertEquals("60", db.page(account, LibraryKind.MOVIE, offset = 60).first().id)
        assertEquals(4, db.page(account, LibraryKind.MOVIE, offset = 120).size)
        assertEquals(5, db.page(account, LibraryKind.MOVIE, filter = false, offset = 120).size)
        assertEquals(listOf("Films"), db.categories(account.cache, LibraryKind.MOVIE, true).map { it.name })
        val rating = db.page(account, LibraryKind.MOVIE, sort = LibrarySort.RATING)
        assertEquals("19", rating.first().id); assertTrue(rating.first().rating!! > 0)
        assertEquals("123", db.page(account, LibraryKind.MOVIE, sort = LibrarySort.RECENT).first().id)
        assertEquals("0", db.page(account, LibraryKind.MOVIE, sort = LibrarySort.NAME).first().id)
        db.saveSort(account.owner, LibraryKind.MOVIE, LibrarySort.RECENT)
        assertEquals(LibrarySort.RECENT, db.sort(account.owner, LibraryKind.MOVIE))
        assertEquals(LibrarySort.PROVIDER, db.sort(account.owner, LibraryKind.SERIES))
        db.transaction { sql ->
            repeat(63) { i -> db.category(sql, account.cache, LibraryKind.MOVIE, LibraryCategory("cat$i", "Category$i"), i + 2) }
            repeat(62) { i -> db.entry(sql, account.cache, LibraryEntry(LibraryKind.EPISODE, "${9000+i}", "Episode", ordinal = i, seriesId = "show", season = "$i")) }
        }
        assertEquals(60, db.categories(account.cache, LibraryKind.MOVIE, true).size)
        assertEquals(4, db.categories(account.cache, LibraryKind.MOVIE, true, 60).size)
        assertEquals(60, db.seasons(account, "show").size); assertEquals(listOf("60", "61"), db.seasons(account, "show", 60))
    }
    @Test fun invalidReplacementAndStaleAccountLeaveWorkingIndexAndAgeUnchanged() {
        populate(3)
        assertThrows(IllegalStateException::class.java) { db.transaction({ false }) { sql ->
            db.clearListing(sql, account.cache, LibraryKind.MOVIE); db.fresh(sql, account.cache, LibraryKind.MOVIE, 999)
        } }
        assertEquals(123L, db.age(account.cache, LibraryKind.MOVIE)); assertEquals(3, db.page(account).size)
        assertThrows(Exception::class.java) { db.transaction { sql ->
            db.clearListing(sql, account.cache, LibraryKind.MOVIE)
            LibraryParser.listing("""[{"stream_id":10,"name":"partial"},bad]""".byteInputStream(), LibraryKind.MOVIE) { db.entry(sql, account.cache, it) }
        } }
        assertEquals(listOf("0", "1", "2"), db.page(account).map { it.id })
    }
    @Test fun evictionRemovesSelectedSeriesCacheButNeverProgressAndListings() {
        populate(2)
        val series = LibraryEntry(LibraryKind.SERIES, "10", "Show")
        val movie = LibraryEntry(LibraryKind.MOVIE, "0", "Film")
        val key = account.key(LibraryKind.EPISODE, "77")
        db.saveProgress(account.owner, key, channel("77"), "10", WatchProgress(40_000, 100_000, false, true, 3))
        db.transaction { sql ->
            db.entry(sql, account.cache, series)
            db.entry(sql, account.cache, LibraryEntry(LibraryKind.EPISODE, "77", "Episode", seriesId = "10", season = "1"))
            db.putDetails(sql, account, series, LibraryDetails("Old series"), 1, 1000)
            db.putDetails(sql, account, movie, LibraryDetails("Opened film"), 2, 1000)
            db.evict(sql, account.cache, movie.kind, movie.id, 1500)
        }
        assertNull(db.details(account, series, 4)); assertNotNull(db.details(account, movie, 4))
        assertNull(db.find(account, LibraryKind.EPISODE, "77")); assertNotNull(db.find(account, LibraryKind.SERIES, "10"))
        assertEquals(40_000, db.progress(account.owner, key).position)
        assertEquals(2, db.page(account, LibraryKind.MOVIE).size)
    }
    @Test fun continueWatchingGroupsEpisodesRetainsOrphansAndSurvivesPasswordRotation() {
        val first = account.key(LibraryKind.EPISODE, "11"); val second = account.key(LibraryKind.EPISODE, "12")
        db.saveProgress(account.owner, first, channel("11"), "series1", WatchProgress(30_000, 100_000, false, true, 1))
        db.saveProgress(account.owner, second, channel("12"), "series1", WatchProgress(50_000, 100_000, false, true, 2))
        db.saveProgress(account.owner, "brief", channel("13"), "", WatchProgress(29_999, 100_000, false, true, 3))
        db.saveProgress(account.owner, "adult", channel("14", "ADULT films"), "", WatchProgress(40_000, 100_000, false, true, 4))
        db.saveProgress(account.owner, "watched", channel("15"), "", WatchProgress(95_000, 100_000, true, true, 5))
        assertEquals(listOf(second), db.continuing(account.owner, true, 0).map { it.first })
        assertEquals(listOf("adult", second), db.continuing(account.owner, false, 0).map { it.first })
        val rotated = LibraryAccount.create("https://provider.example", "demo", "changed")
        assertEquals(50_000, db.progress(rotated.owner, second).position)
        assertTrue(db.continuing("another-account", false, 0).isEmpty())
        db.saveProgress(account.owner, second, channel("12"), "series1", WatchProgress())
        assertEquals(listOf(first), db.continuing(account.owner, true, 0).map { it.first })
    }
}
