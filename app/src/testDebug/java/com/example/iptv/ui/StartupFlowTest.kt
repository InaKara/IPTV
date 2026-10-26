package com.example.iptv.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.iptv.data.parser.M3UParser
import com.example.iptv.data.repository.ChannelRepositoryImpl
import com.example.iptv.data.storage.CredentialsManager
import com.example.iptv.data.storage.RecentlyWatchedManager
import com.example.iptv.data.storage.ChannelAccount
import com.example.iptv.data.storage.ChannelSnapshot
import com.example.iptv.data.storage.FileChannelSnapshots
import com.example.iptv.data.model.M3UChannel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.*
import kotlinx.coroutines.runBlocking
import com.example.iptv.diagnostics.StartupTimings
import com.example.iptv.ui.home.BrowserScreen
import com.example.iptv.ui.home.HomeViewModel
import com.example.iptv.ui.login.AccountSettingsContent
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

    @Test fun downloadedBlankCategoriesReachBrowserAndSurviveFreshCachedStartup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""
                #EXTM3U
                #EXTINF:-1 group-title="",Ungrouped
                https://provider.example/blank
                #EXTINF:-1 group-title="   ",Whitespace
                https://provider.example/spaces
                #EXTINF:-1 group-title="Local",News
                https://provider.example/news
            """.trimIndent()))
            val app = RuntimeEnvironment.getApplication()
            app.getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE).edit().clear().commit()
            val account = ChannelAccount(server.url("/").toString(), "demo", "synthetic")
            val credentials = CredentialsManager(app).apply { save(account.server, account.username, account.password) }
            val cache = FileChannelSnapshots(app)
            val client = OkHttpClient()
            val lines = CopyOnWriteArrayList<String>()
            fun models(): Pair<LoginViewModel, HomeViewModel> {
                val timing = StartupTimings(true, output = { lines.add(it) })
                val recent = RecentlyWatchedManager(app)
                return LoginViewModel(ChannelRepositoryImpl(M3UParser(), client, timing), credentials, recent, timing, cache) to
                    HomeViewModel(recent, timing)
            }
            var active by mutableStateOf(models())
            val first = ViewModelStore().apply { put("startup", active.first); put("home", active.second) }
            val second = ViewModelStore()
            try {
                compose.setContent {
                    IPTVTheme {
                        val login = active.first
                        key(login) {
                            val state by login.uiState.collectAsStateWithLifecycle()
                            LaunchedEffect(login) { login.onForeground() }
                            (state as? LoginUiState.Success)?.let {
                                BrowserScreen(it.channels, { _, _ -> }, active.second, onBrowseReady = login::onBrowseReady)
                            }
                        }
                    }
                }
                compose.waitUntil(5000) { lines.count { "phase=browse_ready" in it } == 1 }
                compose.onNodeWithTag("browser_heading").assertTextEquals("Favorites")
                assertEquals(1, server.requestCount)
                val snapshot = runBlocking { cache.read(account) }!!
                assertEquals(listOf("", "   ", "Local"), snapshot.channels.map { it.groupTitle })
                assertEquals(listOf("Ungrouped", "Whitespace", "News"), snapshot.channels.map { it.name })
                compose.runOnIdle {
                    first.clear()
                    active = models().also { second.put("startup", it.first); second.put("home", it.second) }
                }
                compose.waitForIdle()
                try {
                    compose.waitUntil(5000) { lines.count { "phase=browse_ready" in it } == 2 }
                } catch (error: Throwable) {
                    throw AssertionError("Cached startup: login=${active.first.uiState.value.javaClass.simpleName}, " +
                        "home=${active.second.uiState.value.javaClass.simpleName}, requests=${server.requestCount}, phases=$lines", error)
                }
                compose.onNodeWithTag("browser_heading").assertTextEquals("Favorites")
                assertEquals(1, server.requestCount)
                assertEquals(snapshot.channels, (active.first.uiState.value as LoginUiState.Success).channels)
                assertEquals(snapshot.downloadedAt, (active.first.uiState.value as LoginUiState.Success).downloadedAt)
            } finally {
                first.clear(); second.clear(); Dispatchers.resetMain()
                client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
            }
        }
    }

    @Test fun freshFileSnapshotStartsBrowserWithoutProviderOrConnectAndReportsCacheLayout() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        MockWebServer().use { server ->
            val app = RuntimeEnvironment.getApplication()
            app.getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE).edit().clear().commit()
            val account = ChannelAccount(server.url("/").toString(), "demo", "synthetic")
            val credentials = CredentialsManager(app).apply { save(account.server, account.username, account.password) }
            val cache = FileChannelSnapshots(app)
            val channels = listOf(M3UChannel("Cached News", "Local", null, "https://provider.example/1", null, null, null, false))
            runBlocking { cache.write(account, ChannelSnapshot(channels, System.currentTimeMillis())) }
            val lines = CopyOnWriteArrayList<String>()
            val timing = StartupTimings(true, output = { lines.add(it) })
            val client = OkHttpClient(); val manager = RecentlyWatchedManager(app)
            val login = LoginViewModel(ChannelRepositoryImpl(M3UParser(), client, timing), credentials, manager, timing, cache)
            val home = HomeViewModel(manager, timing)
            val store = ViewModelStore().apply { put("startup", login); put("home", home) }
            try {
                compose.setContent {
                    IPTVTheme {
                        val state by login.uiState.collectAsStateWithLifecycle()
                        LaunchedEffect(Unit) { login.onForeground() }
                        (state as? LoginUiState.Success)?.let {
                            BrowserScreen(it.channels, { _, _ -> }, home, onBrowseReady = login::onBrowseReady)
                        }
                    }
                }
                compose.waitUntil(5000) { lines.any { "phase=browse_ready" in it } }
                compose.onNodeWithTag("browser_heading").assertTextEquals("Favorites")
                compose.onNodeWithText("Connect").assertDoesNotExist()
                compose.onNodeWithText("Save and load").assertDoesNotExist()
                assertEquals(0, server.requestCount)
                val phases = lines.map { it.substringAfter("phase=").substringBefore(' ') }
                assertEquals(listOf("connect", "cache", "history", "grouping", "browse_ready"), phases)
            } finally { store.clear(); Dispatchers.resetMain(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
        }
    }

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
                        else AccountSettingsContent(login.serverUrl, login.username, login.password, state, {}, {}, {}, login::onConnect, login::refresh)
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
