package com.example.iptv.ui.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.example.iptv.data.library.*
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.*
import com.example.iptv.ui.browser.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class MetadataStartupTest {
    @Test fun liveFirstLayoutDoesNotOpenMetadataAndDemandedAliasesRetainLegacyFavorites() = runBlocking<Unit> {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val file = app.noBackupFilesDir.resolve("library.db").apply { delete() }
        val credentials = CredentialsManager(app).apply { save("https://provider.example", "demo", "synthetic") }
        val db = LibraryDatabase(app)
        val client = OkHttpClient.Builder().addInterceptor { error("No network request belongs to startup reconciliation") }.build()
        val repo = LibraryRepository(db, credentials, client)
        val account = repo.account()
        val channel = M3UChannel("Movie", "Films", null, account.stream(LibraryEntry(LibraryKind.MOVIE, "42", "Movie")), null, null, null, true)
        val source = listOf(channel.copy(name = "News", streamUrl = "https://provider.example/live/1.ts", isVod = false), channel)
        val legacy = BrowserCatalog.create(source).channels[1]
        val preferences = BrowserPreferences(app, credentials)
        preferences.load(); preferences.toggle(legacy.id)
        val recents = RecentlyWatchedManager(app)
        recents.activate(credentials.serverUrl, credentials.username, source)
        val vm = HomeViewModel(recents, preferences = preferences, library = repo)
        val store = ViewModelStore().apply { put("browser", vm) }
        try {
            vm.loadChannels(source)
            withTimeout(5000) { vm.uiState.first { it is HomeUiState.Success } }
            assertFalse(file.exists()); assertEquals(BrowserDestination.CATEGORIES, vm.browser.state.value.location.destination)
            vm.syncLibrary(); assertFalse("Live browsing does not request metadata work", file.exists())
            vm.browser.open(BrowserDestination.FAVORITES)
            val demanded = async(start = CoroutineStart.UNDISPATCHED) { vm.syncLibrary() }
            assertTrue(demanded.isActive); assertFalse("Even a demanded surface waits for first layout", file.exists())
            vm.onBrowseReady(); withTimeout(5000) { demanded.await() }
            assertTrue(file.exists())
            assertEquals(LibraryKind.MOVIE to "42", db.alias(account.owner, legacy.id))
            assertTrue(legacy.id in preferences.load().favorites)
            assertEquals(channel.streamUrl, db.storedPlayable(account.owner, account.key(LibraryKind.MOVIE, "42"))?.streamUrl)
            assertEquals(listOf(legacy.id), vm.browser.state.value.items.map { it.id })
            credentials.username = "other"
            vm.loadChannels(listOf(channel.copy(groupTitle = "New account")))
            withTimeout(5000) { vm.uiState.first { it is HomeUiState.Success && it.groups.any { group -> group.name == "New account" } } }
            vm.browser.open(BrowserDestination.FAVORITES)
            val cancelled = async(start = CoroutineStart.UNDISPATCHED) { vm.syncLibrary() }
            assertTrue(cancelled.isActive)
            cancelled.cancelAndJoin()
            assertTrue(preferences.load().favorites.isEmpty())
            assertNull(db.alias(repo.account().owner, legacy.id))
            assertNotNull(db.alias(account.owner, legacy.id))
            credentials.username = "demo"
            assertTrue(legacy.id in preferences.load().favorites)
        } finally { store.clear(); db.close(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown(); Dispatchers.resetMain() }
    }
}
