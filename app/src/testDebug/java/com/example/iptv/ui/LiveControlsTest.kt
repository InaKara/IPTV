package com.example.iptv.ui

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.media3.common.C
import com.example.iptv.R
import com.example.iptv.ui.player.MutablePlayback
import com.example.iptv.ui.player.TvPlayerView
import com.example.iptv.ui.player.guardedPlayback
import java.time.Duration
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LiveControlsTest {
    private fun key(view: View, code: Int) {
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
    private fun layout(view: View) {
        repeat(2) {
            view.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 960, 540)
            // Media3 posts its size/overflow adjustment after the layout pass.
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
    private fun capture(view: View, name: String) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        view.invalidate()
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val file = File("build/reports/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File("build/reports/$name.views").writeText(listOf(androidx.media3.ui.R.id.exo_center_controls,
            androidx.media3.ui.R.id.exo_time, androidx.media3.ui.R.id.exo_rew_with_amount,
            androidx.media3.ui.R.id.exo_play_pause).joinToString("\n") {
            val child = view.findViewById<View>(it)
            "${view.resources.getResourceEntryName(it)}: alpha=${child.alpha} visibility=${child.visibility} bounds=${child.left},${child.top},${child.width},${child.height}"
        })
    }

    @Test fun liveActionLagFocusAndNativeStepButtonsFollowChangingCapabilities() {
        val host = Robolectric.buildActivity(Activity::class.java).setup()
        MutablePlayback().use { scene ->
            val view = TvPlayerView(host.get())
            host.get().setContentView(view)
            view.player = guardedPlayback(scene.player, false)
            view.isLiveChannel = true
            layout(view)
            try {
                key(view, KeyEvent.KEYCODE_MENU); layout(view)
                val live = view.findViewById<Button>(R.id.go_live)
                val lag = view.findViewById<TextView>(R.id.live_lag)
                val play = view.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)
                val rewind = view.findViewById<Button>(androidx.media3.ui.R.id.exo_rew_with_amount)
                val forward = view.findViewById<Button>(androidx.media3.ui.R.id.exo_ffwd_with_amount)
                assertEquals(View.GONE, view.findViewById<View>(androidx.media3.ui.R.id.exo_duration).visibility)
                assertEquals("-00:25", lag.text.toString())
                assertTrue(live.isEnabled); assertTrue(rewind.isEnabled); assertTrue(forward.isEnabled)
                assertEquals("10", rewind.text.toString()); assertEquals("10", forward.text.toString())
                listOf(rewind, forward).forEach { button ->
                    assertTrue(button.width > 0 && button.height > 0)
                    val icon = Bitmap.createBitmap(button.width, button.height, Bitmap.Config.ARGB_8888)
                    button.draw(Canvas(icon))
                    val pixels = IntArray(icon.width * icon.height)
                    icon.getPixels(pixels, 0, icon.width, 0, 0, icon.width, icon.height)
                    assertTrue("Native seek icon remains visibly drawn", pixels.any { android.graphics.Color.alpha(it) > 0 && android.graphics.Color.red(it) > 180 })
                }
                assertTrue(play.hasFocus())
                scene.changed(); assertTrue("Live updates must not steal Menu focus", play.hasFocus())
                rewind.performClick(); assertEquals(10000L, scene.position)
                forward.performClick(); assertEquals(20000L, scene.position)
                // Verify native remote focus can reach the lower-bar Live action.
                val pending = ArrayDeque<View>(); val seen = mutableSetOf<View>()
                pending.add(play)
                while (pending.isNotEmpty()) {
                    val item = pending.removeFirst()
                    if (!seen.add(item)) continue
                    listOf(View.FOCUS_UP, View.FOCUS_DOWN, View.FOCUS_LEFT, View.FOCUS_RIGHT).forEach {
                        item.focusSearch(it)?.let { next -> if (next !in seen) pending.add(next) }
                    }
                }
                assertTrue("Live must be reachable with directional focus", live in seen)
                assertTrue(live.requestFocus())
                val image = Bitmap.createBitmap(live.width, live.height, Bitmap.Config.ARGB_8888)
                live.draw(Canvas(image))
                assertEquals(TvPlayerView.AMBER, image.getPixel(3, 3))
                assertEquals(TvPlayerView.DARK, live.currentTextColor)
                capture(view, "playback-live-controls")
                key(view, KeyEvent.KEYCODE_DPAD_CENTER)
                assertEquals(1, scene.defaultSeeks); assertEquals(45000L, scene.position)
                scene.changed()
                assertEquals("● Live", live.text.toString()); assertEquals("-00:00", lag.text.toString())
                assertTrue(live.hasFocus())
                scene.position = 10000
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100))
                assertEquals("-00:35", lag.text.toString())
                assertTrue("Live focus must survive periodic lag updates", live.hasFocus())
                scene.seekable = false; scene.known = false; scene.duration = C.TIME_UNSET
                scene.changed()
                assertFalse(live.isEnabled); assertFalse(rewind.isEnabled); assertFalse(forward.isEnabled)
                assertEquals("—", lag.text.toString())
                val focus = view.findFocus()
                assertTrue("Unavailable Live action must leave a visible, enabled focus target", focus != null && focus.isEnabled && focus.isShown)
                assertNotSame(live, focus)
            } finally {
                view.player = null
                assertTrue(scene.listeners.isEmpty())
                host.pause().stop().destroy()
            }
        }
    }

    @Test fun liveAndVodTransitionsRetainTimesShortcutsAndControllerTimeout() {
        val host = Robolectric.buildActivity(Activity::class.java).setup()
        MutablePlayback().use { scene ->
            val view = TvPlayerView(host.get())
            host.get().setContentView(view)
            val offsets = mutableListOf<Long>()
            view.seek = { offsets.add(it) }
            view.player = guardedPlayback(scene.player, false)
            view.isLiveChannel = true
            layout(view)
            try {
                key(view, KeyEvent.KEYCODE_DPAD_LEFT)
                key(view, KeyEvent.KEYCODE_MEDIA_REWIND); key(view, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
                assertEquals(listOf(-10000L, 10000L), offsets)
                assertFalse(view.isControllerFullyVisible)
                key(view, KeyEvent.KEYCODE_MENU); layout(view)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2000))
                assertTrue(view.isControllerFullyVisible)
                key(view, KeyEvent.KEYCODE_DPAD_DOWN)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2000))
                assertTrue(view.isControllerFullyVisible)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100))
                assertFalse(view.isControllerFullyVisible) // Position polling does not keep controls open.
                scene.live = false; scene.duration = 120000; scene.position = 20000
                view.player = guardedPlayback(scene.player, true)
                view.isLiveChannel = false
                key(view, KeyEvent.KEYCODE_DPAD_RIGHT)
                assertEquals(30000L, offsets.last())
                key(view, KeyEvent.KEYCODE_MENU); layout(view)
                assertEquals(View.GONE, view.findViewById<View>(R.id.go_live).visibility)
                assertEquals(View.GONE, view.findViewById<View>(R.id.live_lag).visibility)
                assertEquals(View.VISIBLE, view.findViewById<View>(androidx.media3.ui.R.id.exo_position).visibility)
                assertEquals(View.VISIBLE, view.findViewById<View>(androidx.media3.ui.R.id.exo_duration).visibility)
                assertEquals("00:20", view.findViewById<TextView>(androidx.media3.ui.R.id.exo_position).text.toString())
                assertEquals("02:00", view.findViewById<TextView>(androidx.media3.ui.R.id.exo_duration).text.toString())
                val rewind = view.findViewById<Button>(androidx.media3.ui.R.id.exo_rew_with_amount)
                val forward = view.findViewById<Button>(androidx.media3.ui.R.id.exo_ffwd_with_amount)
                assertTrue("VOD rewind remains enabled and visible", rewind.isEnabled && rewind.isShown)
                assertTrue("VOD forward remains enabled and visible", forward.isEnabled && forward.isShown)
                assertEquals("10", rewind.text.toString()); assertEquals("10", forward.text.toString())
                capture(view, "playback-vod-controls")
                key(view, KeyEvent.KEYCODE_BACK); assertFalse(view.isControllerFullyVisible)
            } finally { view.player = null; host.pause().stop().destroy() }
        }
    }
}
