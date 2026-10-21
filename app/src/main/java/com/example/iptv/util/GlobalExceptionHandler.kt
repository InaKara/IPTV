package com.example.iptv.util

import android.content.Context
import android.os.Process
import kotlin.system.exitProcess

/** Save only a generic notice; let Android's original handler report and terminate the crash. */
class GlobalExceptionHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            context.getSharedPreferences("crash_notice", Context.MODE_PRIVATE)
                .edit().putBoolean("pending", true).commit()
        } catch (_: Exception) {
            // A storage failure must never bypass the platform's crash handling.
        } finally {
            if (defaultHandler != null) defaultHandler.uncaughtException(thread, throwable)
            else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }
}
