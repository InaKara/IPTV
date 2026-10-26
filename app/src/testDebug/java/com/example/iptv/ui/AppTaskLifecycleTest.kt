package com.example.iptv.ui

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.iptv.MainActivity
import com.example.iptv.SplashActivity
import com.example.iptv.ui.error.ErrorActivity
import com.example.iptv.util.AppTaskNavigation
import com.example.iptv.util.GlobalExceptionHandler
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppTask
import org.robolectric.shadow.api.Shadow

class TaskEntryActivity : ComponentActivity() {
    override fun getTaskId() = 711
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
class AppTaskLifecycleTest {
    // Keep Compose's test clock/window environment managed around raw Activity controllers too.
    @get:Rule val compose = createComposeRule()
    @Test fun nonRootExitRemovesTheWholeOwnTaskWithoutTouchingOtherTasksOrSavedData() {
        val controller = Robolectric.buildActivity(TaskEntryActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(activity).setIsTaskRoot(false)
        val manager = activity.getSystemService(ActivityManager::class.java)
        val own = ShadowAppTask.newInstance()
        val other = ShadowAppTask.newInstance()
        Shadow.extract<ShadowAppTask>(own).setTaskInfo(ActivityManager.RecentTaskInfo().apply { id = 711; numActivities = 3 })
        Shadow.extract<ShadowAppTask>(other).setTaskInfo(ActivityManager.RecentTaskInfo().apply { id = 712; numActivities = 1 })
        shadowOf(manager).setAppTasks(listOf(other, own))
        val preferences = activity.getSharedPreferences("aypi_tivi_prefs", 0)
        preferences.edit().putString("username", "synthetic").putString("favorites_v1", "retained")
            .putBoolean("filter_adult", true).commit()
        val before = preferences.all.toMap()
        try {
            AppTaskNavigation.exit(activity)
            assertTrue(Shadow.extract<ShadowAppTask>(own).isFinishedAndRemoved)
            assertFalse(Shadow.extract<ShadowAppTask>(other).isFinishedAndRemoved)
            assertTrue(AppTaskNavigation.isClosing(activity))
            assertFalse(AppTaskNavigation.openBrowser(activity))
            assertNull(shadowOf(activity).nextStartedActivity)
            assertEquals(before, preferences.all)
            assertFalse(activity.getSharedPreferences("crash_notice", 0).getBoolean("pending", false))
            AppTaskNavigation.exit(activity) // Repeated confirmation cannot reopen anything.
            assertNull(shadowOf(activity).nextStartedActivity)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun browserHandoffRequiresResumedHostAndUsesTheExistingBrowserRoute() {
        val controller = Robolectric.buildActivity(TaskEntryActivity::class.java).create().start()
        val activity = controller.get()
        try {
            assertFalse(AppTaskNavigation.openBrowser(activity))
            assertNull(shadowOf(activity).nextStartedActivity)
            controller.resume()
            assertTrue(AppTaskNavigation.openBrowser(activity))
            val intent = shadowOf(activity).nextStartedActivity
            assertEquals(ComponentName(activity, MainActivity::class.java), intent.component)
            assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                intent.flags and (Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            assertTrue(activity.isFinishing)
            assertFalse(AppTaskNavigation.openBrowser(activity))
            assertNull(shadowOf(activity).nextStartedActivity)
            val info = activity.packageManager.getActivityInfo(ComponentName(activity, MainActivity::class.java), 0)
            assertEquals(android.content.pm.ActivityInfo.LAUNCH_SINGLE_TOP, info.launchMode)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun actualCrashMarkerStillDelegatesAndIsConsumedOnceByRealSplash() {
        val app = RuntimeEnvironment.getApplication()
        val marker = app.getSharedPreferences("crash_notice", 0)
        marker.edit().clear().commit()
        var delegated = false
        GlobalExceptionHandler(app) { _, _ -> delegated = true }
            .uncaughtException(Thread.currentThread(), IllegalStateException("synthetic"))
        assertTrue(delegated); assertTrue(marker.getBoolean("pending", false))
        val controller = Robolectric.buildActivity(SplashActivity::class.java).create()
        try {
            assertEquals(ComponentName(app, ErrorActivity::class.java), shadowOf(controller.get()).nextStartedActivity.component)
            assertTrue(controller.get().isFinishing)
            assertFalse(marker.getBoolean("pending", false))
            assertTrue(marker.all.isEmpty())
        } finally { controller.destroy() }
    }
}
