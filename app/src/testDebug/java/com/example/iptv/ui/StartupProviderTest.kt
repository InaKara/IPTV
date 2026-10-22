package com.example.iptv.ui

import android.app.Application
import android.util.Log
import com.example.iptv.di.AppModule
import com.example.iptv.diagnostics.StartupTimings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class StartupProviderTest {
    @Test fun debugProviderUsesInfoPriorityVisibleWithFireOsDefaultLogThreshold() {
        ShadowLog.clear()
        val timing = AppModule.provideStartupTimings()
        val attempt = timing.begin()
        assertNotNull("The debug variant must enable startup diagnostics", attempt)
        attempt!!.finish(StartupTimings.Outcome.CANCELLED)
        val records = ShadowLog.getLogsForTag("AypiStartup")
        assertEquals(2, records.size)
        assertTrue(records.all { it.type == Log.INFO })
        assertTrue(records.all { it.msg.matches(Regex("attempt=1 phase=(connect|cancelled) ms=\\d+")) })
    }
}
