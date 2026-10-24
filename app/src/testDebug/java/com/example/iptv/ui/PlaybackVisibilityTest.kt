package com.example.iptv.ui

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import androidx.media3.ui.PlayerControlView
import com.example.iptv.R
import com.example.iptv.ui.player.MutablePlayback
import com.example.iptv.ui.player.TvPlayerView
import com.example.iptv.ui.player.guardedPlayback
import java.io.File
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackVisibilityTest {
    private fun layout(view: View, width: Int = 960, height: Int = 540) {
        repeat(2) {
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun key(view: View, code: Int) {
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun bounds(parent: android.view.ViewGroup, child: View): Rect = Rect(0, 0, child.width, child.height).also {
        parent.offsetDescendantRectToMyCoords(child, it)
    }

    // A painted frame isolates controller compositing; it does not simulate a hardware decoder.
    private fun frame(width: Int, height: Int): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        val canvas = Canvas(it)
        val paint = Paint()
        val colours = intArrayOf(0xffffdc64.toInt(), 0xff102238.toInt(), 0xff71bad0.toInt(), 0xff251820.toInt())
        for (row in 0..3) for (column in 0..5) {
            paint.color = colours[(row + column) % colours.size]
            canvas.drawRect(column * width / 6f, row * height / 4f,
                (column + 1) * width / 6f, (row + 1) * height / 4f, paint)
        }
    }

    @Test fun controllerLeavesBrightAndDarkFramesClearAboveBottomQuarter() {
        val host = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            MutablePlayback().use { scene ->
              for ((width, height) in listOf(960 to 540, 1920 to 1080)) {
                val configuration = android.content.res.Configuration(host.get().resources.configuration).apply {
                    densityDpi = if (width == 1920) 320 else 160
                }
                val context = host.get().createConfigurationContext(configuration).apply { theme.setTo(host.get().theme) }
                val view = TvPlayerView(context)
                host.get().setContentView(view)
                view.player = guardedPlayback(scene.player, false)
                try {
                    for (live in listOf(true, false)) {
                        scene.live = live
                        scene.playing = live
                        view.player = guardedPlayback(scene.player, !live)
                        view.isLiveChannel = live
                        view.hideController()
                        layout(view, width, height)
                        val controls = view.findViewById<PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                        val before = frame(width, height)
                        val after = before.copy(Bitmap.Config.ARGB_8888, true)
                        try {
                            key(view, KeyEvent.KEYCODE_MENU)
                            layout(view, width, height)
                            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                            assertTrue(view.isControllerFullyVisible)
                            val panel = bounds(controls, view.findViewById(R.id.playback_controls_panel))
                            assertTrue("At least three quarters of the picture stay clear", panel.top >= height * 3 / 4)
                            assertEquals(Rect(0, panel.top, width, height), panel)
                            val transport = view.findViewById<android.view.ViewGroup>(androidx.media3.ui.R.id.exo_center_controls)
                            val detail = (0 until transport.childCount).joinToString { index ->
                                val child = transport.getChildAt(index)
                                "${child.resources.getResourceEntryName(child.id)}=${child.width}x${child.height},params=${child.layoutParams.width}x${child.layoutParams.height},visible=${child.visibility}"
                            }
                            listOf(androidx.media3.ui.R.id.exo_center_controls, androidx.media3.ui.R.id.exo_progress,
                                androidx.media3.ui.R.id.exo_time, androidx.media3.ui.R.id.exo_settings).forEach { id ->
                                val child = view.findViewById<View>(id)
                                assertTrue("${child.resources.getResourceEntryName(id)} ${bounds(controls, child)} must be shown inside $panel; $detail",
                                    child.isShown && panel.contains(bounds(controls, child)))
                            }
                            controls.draw(Canvas(after))
                            val expected = IntArray(width * panel.top)
                            val actual = IntArray(expected.size)
                            before.getPixels(expected, 0, width, 0, 0, width, panel.top)
                            after.getPixels(actual, 0, width, 0, 0, width, panel.top)
                            assertArrayEquals("No veil or controls may change the picture above the panel", expected, actual)
                            val play = view.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)
                            val focus = bounds(controls, play)
                            assertTrue(play.hasFocus())
                            assertEquals(TvPlayerView.AMBER, after.getPixel(focus.left + 3, focus.top + 3))
                            assertNotEquals("Only the panel shades its background", before.getPixel(2, height - 2), after.getPixel(2, height - 2))
                            val file = File("build/reports/playback-visible-${if (live) "live" else "vod"}-$width.png")
                            file.parentFile!!.mkdirs()
                            file.outputStream().use { after.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        } finally { before.recycle(); after.recycle() }
                    }
                } finally { view.player = null }
              }
            }
        } finally { host.pause().stop().destroy() }
    }

    @Test fun openingSeekingAndDismissingControlsPreserveSurfaceAndPlayIntent() {
        val host = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            for (live in listOf(true, false)) for (playing in listOf(true, false)) MutablePlayback().use { scene ->
                scene.live = live
                scene.playing = playing
                val view = TvPlayerView(host.get())
                host.get().setContentView(view)
                view.player = guardedPlayback(scene.player, !live)
                view.isLiveChannel = live
                val visibility = mutableListOf<Boolean>()
                view.onControlsVisibilityChanged = { visibility.add(it) }
                layout(view)
                // Simulate the player having rendered its first frame, without a network source.
                scene.listeners.toList().forEach { it.onRenderedFirstFrame() }
                val surface = requireNotNull(view.videoSurfaceView)
                val surfaceBounds = bounds(view, surface)
                val shutter = view.findViewById<View>(androidx.media3.ui.R.id.exo_shutter)
                fun assertPictureRetained() {
                    assertSame(surface, view.videoSurfaceView)
                    assertEquals(surfaceBounds, bounds(view, surface))
                    assertEquals(View.VISIBLE, surface.visibility)
                    assertEquals(View.INVISIBLE, shutter.visibility)
                    assertEquals(playing, scene.playing)
                }
                try {
                    key(view, KeyEvent.KEYCODE_MENU); layout(view)
                    assertPictureRetained()
                    assertEquals(true, visibility.last())
                    val play = view.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)
                    assertTrue(play.hasFocus())
                    val rewind = view.findViewById<View>(androidx.media3.ui.R.id.exo_rew_with_amount)
                    val forward = view.findViewById<View>(androidx.media3.ui.R.id.exo_ffwd_with_amount)
                    repeat(2) {
                        assertTrue(rewind.requestFocus()); key(view, KeyEvent.KEYCODE_DPAD_CENTER)
                        assertEquals(10000L, scene.position)
                        assertTrue(forward.requestFocus()); key(view, KeyEvent.KEYCODE_DPAD_CENTER)
                        assertEquals(20000L, scene.position)
                        assertPictureRetained()
                    }
                    val settings = view.findViewById<View>(androidx.media3.ui.R.id.exo_settings)
                    assertTrue(settings.requestFocus()); key(view, KeyEvent.KEYCODE_DPAD_CENTER)
                    val popup = requireNotNull(shadowOf(RuntimeEnvironment.getApplication()).latestPopupWindow)
                    assertTrue(popup.isShowing)
                    assertPictureRetained()
                    popup.dismiss()
                    assertFalse(popup.isShowing)
                    assertTrue(view.isControllerFullyVisible)
                    key(view, KeyEvent.KEYCODE_BACK)
                    assertFalse(view.isControllerFullyVisible)
                    assertEquals(false, visibility.last())
                    assertPictureRetained()
                    key(view, KeyEvent.KEYCODE_MENU); layout(view)
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3100))
                    assertFalse(view.isControllerFullyVisible)
                    assertEquals(false, visibility.last())
                    assertPictureRetained()
                } finally { view.onControlsVisibilityChanged = {}; view.player = null }
            }
        } finally { host.pause().stop().destroy() }
    }
}
