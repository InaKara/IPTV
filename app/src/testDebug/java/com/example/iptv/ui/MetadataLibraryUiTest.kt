package com.example.iptv.ui

import android.app.Application
import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.example.iptv.data.library.*
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.*
import com.example.iptv.ui.home.*
import com.example.iptv.ui.library.LibraryViewModel
import com.example.iptv.ui.theme.IPTVTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.OkHttpClient
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
class MetadataLibraryUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun remoteOpensDetailsBeforePlayAndBackRestoresLibraryAndPlaylistFallback() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        app.noBackupFilesDir.resolve("library.db").delete()
        val creds = CredentialsManager(app).apply { save("https://provider.example", "demo", "synthetic") }
        val db = LibraryDatabase(app); val client = OkHttpClient(); val repo = LibraryRepository(db, creds, client)
        val account = repo.account()
        val film = LibraryEntry(LibraryKind.MOVIE, "42", "A movie", "1", "Films")
        db.transaction { sql ->
            db.category(sql, account.cache, LibraryKind.MOVIE, LibraryCategory("1", "Films"), 0)
            db.entry(sql, account.cache, film); db.fresh(sql, account.cache, LibraryKind.MOVIE, System.currentTimeMillis())
            db.putDetails(sql, account, film, LibraryDetails("The plot", "2024", "01:30:00", "Drama", "Actor", "Director"), System.currentTimeMillis(), 500)
        }
        runBlocking { repo.record(account, repo.resolve(account, film).channel, 4_980_000, 7_200_000, true) }
        val recents = RecentlyWatchedManager(app); val prefs = BrowserPreferences(app, creds)
        val home = HomeViewModel(recents, preferences = prefs, library = repo)
        val library = LibraryViewModel(repo, recents, prefs)
        val store = ViewModelStore().apply { put("home", home); put("library", library) }
        var back: OnBackPressedDispatcher? = null
        var browsing by mutableStateOf(true); var plays = 0
        val playlist = listOf(M3UChannel("News", "Live", null, "https://provider.example/live/1.ts", null, null, null, false),
            M3UChannel("Legacy film", "Legacy VOD", null, "https://provider.example/movie/old.mp4", null, null, null, true))
        try {
            compose.setContent {
                back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                IPTVTheme { if (browsing) BrowserScreen(playlist, { _, _ -> plays++; browsing = false }, home, library = library) }
            }
            compose.waitUntil(5000) { compose.onAllNodesWithTag("browser").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("sidebar_CATEGORIES").assertIsFocused()
            compose.onNodeWithTag("sidebar_VOD").performSemanticsAction(SemanticsActions.RequestFocus) { it() }.performKeyInput { pressKey(Key.DirectionCenter) }
            compose.onNodeWithTag("browser_heading").assertTextEquals("VOD")
            compose.onNodeWithTag("library_root_0").performKeyInput { pressKey(Key.DirectionCenter) }
            compose.waitUntil(5000) { compose.onAllNodesWithText("All movies").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("All movies").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("A movie").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("A movie").performSemanticsAction(SemanticsActions.RequestFocus) { it() }.performKeyInput { pressKey(Key.DirectionCenter) }
            compose.waitUntil(5000) { compose.onAllNodesWithTag("library_play").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(0, plays)
            compose.onNodeWithTag("browser_heading").assertTextEquals("A movie")
            compose.onNodeWithTag("library_play").assertTextEquals("Resume from 01:23")
            compose.onNodeWithTag("library_retry").assertDoesNotExist()
            compose.onNodeWithTag("library_fallback").assertDoesNotExist()
            compose.onNodeWithTag("library_play").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
            compose.waitUntil(5000) { plays == 1 }
            compose.runOnIdle { browsing = true }
            compose.onNodeWithTag("browser_heading").assertTextEquals("A movie")
            compose.runOnIdle { back!!.onBackPressed() }
            compose.waitUntil(5000) { compose.onAllNodesWithText("All movies").fetchSemanticsNodes().isEmpty() && compose.onAllNodesWithTag("library_grid").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("A movie").assertIsFocused()
            compose.onNodeWithTag("library_back").assertDoesNotExist()
            compose.onNodeWithTag("library_refresh").assertDoesNotExist()
            compose.onNodeWithTag("library_fallback").assertDoesNotExist()
            compose.onNodeWithTag("sidebar_VOD").performClick()
            compose.onNodeWithTag("library_root_4").performClick()
            compose.onNodeWithText("Legacy VOD").assertExists()
            compose.runOnIdle { back!!.onBackPressed() }
            compose.onNodeWithTag("library_root_4").assertExists()
        } finally { compose.runOnIdle { store.clear() }; db.close(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown(); Dispatchers.resetMain() }
    }
}
