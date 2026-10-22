package com.example.iptv.data.storage

import android.app.Application
import android.content.Context
import com.example.iptv.ui.browser.GridDensity
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
class BrowserPreferencesTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val prefs get() = app.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
    @Before fun clear() { prefs.edit().clear().commit() }

    @Test fun favoritesAndDensitySurviveRestartAndAccountsStaySeparate() = runBlocking {
        val credentials = CredentialsManager(app).apply { save("https://provider.example", "one", "synthetic") }
        var store = BrowserPreferences(app, credentials)
        assertEquals(GridDensity.STANDARD, store.load().density)
        store.toggle("channel-a")
        store.toggle("channel-b")
        store.toggle("channel-a")
        store.density(GridDensity.COMPACT)
        store = BrowserPreferences(app, credentials)
        val saved = store.load()
        assertEquals(setOf("channel-b"), saved.favorites)
        assertEquals(GridDensity.COMPACT, saved.density)
        credentials.save("https://provider.example/", "two", "synthetic")
        assertTrue(store.load().favorites.isEmpty())
        store.toggle("other")
        credentials.save("https://provider.example/", "one", "changed-password")
        assertEquals(setOf("channel-b"), store.load().favorites)
        assertEquals(GridDensity.COMPACT, store.load().density)
        val raw = prefs.getString("favorites_v1", "")!!
        assertFalse(raw.contains("provider.example"))
        assertFalse(raw.contains("synthetic"))
        credentials.clear()
        assertEquals(raw, prefs.getString("favorites_v1", ""))
    }

    @Test fun corruptAndFutureFavoritesArePreservedAndInvalidDensityUsesStandard() = runBlocking {
        CredentialsManager(app).save("https://provider.example", "one", "synthetic")
        for (raw in listOf("invalid-json", """{"version":99,"accounts":{}}""")) {
            prefs.edit().putString("favorites_v1", raw).putString("grid_density", "invalid").commit()
            val store = BrowserPreferences(app, CredentialsManager(app))
            assertEquals(GridDensity.STANDARD, store.load().density)
            assertTrue(store.toggle("new-channel").favorites.isEmpty())
            assertEquals(raw, prefs.getString("favorites_v1", ""))
        }
    }
}
