package com.example.iptv.util

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import com.example.iptv.MainActivity

/** Only the current app task is closed; no process kill or preference reset. */
internal object AppTaskNavigation {
    private val closingTasks = java.util.WeakHashMap<Application, MutableState<Int?>>()
    private fun closingTask(activity: Activity) = closingTasks.getOrPut(activity.application) { mutableStateOf(null) }
    fun isClosing(activity: Activity) = closingTask(activity).value == activity.taskId

    @Suppress("DEPRECATION")
    fun exit(activity: Activity) {
        if (isClosing(activity)) return
        closingTask(activity).value = activity.taskId
        val manager = activity.getSystemService(ActivityManager::class.java)
        val task = manager.appTasks.firstOrNull { it.taskInfo?.id == activity.taskId }
        if (task != null) {
            // Unlike Activity.finishAndRemoveTask, this also removes a non-root duplicate's task.
            task.finishAndRemoveTask()
        } else {
            // The task may already have been removed by Android. Finish any remaining same-affinity UI.
            activity.finishAffinity()
            activity.finishAndRemoveTask()
        }
    }

    fun openBrowser(activity: ComponentActivity): Boolean {
        if (activity.isFinishing || activity.isDestroyed || isClosing(activity) ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
        activity.startActivity(Intent(activity, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        activity.finish()
        return true
    }
}
