package com.example.iptv.ui

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import com.example.iptv.ExitConfirmDialog
import com.example.iptv.data.library.*
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.*
import com.example.iptv.ui.browser.BrowserDestination
import com.example.iptv.ui.home.BrowserScreen
import com.example.iptv.ui.home.HomeViewModel
import com.example.iptv.ui.theme.IPTVTheme
import com.example.iptv.util.AppTaskNavigation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppTask
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class MainExitIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    @Before fun mainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun close() { compose.runOnIdle { store.clear() }; Dispatchers.resetMain() }

    @Test fun confirmedExitDialogPreservesCancelAndClosesWholeTaskWithBrowserFocusDisabled() = runBlocking<Unit> {
        val app = RuntimeEnvironment.getApplication()
        val preferences = app.getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        app.getSharedPreferences("crash_notice", 0).edit().clear().commit()
        val credentials = CredentialsManager(app).apply { save("https://provider.example", "demo", "synthetic") }
        val prefs = BrowserPreferences(app, credentials)
        val saved = prefs.load(); prefs.toggle("retained-favorite", saved.account); prefs.adultFilter(true)
        val live = M3UChannel("News", "Live", null, "https://provider.example/live/1.ts", null, null, null, false)
        val account = LibraryAccount.create(credentials.serverUrl, credentials.username, credentials.password)
        val progress = WatchProgress(45_000, 100_000, false, true, 1234)
        val db = LibraryDatabase(app)
        db.saveProgress(account.owner, account.key(LibraryKind.MOVIE,"9"), live.copy(isVod=true), "", progress)
        val vm = HomeViewModel(RecentlyWatchedManager(app), preferences=prefs)
        store.put("home", vm)
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        lateinit var activity: Activity
        lateinit var back: OnBackPressedDispatcher
        var exitCalls = 0
        try {
            compose.setContent {
                activity = generateSequence(LocalView.current.context) { (it as? ContextWrapper)?.baseContext }
                    .filterIsInstance<Activity>().first()
                back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    IPTVTheme {
                        ExitConfirmDialog { exitCalls++; AppTaskNavigation.exit(activity) }
                        BrowserScreen(listOf(live), { _, _ -> fail("Exit must not start playback") }, vm)
                    }
                }
            }
            compose.waitUntil(5000) { compose.onAllNodesWithTag("browser").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("sidebar_FAVORITES").assertIsFocused().assertIsSelected()
            assertFalse(vm.browser.state.value.canGoBack)
            val manager = activity.getSystemService(ActivityManager::class.java)
            val task = ShadowAppTask.newInstance()
            Shadow.extract<ShadowAppTask>(task).setTaskInfo(ActivityManager.RecentTaskInfo().apply { id = activity.taskId; numActivities=2 })
            shadowOf(manager).setAppTasks(listOf(task))
            compose.runOnIdle { back.onBackPressed() }
            compose.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                .performKeyInput { pressKey(Key.DirectionCenter) }
            compose.waitForIdle()
            assertFalse(Shadow.extract<ShadowAppTask>(task).isFinishedAndRemoved)
            compose.onNodeWithTag("sidebar_FAVORITES").assertIsFocused()
            val before = preferences.all.toMap()
            compose.runOnIdle { back.onBackPressed() }
            compose.onNodeWithText("Exit").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                .performKeyInput { pressKey(Key.DirectionCenter) }
            compose.waitForIdle()
            assertEquals("Remote confirmation must invoke Exit once", 1, exitCalls)
            assertTrue("Confirmed Exit must remove the own multi-activity task", Shadow.extract<ShadowAppTask>(task).isFinishedAndRemoved)
            assertTrue(AppTaskNavigation.isClosing(activity))
            compose.runOnIdle {
                owner.lifecycle.currentState = Lifecycle.State.CREATED
                vm.browser.open(BrowserDestination.SEARCH)
                owner.lifecycle.currentState = Lifecycle.State.RESUMED
            }
            compose.waitForIdle()
            assertNull(shadowOf(activity).nextStartedActivity)
            assertEquals(before, preferences.all)
            assertEquals(progress, db.progress(account.owner, account.key(LibraryKind.MOVIE,"9")))
            assertFalse(app.getSharedPreferences("crash_notice", 0).getBoolean("pending", false))
        } finally { db.close() }
    }
}
