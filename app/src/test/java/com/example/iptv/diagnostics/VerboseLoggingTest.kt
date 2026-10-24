package com.example.iptv.diagnostics

import android.app.Application
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class VerboseLoggingTest {
    @Test fun defaultPersistenceImmediateGatingAndPrivateInputsRespectDebugBoundary() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val logging = VerboseLogging(app)
        assertFalse(logging.enabled.value)
        val records = mutableListOf<String>()
        val gate = DiagnosticGate(true, { logging.enabled.value }) { tag, value -> records.add("$tag $value") }
        gate.event(DiagnosticEvent.LIST_BEGIN); assertTrue(records.isEmpty())
        logging.setEnabled(true)
        assertTrue(VerboseLogging(app).enabled.value)
        gate.event(DiagnosticEvent.LIST_OK, 123, 200)
        gate.startup("attempt=1 phase=grouping ms=42 channels=100000")
        gate.audio("codec=mp2 channels=2 rate=48000 supported=1 selected=1")
        assertEquals(3, records.size)
        listOf("https://provider.example/movie/demo/secret/1.mp4", "account=demo password=secret", "exception=private payload", "title=Film").forEach {
            gate.startup(it); gate.audio(it)
        }
        assertEquals(3, records.size)
        assertTrue(records.none { "secret" in it || "provider.example" in it || "Film" in it })
        DiagnosticGate(false, { true }) { _, _ -> fail("Non-debug policy must never emit verbose diagnostics") }.apply {
            event(DiagnosticEvent.LIBRARY_OPEN); startup("attempt=1 phase=connect ms=0"); audio("tracks=1 supported=1 selected=1")
        }
        logging.setEnabled(false)
        gate.event(DiagnosticEvent.PLAY_PREPARE); assertEquals(3, records.size)
        assertFalse(VerboseLogging(app).enabled.value)
    }
}
