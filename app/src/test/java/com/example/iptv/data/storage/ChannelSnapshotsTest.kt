package com.example.iptv.data.storage

import android.app.Application
import android.util.AtomicFile
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.model.ChannelTextSharing
import com.example.iptv.ui.browser.BrowserCatalog
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class ChannelSnapshotsTest {
    private val account = ChannelAccount("https://provider.example/", "demo", "synthetic")
    private val channel = M3UChannel("News, HD", "Live", "https://logo.example/local.svg",
        "https://provider.example/stream?token=synthetic", "Agent", "id", "Guide", false, "007")

    @Test fun snapshotRoundTripsMetadataInAccountScopedNoBackupStorage() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = FileChannelSnapshots(context)
        val snapshot = ChannelSnapshot(listOf(channel,
            channel.copy(name = "Ungrouped", groupTitle = "", streamUrl = "https://provider.example/blank"),
            channel.copy(name = "Whitespace group", groupTitle = "   ", streamUrl = "https://provider.example/spaces")), 1000)
        store.write(account, snapshot)
        assertEquals(snapshot, store.read(account))
        assertEquals(File(context.noBackupFilesDir, "playlists"), store.directory)
        assertNull(store.read(account.copy(password = "another")))
        assertNull(store.read(account.copy(username = "another")))
        assertEquals(account.key, account.copy(server = "https://provider.example").key)
        assertFalse(account.toString().contains("synthetic"))
        assertFalse(File(store.directory, account.key).name.contains("demo"))
    }

    @Test fun corruptFutureWrongAccountAndIncompleteSnapshotsAreCacheMisses() = runBlocking {
        val store = FileChannelSnapshots(RuntimeEnvironment.getApplication())
        store.directory.mkdirs()
        val file = File(store.directory, account.key)
        store.write(account, ChannelSnapshot(listOf(channel), 1000))
        val complete = file.readText()
        val invalid = listOf("{", "null", "{}",
            complete.replace("\"version\":1", "\"version\":2"),
            complete.replace(account.key, "wrong-account"),
            complete.replace("\"name\":\"News, HD\"", "\"name\":\"\""),
            complete.replace("\"groupTitle\":\"Live\"", "\"groupTitle\":null"),
            complete.replace("\"groupTitle\":\"Live\",", ""),
            """{"version":1,"account":"${account.key}","channels":[null]}""",
            """{"version":1,"account":"${account.key}","channels":[{"name":"Name"}]}""")
        invalid.forEach { file.writeText(it); assertNull(store.read(account)) }
    }

    @Test fun unfinishedOrRejectedReplacementPreservesLastCompleteSnapshot() = runBlocking {
        val store = FileChannelSnapshots(RuntimeEnvironment.getApplication())
        val good = ChannelSnapshot(listOf(channel), 1000)
        store.write(account, good)
        val atomic = AtomicFile(File(store.directory, account.key))
        val unfinished = atomic.startWrite()
        unfinished.write("incomplete".toByteArray())
        unfinished.close() // Simulate interruption before finishWrite.
        assertEquals(good, store.read(account))
        try { store.write(account, ChannelSnapshot(emptyList(), 2000)); fail("empty replacement accepted") }
        catch (_: IllegalArgumentException) { }
        try { store.write(account, ChannelSnapshot(listOf(channel.copy(streamUrl = "")), 2000)); fail("invalid replacement accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(good, store.read(account))
    }

    @Test fun refreshAgeUsesExactBoundaryAndTreatsInvalidClockValuesAsStale() {
        val snapshot = ChannelSnapshot(listOf(channel), 1000)
        assertFalse(snapshot.stale(1000 + ChannelSnapshot.MAX_AGE_MS))
        assertTrue(snapshot.stale(1001 + ChannelSnapshot.MAX_AGE_MS))
        assertTrue(snapshot.stale(999))
        assertTrue(snapshot.copy(downloadedAt = 0).stale(1000))
    }

    @Test fun snapshotSharingPreservesSchemaValuesAndCanonicalOccurrences() = runBlocking {
        val store = FileChannelSnapshots(RuntimeEnvironment.getApplication())
        val base = channel.copy(groupTitle = " TR ", userAgent = " Agent ", tvgName = channel.name)
        val rows = listOf(base, base.copy(), base.copy(groupTitle = "tr", userAgent = "agent", tvgName = "news, HD"),
            base.copy(groupTitle = "", userAgent = "", tvgName = "", logoUrl = "", tvgId = "", providerNumber = ""),
            base.copy(groupTitle = "   ", userAgent = null, tvgName = null, logoUrl = null, tvgId = null, providerNumber = null),
            base.copy(name = "Film Ω", groupTitle = "VOD", tvgName = "Film Ω", streamUrl = "https://provider.example/movie/1.mp4", isVod = true))
        val snapshot = ChannelSnapshot(rows, 1234567)
        store.write(account, snapshot)
        val file = File(store.directory, account.key)
        val gson = Gson()
        val json = gson.fromJson(file.readText(), JsonObject::class.java)
        assertEquals(1, json["version"].asInt)
        assertEquals(account.key, json["account"].asString)
        assertEquals(gson.toJsonTree(rows), json["channels"])
        // Preserve Gson's handling of explicit nulls and unknown future metadata.
        json.addProperty("unknown", "ignored")
        json["channels"].asJsonArray[4].asJsonObject.add("userAgent", com.google.gson.JsonNull.INSTANCE)
        json["channels"].asJsonArray[0].asJsonObject.addProperty("unknown", true)
        file.writeText(gson.toJson(json))
        val originalBytes = file.readBytes()
        val actual = requireNotNull(store.read(account))
        assertEquals(snapshot, actual)
        assertArrayEquals(originalBytes, file.readBytes())
        assertSame(actual.channels[0].groupTitle, actual.channels[1].groupTitle)
        assertSame(actual.channels[0].userAgent, actual.channels[1].userAgent)
        actual.channels.filter { it.name == it.tvgName }.forEach { assertSame(it.name, it.tvgName) }
        val expectedIds = BrowserCatalog.create(rows).channels.map { it.id }
        assertEquals(expectedIds, BrowserCatalog.create(actual.channels).channels.map { it.id })
        assertNotEquals(expectedIds[0], expectedIds[1])
        store.write(account, actual)
        assertEquals(snapshot, store.read(account))
    }

    @Test fun snapshotPoolsAreBoundedAndIsolatedBetweenAccountsAndReads() = runBlocking {
        val store = FileChannelSnapshots(RuntimeEnvironment.getApplication())
        val rows = (0..ChannelTextSharing.GROUP_LIMIT).map { index ->
            channel.copy(name = "Name $index", tvgName = "Name $index", groupTitle = "Group $index", userAgent = "Agent $index")
        }.let { it + it.first() + it.last() + it[ChannelTextSharing.AGENT_LIMIT] }
        val snapshot = ChannelSnapshot(rows, 1000)
        val otherAccount = account.copy(username = "another")
        store.write(account, snapshot)
        store.write(otherAccount, snapshot)
        val first = requireNotNull(store.read(account)).channels
        assertEquals(rows, first)
        assertSame(first[0].groupTitle, first[first.lastIndex - 2].groupTitle)
        assertSame(first[0].userAgent, first[first.lastIndex - 2].userAgent)
        assertNotSame(first[ChannelTextSharing.GROUP_LIMIT].groupTitle, first[first.lastIndex - 1].groupTitle)
        assertNotSame(first[ChannelTextSharing.AGENT_LIMIT].userAgent, first.last().userAgent)
        assertSame(first[ChannelTextSharing.AGENT_LIMIT].groupTitle, first.last().groupTitle)
        listOf(account, otherAccount).forEach { current ->
            val next = requireNotNull(store.read(current)).channels
            assertEquals(rows, next)
            assertNotSame(first[0].groupTitle, next[0].groupTitle)
            assertNotSame(first[0].userAgent, next[0].userAgent)
            assertSame(next[0].groupTitle, next[next.lastIndex - 2].groupTitle)
        }
    }
}
