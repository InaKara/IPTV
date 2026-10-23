package com.example.iptv.data.storage

import android.app.Application
import android.util.AtomicFile
import com.example.iptv.data.model.M3UChannel
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
}
