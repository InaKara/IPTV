package com.example.iptv.data.library

import android.app.Application
import android.content.Context
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class SeasonFavoriteTest {
    @Test fun upgradeAndSeasonReferencesPreserveEpisodeStateAcrossEvictionPasswordAndRemoval() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        app.noBackupFilesDir.resolve("library.db").delete()
        val credentials = CredentialsManager(app).apply { save("https://provider.example", "demo", "synthetic") }
        val client = OkHttpClient()
        var db = LibraryDatabase(app)
        val account = LibraryAccount.create(credentials.serverUrl, credentials.username, credentials.password)
        val movieKey = account.key(LibraryKind.EPISODE, "7")
        val episode = M3UChannel("Episode", "Drama", null, account.stream(LibraryEntry(LibraryKind.EPISODE, "7", "Episode")), null, null, null, true)
        db.saveProgress(account.owner, movieKey, episode, "7", WatchProgress(40_000, 100_000, false, true, 1))
        db.writableDatabase.execSQL("DROP TABLE season_favorites")
        db.writableDatabase.execSQL("DROP TABLE details")
        db.writableDatabase.execSQL("CREATE TABLE details(cache TEXT,kind TEXT,id TEXT,plot TEXT,year TEXT,runtime TEXT,genre TEXT,cast TEXT,director TEXT,time INTEGER,used INTEGER,bytes INTEGER,PRIMARY KEY(cache,kind,id))")
        db.writableDatabase.version = 1; db.close()
        db = LibraryDatabase(app)
        val repo = LibraryRepository(db, credentials, client)
        val active = repo.account()
        val season = SeasonFavorite("7", "Specials / 零", "A series", "2", "Drama")
        val prefs = BrowserPreferences(app, credentials)
        try {
            assertEquals(3, db.readableDatabase.version)
            assertEquals(40_000, db.progress(active.owner, movieKey).position)
            assertNotEquals(movieKey, season.key(active))
            prefs.load(); prefs.toggle(movieKey)
            repo.saveSeason(active, season); prefs.toggle(season.key(active))
            assertEquals(season, db.season(active, season.key(active)))
            db.transaction { sql ->
                db.entry(sql, active.cache, LibraryEntry(LibraryKind.SERIES, "7", "A series"))
                db.putDetails(sql, active, season.series(), LibraryDetails("Temporary"), 1, 1000)
                db.evict(sql, "other", LibraryKind.MOVIE, "different", 0)
            }
            assertNull(db.details(active, season.series(), 2))
            assertEquals(season, db.season(active, season.key(active)))
            credentials.password = "replacement"
            val rotated = repo.account()
            val reference = repo.favoriteReferences(rotated, prefs.load().favorites)[season.key(rotated)]!!
            assertEquals(season, reference.season); assertEquals("", reference.channel.streamUrl)
            assertEquals(40_000, db.progress(rotated.owner, movieKey).position)
            prefs.toggle(season.key(rotated), rotated.owner)
            assertTrue(movieKey in prefs.load().favorites)
            assertEquals(40_000, db.progress(rotated.owner, movieKey).position)
            assertNotNull(db.season(rotated, season.key(rotated)))
            credentials.username = "other"
            assertNull(db.season(repo.account(), season.key(active)))
        } finally { db.close(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    }
}
