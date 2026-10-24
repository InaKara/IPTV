package com.example.iptv.data.library

import android.app.Application
import android.content.Context
import com.example.iptv.data.storage.CredentialsManager
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class LibraryRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var db: LibraryDatabase
    private lateinit var credentials: CredentialsManager
    private lateinit var repo: LibraryRepository
    private lateinit var client: OkHttpClient
    @Before fun setup() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        app.noBackupFilesDir.resolve("library.db").delete()
        server = MockWebServer().apply { start() }
        credentials = CredentialsManager(app).apply { save(server.url("/").toString(), "demo +&", "secret/?&") }
        db = LibraryDatabase(app); client = OkHttpClient.Builder().followSslRedirects(false).build()
        repo = LibraryRepository(db, credentials, client)
    }
    @After fun close() { db.close(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown(); server.close() }
    private fun listing() {
        server.enqueue(MockResponse().setBody("""[{"category_id":"1","category_name":"Movies"}]"""))
        server.enqueue(MockResponse().setBody("""[{"stream_id":42,"name":"Film","category_id":"1","rating":7}]"""))
    }
    @Test fun lazySingleFlightAgeAndInvalidRefreshRetainCompleteCache() = runBlocking {
        assertEquals(0, server.requestCount)
        val account = repo.account()
        listing()
        val result = coroutineScope { listOf(async { repo.refresh(account, LibraryKind.MOVIE) }, async { repo.refresh(account, LibraryKind.MOVIE) }).awaitAll() }
        assertEquals(listOf(true, true), result); assertEquals(2, server.requestCount)
        val request = server.takeRequest().requestUrl!!
        assertEquals("demo +&", request.queryParameter("username")); assertEquals("secret/?&", request.queryParameter("password"))
        assertEquals("get_vod_categories", request.queryParameter("action")); server.takeRequest()
        assertTrue(repo.refresh(account, LibraryKind.MOVIE)); assertEquals(2, server.requestCount)
        val age = db.age(account.cache, LibraryKind.MOVIE)
        server.enqueue(MockResponse().setBody("""[{"category_id":"2","category_name":"Replacement"}]"""))
        server.enqueue(MockResponse().setBody("""[{"stream_id":9,"name":"Partial"},invalid]"""))
        assertFalse(repo.refresh(account, LibraryKind.MOVIE, true))
        assertEquals(age, db.age(account.cache, LibraryKind.MOVIE))
        assertEquals(listOf("Film"), db.page(account).map { it.name })
        assertEquals("Movies", db.categories(account.cache, LibraryKind.MOVIE, false).single().name)
    }
    @Test fun selectedSeriesLoadsEpisodesOnceAndOfflineFailureKeepsDetailAge() = runBlocking {
        val account = repo.account()
        val series = LibraryEntry(LibraryKind.SERIES, "10", "Show", "cat")
        server.enqueue(MockResponse().setBody("""{"info":{"plot":"Story"},"episodes":{"1":[{"id":11,"title":"Episode"}]}}"""))
        assertTrue(repo.details(account, series)); assertEquals(1, server.requestCount)
        assertEquals("get_series_info", server.takeRequest().requestUrl!!.queryParameter("action"))
        val stored = db.details(account, series, System.currentTimeMillis())!!
        assertEquals(listOf("1"), db.seasons(account, "10"))
        assertEquals("Episode", db.page(account, LibraryKind.EPISODE, series = "10").single().name)
        assertTrue(repo.details(account, series)); assertEquals(1, server.requestCount)
        server.enqueue(MockResponse().setResponseCode(503))
        assertFalse(repo.details(account, series, true))
        assertEquals(stored, db.details(account, series, System.currentTimeMillis()))
        assertEquals(1, db.page(account, LibraryKind.EPISODE, series = "10").size)
    }
    @Test fun cancellationAndAccountChangeRejectLatePartialReplacement() = runBlocking {
        val account = repo.account()
        listing(); assertTrue(repo.refresh(account, LibraryKind.MOVIE))
        server.takeRequest(); server.takeRequest()
        val age = db.age(account.cache, LibraryKind.MOVIE)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val task = launch(Dispatchers.Default) { repo.refresh(account, LibraryKind.MOVIE, true) }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(3, TimeUnit.SECONDS) })
        withTimeout(2000) { task.cancelAndJoin() }
        assertEquals(age, db.age(account.cache, LibraryKind.MOVIE)); assertEquals(1, db.page(account).size)
        server.enqueue(MockResponse().setBody("[]").setBodyDelay(200, TimeUnit.MILLISECONDS))
        server.enqueue(MockResponse().setBody("[]"))
        val late = async(Dispatchers.Default) { repo.refresh(account, LibraryKind.MOVIE, true) }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(3, TimeUnit.SECONDS) })
        credentials.username = "other"
        assertFalse(late.await())
        assertEquals(age, db.age(account.cache, LibraryKind.MOVIE)); assertEquals(1, db.page(account).size)
        assertTrue(db.page(repo.account()).isEmpty())
    }
    @Test fun typedFavoritesRecentsAndProgressResolveWithoutBulkEpisodeDownloads() = runBlocking {
        val account = repo.account()
        listing(); assertTrue(repo.refresh(account, LibraryKind.MOVIE))
        val entry = db.find(account, LibraryKind.MOVIE, "42")!!
        val resolved = repo.resolve(account, entry)
        val prefs = com.example.iptv.data.storage.BrowserPreferences(RuntimeEnvironment.getApplication(), credentials)
        prefs.load(); prefs.toggle(resolved.id)
        val recents = com.example.iptv.data.storage.RecentlyWatchedManager(RuntimeEnvironment.getApplication())
        recents.activate(credentials.serverUrl, credentials.username, emptyList())
        recents.addChannel(resolved.channel, resolved.id, account.owner)
        assertEquals(listOf(resolved.channel), recents.recentChannels.value)
        assertEquals(1, repo.supplemental(account, prefs.load().favorites).size)
        assertEquals(1, repo.supplemental(account, emptySet(), "Film").size)
        repo.record(account, resolved.channel, 40_000, 100_000, true)
        assertEquals(40_000, repo.progress(account, resolved.channel).position)
        repo.record(account, resolved.channel.copy(isVod = false), 95_000, 100_000, true)
        assertFalse(repo.progress(account, resolved.channel).watched)
        val count = server.requestCount
        credentials.password = "replacement"
        val rotated = repo.account()
        assertEquals(account.owner, rotated.owner)
        assertEquals(resolved.id, repo.key(rotated, resolved.channel.copy(streamUrl = rotated.stream(entry))))
        assertTrue(repo.progress(rotated, resolved.channel.copy(streamUrl = rotated.stream(entry))).resumable)
        assertEquals(count, server.requestCount)
    }
    @Test fun oldSeriesRevisionRefreshesOnlySelectedSeriesAndRetainsDurableStateOnFailure() = runBlocking {
        val account = repo.account()
        val show = LibraryEntry(LibraryKind.SERIES, "10", "Show", "cat")
        val other = show.copy(id = "20", name = "Other")
        val episode = LibraryEntry(LibraryKind.EPISODE, "900", "Old collapsed row", "cat", seriesId = "10", season = "2")
        val channel = repo.resolve(account, episode)
        val prefs = com.example.iptv.data.storage.BrowserPreferences(RuntimeEnvironment.getApplication(), credentials)
        prefs.load(); prefs.toggle(channel.id)
        repo.record(account, channel.channel, 40_000, 100_000, true, legacyKey = channel.id)
        db.transaction { sql ->
            db.entry(sql, account.cache, episode)
            db.entry(sql, account.cache, episode.copy(id = "901", seriesId = "20"))
            db.putDetails(sql, account, show, LibraryDetails("Old"), System.currentTimeMillis(), 200)
            db.putDetails(sql, account, other, LibraryDetails("Other"), System.currentTimeMillis(), 200)
            // Recreate the deployed v2 schema, preserving its actual rows and durable tables.
            sql.execSQL("ALTER TABLE details RENAME TO details_current")
            sql.execSQL("CREATE TABLE details(cache TEXT,kind TEXT,id TEXT,plot TEXT,year TEXT,runtime TEXT,genre TEXT,cast TEXT,director TEXT,time INTEGER,used INTEGER,bytes INTEGER,PRIMARY KEY(cache,kind,id))")
            sql.execSQL("INSERT INTO details SELECT cache,kind,id,plot,year,runtime,genre,\"cast\",director,time,used,bytes FROM details_current")
            sql.execSQL("DROP TABLE details_current")
        }
        db.writableDatabase.version = 2; db.close()
        db = LibraryDatabase(RuntimeEnvironment.getApplication()); repo = LibraryRepository(db, credentials, client)
        val active = repo.account()
        assertEquals(3, db.readableDatabase.version); assertFalse(db.seriesParserCurrent(active, show))
        assertEquals(0, server.requestCount)
        server.enqueue(MockResponse().setResponseCode(503))
        assertFalse(repo.details(active, show))
        assertEquals(listOf("900"), db.page(active, LibraryKind.EPISODE, series = "10").map { it.id })
        assertEquals("Old", db.details(active, show, 0)!!.first.synopsis)
        assertFalse(db.seriesParserCurrent(active, show))
        server.enqueue(MockResponse().setBody("""{"info":{"plot":"Repaired"},"episodes":{"1":[{"id":"101","title":"One","info":{"id":"900"}},{"id":"102","info":{"id":"900"}}],"2":[{"id":"201","info":{"id":"900"}}]}}"""))
        assertTrue(repo.details(active, show)); assertTrue(db.seriesParserCurrent(active, show))
        assertEquals(listOf("101", "102", "201"), db.page(active, LibraryKind.EPISODE, series = "10").map { it.id })
        assertEquals(listOf("1", "2"), db.seasons(active, "10"))
        assertEquals(listOf("901"), db.page(active, LibraryKind.EPISODE, series = "20").map { it.id })
        assertFalse(db.seriesParserCurrent(active, other))
        assertEquals(40_000, repo.progress(active, channel.channel, channel.id).position)
        assertTrue(channel.id in prefs.load().favorites)
        assertTrue(repo.details(active, show)); assertEquals(2, server.requestCount)
        repeat(2) { assertEquals("10", server.takeRequest().requestUrl!!.queryParameter("series_id")) }
    }
    @Test fun favoritesAndEpisodeProgressRemainResolvableAfterEvictionAndPasswordRotation() = runBlocking {
        val account = repo.account()
        val episode = LibraryEntry(LibraryKind.EPISODE, "99", "Episode", categoryName = "Drama", seriesId = "10")
        val original = repo.resolve(account, episode)
        val legacy = original.channel.copy(userAgent = "preserved")
        val unused = legacy.copy(streamUrl = account.stream(episode.copy(id = "100")))
        val catalog = com.example.iptv.ui.browser.BrowserCatalog.create(listOf(legacy, unused))
        repo.activate(account, catalog)
        val preserved = catalog.channels.first().id
        repo.local { repo.ensureAliases(account, setOf(preserved)) }
        assertNotNull(db.alias(account.owner, preserved))
        assertNull("Unused playlist items must not trigger bulk alias writes", db.alias(account.owner, catalog.channels[1].id))
        repo.retainPlayable(account, catalog.channels.first())
        assertNotNull(repo.favoriteReferences(account, setOf(preserved))[preserved])
        repo.record(account, legacy, 40_000, 100_000, true)
        credentials.password = "rotated"
        val rotated = repo.account()
        val updatedLegacy = legacy.copy(streamUrl = rotated.stream(episode))
        repo.activate(rotated, com.example.iptv.ui.browser.BrowserCatalog.create(listOf(updatedLegacy)))
        val favorite = repo.favoriteReferences(rotated, setOf(preserved)).values.single()
        assertEquals(updatedLegacy.streamUrl, favorite.channel.streamUrl)
        assertEquals("preserved", favorite.channel.userAgent)
        assertTrue(repo.progress(rotated, favorite.channel).resumable)
        assertFalse(repo.valid(account))
        val identity = com.example.iptv.ui.browser.BrowserCatalog.create(listOf(updatedLegacy, updatedLegacy)).channels
        repo.activate(rotated, com.example.iptv.ui.browser.BrowserCatalog.create(listOf(updatedLegacy, updatedLegacy)))
        repo.record(rotated, updatedLegacy, 40_000, 100_000, true, legacyKey = identity[1].id)
        assertFalse(repo.progress(rotated, updatedLegacy, identity[0].id).resumable)
        assertTrue(repo.progress(rotated, updatedLegacy, identity[1].id).resumable)
        assertEquals(0, server.requestCount)
    }
}
