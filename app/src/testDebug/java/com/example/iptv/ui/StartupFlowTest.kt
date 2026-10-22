package com.example.iptv.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.iptv.data.parser.M3UParser
import com.example.iptv.data.repository.ChannelRepositoryImpl
import com.example.iptv.data.storage.CredentialsManager
import com.example.iptv.data.storage.RecentlyWatchedManager
import com.example.iptv.diagnostics.StartupTimings
import com.example.iptv.ui.home.BrowserScreen
import com.example.iptv.ui.home.HomeViewModel
import com.example.iptv.ui.login.LoginContent
import com.example.iptv.ui.login.LoginUiState
import com.example.iptv.ui.login.LoginViewModel
import com.example.iptv.ui.theme.IPTVTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
@OptIn(ExperimentalCoroutinesApi::class)
class StartupFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loginThroughFirstBrowseLayoutReportsEachPhaseOnceAndFailureAllowsRetry() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setBody("private provider failure details"))
            server.enqueue(MockResponse().setBody("#EXTM3U\n#EXTINF:-1 group-title=\"Live\",Local News\nhttps://provider.example/stream"))
            val app = RuntimeEnvironment.getApplication()
            app.getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE).edit().clear().commit()
            val lines = CopyOnWriteArrayList<String>()
            val timing = StartupTimings(true, output = { lines.add(it) })
            val client = OkHttpClient()
            val manager = RecentlyWatchedManager(app)
            val credentials = CredentialsManager(app).apply { save(server.url("/").toString(), "private-user", "private-password") }
            val login = LoginViewModel(ChannelRepositoryImpl(M3UParser(), client, timing), credentials, manager, timing)
            val home = HomeViewModel(manager, timing)
            val store = ViewModelStore().apply { put("login", login); put("home", home) }
            try {
                compose.setContent {
                    IPTVTheme {
                        val state by login.uiState.collectAsStateWithLifecycle()
                        if (state is LoginUiState.Success) BrowserScreen((state as LoginUiState.Success).channels, { _, _ -> }, home)
                        else LoginContent(login.serverUrl, login.username, login.password, state, {}, {}, {}, login::onConnect)
                    }
                }
                compose.runOnIdle { login.onConnect() }
                compose.waitUntil(5000) { login.uiState.value is LoginUiState.Error }
                assertTrue(lines.any { "attempt=1 phase=failed" in it })
                compose.runOnIdle { login.onConnect(); login.onConnect() }
                compose.waitUntil(5000) { lines.any { "attempt=2 phase=browse_ready" in it } }
                val phases = lines.filter { it.startsWith("attempt=2 ") }.map { it.substringAfter("phase=").substringBefore(' ') }
                assertEquals(listOf("connect", "provider_wait", "download_parse", "history", "grouping", "browse_ready"), phases)
                compose.runOnIdle { home.onBrowseReady() }
                assertEquals(1, lines.count { "phase=browse_ready" in it })
                assertEquals(2, server.requestCount)
                assertTrue(lines.none { "private" in it || "provider.example" in it || "Local News" in it })
                assertNull(timing.current())
            } finally { store.clear(); Dispatchers.resetMain(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
        }
    }
}
