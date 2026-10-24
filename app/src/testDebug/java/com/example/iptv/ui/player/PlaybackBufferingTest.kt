package com.example.iptv.ui.player

import android.app.Application
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocation
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.storage.CredentialsManager
import java.lang.reflect.Proxy
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PlaybackBufferingTest {
    private val period = MediaPeriodId(Any())
    private fun prepared(control: LoadControl): LoadControl {
        fun renderer(type: Int) = Proxy.newProxyInstance(Renderer::class.java.classLoader, arrayOf(Renderer::class.java)) { _, method, _ ->
            if (method.name == "getTrackType") type else null
        } as Renderer
        val selection = Proxy.newProxyInstance(ExoTrackSelection::class.java.classLoader, arrayOf(ExoTrackSelection::class.java)) { _, _, _ -> null } as ExoTrackSelection
        control.onPrepared()
        control.onTracksSelected(Timeline.EMPTY, period, arrayOf(renderer(C.TRACK_TYPE_VIDEO), renderer(C.TRACK_TYPE_AUDIO)),
            TrackGroupArray.EMPTY, arrayOf(selection, selection))
        return control
    }
    private fun starts(control: LoadControl, buffered: Long, rebuffer: Boolean) = control.shouldStartPlayback(
        Timeline.EMPTY, period, buffered, 1f, rebuffer, C.TIME_UNSET)

    @Test fun vodStopsAtSizeTargetBeforeMinimumTimeAndReleasesAllocator() {
        val control = prepared(playbackLoadControl(true))
        val allocator = control.allocator
        val held = mutableListOf<Allocation>()
        try {
            assertTrue(control.shouldContinueLoading(0, 0, 1f))
            assertFalse(starts(control, 1_000_000, false))
            while (allocator.totalBytesAllocated < 24 * 1024 * 1024) held.add(allocator.allocate())
            assertEquals(24 * 1024 * 1024, allocator.totalBytesAllocated)
            assertFalse("Size must win even with less than ten seconds buffered", control.shouldContinueLoading(0, 1_000_000, 1f))
            assertTrue("A full byte target allows high-bitrate playback without waiting for an impossible time minimum", starts(control, 1_000_000, false))
        } finally {
            held.forEach { allocator.release(it) }
            control.onStopped(); control.onReleased()
        }
        assertEquals(0, allocator.totalBytesAllocated)
        prepared(control)
        assertTrue("A new playback can load after the old allocations are released", control.shouldContinueLoading(0, 0, 1f))
        control.onReleased()
    }
    @Test fun vodTimeWindowAndStartupDecisionsPreserveLiveBaseline() {
        val vod = prepared(playbackLoadControl(true))
        val live = prepared(playbackLoadControl(false))
        val baseline = prepared(DefaultLoadControl())
        try {
            assertTrue(vod.shouldContinueLoading(0, 9_999_999, 1f))
            assertTrue(vod.shouldContinueLoading(0, 20_000_000, 1f))
            assertFalse(vod.shouldContinueLoading(0, 30_000_000, 1f))
            assertFalse(vod.shouldContinueLoading(0, 20_000_000, 1f))
            assertTrue(vod.shouldContinueLoading(0, 9_999_999, 1f))
            for (buffered in listOf(0L, 10_000_000L, 30_000_000L, 50_000_000L, 40_000_000L, 9_000_000L)) {
                assertEquals(baseline.shouldContinueLoading(0, buffered, 1f), live.shouldContinueLoading(0, buffered, 1f))
            }
            for (rebuffer in listOf(false, true)) {
                val threshold = (if (rebuffer) DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS else DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS) * 1000L
                for (buffered in listOf(threshold - 1, threshold)) {
                    assertEquals(starts(baseline, buffered, rebuffer), starts(vod, buffered, rebuffer))
                    assertEquals(starts(baseline, buffered, rebuffer), starts(live, buffered, rebuffer))
                }
                assertFalse(starts(vod, threshold - 1, rebuffer)); assertTrue(starts(vod, threshold, rebuffer))
            }
        } finally { vod.onReleased(); live.onReleased(); baseline.onReleased() }
    }
    @Test fun actualPlayerUsesVodPolicyForLegacyAndLibraryAndDefaultForLive() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val server = MockWebServer().apply { start() }
        val credentials = CredentialsManager(app).apply { save(server.url("/").toString(), "demo", "synthetic") }
        val vm = PlayerViewModel(app, credentials)
        try {
            for ((vod, key) in listOf(true to null, true to "library:synthetic:MOVIE:1", false to null)) {
                server.enqueue(MockResponse().setResponseCode(404))
                val channel = M3UChannel("Fixture", "Fixture", null, server.url("/fixture.mp4").toString(), null, null, null, vod)
                vm.start(channel, key)
                // Inspect the actual engine's policy, not merely an independently built factory result.
                val engine = PlayerViewModel::class.java.getDeclaredField("engine").apply { isAccessible = true }.get(vm)!!
                val internal = engine.javaClass.getDeclaredField("internalPlayer").apply { isAccessible = true }.get(engine)
                val control = internal.javaClass.getDeclaredField("loadControl").apply { isAccessible = true }.get(internal) as LoadControl
                vm.releasePlayer() // Drain the real engine before exercising its captured policy in isolation.
                prepared(control)
                assertTrue(control.shouldContinueLoading(0, 0, 1f))
                assertEquals("VOD stops at thirty seconds; live retains its fifty-second baseline", !vod, control.shouldContinueLoading(0, 30_000_000, 1f))
                control.onReleased()
            }
        } finally { vm.releasePlayer(); server.close() }
    }
}
