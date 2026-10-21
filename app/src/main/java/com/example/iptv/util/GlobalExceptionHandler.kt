package com.example.iptv.util

import android.content.Context
import android.content.Intent
import android.os.Process
import com.example.iptv.ui.error.ErrorActivity
import java.io.PrintWriter
import java.io.StringWriter

class GlobalExceptionHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val stringWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stringWriter))
        val stackTrace = stringWriter.toString()

        try {
            val intent = Intent(context, ErrorActivity::class.java).apply {
                putExtra("error", stackTrace)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)
            
            // Kill the process to avoid restart loops
            Process.killProcess(Process.myPid())
            System.exit(10)
        } catch (e: Exception) {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
