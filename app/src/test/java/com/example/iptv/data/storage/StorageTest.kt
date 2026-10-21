package com.example.iptv.data.storage

import android.app.Application
import android.content.Context
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.util.GlobalExceptionHandler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class StorageTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
    private fun channel(url: String, name: String = "News") = M3UChannel(name, "Group", null, url, null, null, null, false)

    @Before fun reset() { prefs.edit().clear().commit() }

    @Test fun credentialsClearDoesNotEraseHistory() {
        prefs.edit().putString("recent_history_v1", "sentinel").commit()
        val credentials = CredentialsManager(context)
        credentials.save("https://provider.example", "demo", "secret")
        credentials.clear()
        assertFalse(prefs.contains("username"))
        assertFalse(prefs.contains("password"))
        assertFalse(prefs.contains("server_url"))
        assertEquals("sentinel", prefs.getString("recent_history_v1", null))
    }

    @Test fun historyIsScopedAndUsesLatestPlaylistMetadata() = runBlocking {
        val manager = RecentlyWatchedManager(context)
        val a = channel("https://provider.example/a")
        val b = channel("https://provider.example/b")
        manager.activate("https://provider.example", "account-a", listOf(a, b))
        manager.addChannel(a)
        manager.activate("https://provider.example", "account-b", listOf(a, b))
        assertTrue(manager.getRecentChannels().isEmpty())
        manager.addChannel(b)
        manager.activate("https://provider.example/", "account-a", listOf(a.copy(name = "Renamed")))
        assertEquals(listOf("Renamed"), manager.getRecentChannels().map { it.name })
        manager.activate("https://provider.example", "account-a", listOf(b))
        assertTrue(manager.getRecentChannels().isEmpty())
    }

    @Test fun futureHistoryIsNeverOverwritten() = runBlocking {
        val json = """{"version":99,"accounts":{}}"""
        prefs.edit().putString("recent_history_v1", json).commit()
        val manager = RecentlyWatchedManager(context)
        val a = channel("https://provider.example/a")
        manager.activate("https://provider.example", "demo", listOf(a))
        manager.addChannel(a)
        assertEquals(json, prefs.getString("recent_history_v1", null))
    }

    @Test fun legacyHistoryMigratesOnlyMatchingReferencesAndSurvivesRestart() = runBlocking {
        CredentialsManager(context).save("https://provider.example", "demo", "synthetic")
        prefs.edit().putString("recent_channels",
            """[{"streamUrl":"https://provider.example/a"},{"streamUrl":"other"},{"name":"missing"}]""").commit()
        val a = channel("https://provider.example/a", "Current name")
        val manager = RecentlyWatchedManager(context)
        manager.activate("https://provider.example", "demo", listOf(a))
        assertEquals(listOf(a), manager.getRecentChannels())
        val restarted = RecentlyWatchedManager(context)
        restarted.activate("https://provider.example", "demo", listOf(a))
        assertEquals(listOf(a), restarted.getRecentChannels())
    }

    @Test fun crashNoticeDelegatesWithoutSavingExceptionDetails() {
        var delegated = false
        GlobalExceptionHandler(context) { _, _ -> delegated = true }
            .uncaughtException(Thread.currentThread(), IllegalStateException("https://provider.example/private"))
        assertTrue(delegated)
        val notice = context.getSharedPreferences("crash_notice", Context.MODE_PRIVATE)
        assertEquals(mapOf("pending" to true), notice.all)
    }
}
