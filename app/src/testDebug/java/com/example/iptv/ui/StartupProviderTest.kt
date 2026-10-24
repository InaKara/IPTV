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
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        app.getSharedPreferences("aypi_tivi_prefs", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val logging = com.example.iptv.diagnostics.VerboseLogging(app)
        val timing = AppModule.provideStartupTimings(logging)
        val attempt = timing.begin()
        assertNotNull("The debug variant must enable startup diagnostics", attempt)
        attempt!!.finish(StartupTimings.Outcome.CANCELLED)
        assertTrue("Optional startup output defaults Off", ShadowLog.getLogsForTag("AypiStartup").isEmpty())
        logging.setEnabled(true)
        timing.begin()!!.finish(StartupTimings.Outcome.CANCELLED)
        val records = ShadowLog.getLogsForTag("AypiStartup")
        assertEquals(2, records.size)
        assertTrue(records.all { it.type == Log.INFO })
        assertTrue(records.all { it.msg.matches(Regex("attempt=2 phase=(connect|cancelled) ms=\\d+")) })
        logging.setEnabled(false)
    }
}
