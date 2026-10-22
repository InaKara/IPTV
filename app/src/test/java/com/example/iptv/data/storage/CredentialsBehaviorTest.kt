package com.example.iptv.data.storage

import android.app.Application
import android.content.Context
import com.example.iptv.BuildConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class CredentialsBehaviorTest {
    @Test fun defaultsAndSavedAccountRoundTripWithoutOverwritingCustomServer() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE).edit().clear().commit()
        val fresh = CredentialsManager(app)
        // Boolean assertions keep private debug defaults out of assertion messages/reports.
        assertTrue(fresh.serverUrl == BuildConfig.DEFAULT_SERVER_URL.trim().trimEnd('/'))
        assertTrue(fresh.username == BuildConfig.DEFAULT_USERNAME)
        assertTrue(fresh.password == BuildConfig.DEFAULT_PASSWORD)
        fresh.save("https://custom.example/", "demo", "synthetic")
        val restarted = CredentialsManager(app)
        assertEquals("https://custom.example", restarted.serverUrl)
        assertEquals("demo", restarted.username)
        assertEquals("synthetic", restarted.password)
    }
}
