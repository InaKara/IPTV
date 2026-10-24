package com.example.iptv.ui

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.media3.common.*
import androidx.media3.ui.PlayerControlView
import com.example.iptv.R
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.ui.player.*
import com.google.common.collect.ImmutableList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LiveInfoTest {
    @Test fun compactInfoUsesActualFormatsAndRestoresFocusTimeoutAndPlaybackIntent() {
        val host = Robolectric.buildActivity(Activity::class.java).setup()
        MutablePlayback().use { scene ->
            val view = TvPlayerView(host.get())
            host.get().setContentView(view)
            view.player = scene.player; view.isLiveChannel = true
            view.channel = M3UChannel("News 4K", "Live", null, "https://provider.example/live/private/password/1.ts", null, null, null, false, "101")
            repeat(2) {
                view.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, 960, 540); shadowOf(Looper.getMainLooper()).idle()
            }
            view.openControls()
            val button = view.findViewById<Button>(R.id.playback_info)
            assertTrue(button.isShown); assertTrue(button.requestFocus()); button.performClick()
            val popup = shadowOf(host.get().application).latestPopupWindow
            assertTrue(popup.isShowing); assertTrue(popup.width <= view.width * .6); assertTrue(popup.height < view.height / 2)
            val text = popup.contentView as TextView
            assertTrue(text.text.contains("Video: Unknown")); assertTrue(text.text.contains("Audio: Unknown"))
            assertFalse(text.text.contains("provider.example")); assertFalse(text.text.contains("password"))
            assertTrue(text.text.contains("Number: 101"))
            val controls = view.findViewById<PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
            assertEquals(0, controls.showTimeoutMs)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2)); assertTrue(popup.isShowing); assertTrue(view.isControllerFullyVisible)
            val video = Format.Builder().setSampleMimeType("video/avc").setWidth(1920).setHeight(1080).build()
            val audio = Format.Builder().setSampleMimeType("audio/mpeg-L2").setChannelCount(2).setSampleRate(48000).build()
            scene.tracks = Tracks(ImmutableList.of(
                Tracks.Group(TrackGroup("video", video), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true)),
                Tracks.Group(TrackGroup("audio", audio), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true))))
            scene.changed()
            assertTrue(text.text.contains("1920 × 1080")); assertTrue(text.text.contains("MP2 · 2 ch · 48000 Hz"))
            assertFalse(text.text.contains("3840"))
            val image = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888); controls.draw(Canvas(image))
            assertEquals(0, android.graphics.Color.alpha(image.getPixel(300, 40)))
            assertEquals(20_000L, scene.position); assertTrue(scene.playing); assertTrue(scene.seeks.isEmpty())
            assertTrue(view.closeControls()); assertFalse(popup.isShowing); assertTrue(button.hasFocus())
            assertEquals(PlayerViewModel.OVERLAY_MS.toInt(), controls.showTimeoutMs)
            assertTrue(view.isControllerFullyVisible)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4)); assertFalse(view.isControllerFullyVisible)
            view.isLiveChannel = false; assertEquals(View.GONE, button.visibility)
            view.player = null
        }
        host.pause().stop().destroy()
    }
    @Test fun infoSharesInactivityExpiryAndForcedHideNeverReopensControls() {
        val host = Robolectric.buildActivity(Activity::class.java).setup()
        MutablePlayback().use { scene ->
            val view = TvPlayerView(host.get())
            host.get().setContentView(view); view.player = scene.player; view.isLiveChannel = true
            repeat(2) {
                view.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, 960, 540); shadowOf(Looper.getMainLooper()).idle()
            }
            val button = view.findViewById<Button>(R.id.playback_info)
            view.openControls(); button.performClick()
            val popup = shadowOf(host.get().application).latestPopupWindow
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            (popup.contentView as TextView).dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_DOWN))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2500))
            assertTrue("Interaction extends the shared inactivity period", popup.isShowing)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertFalse(popup.isShowing); assertFalse(view.isControllerFullyVisible)
            assertEquals(20_000L, scene.position); assertTrue(scene.playing); assertTrue(scene.seeks.isEmpty())
            view.openControls(); button.performClick()
            val forced = shadowOf(host.get().application).latestPopupWindow
            view.hideController()
            assertFalse(forced.isShowing)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4)); assertFalse(view.isControllerFullyVisible)
            view.openControls(); button.performClick()
            val released = shadowOf(host.get().application).latestPopupWindow
            view.player = null; assertFalse(released.isShowing)
        }
        host.pause().stop().destroy()
    }
}
