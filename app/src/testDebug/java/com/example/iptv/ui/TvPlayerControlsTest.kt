package com.example.iptv.ui

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.iptv.ui.player.TvPlayerView
import java.lang.reflect.Proxy
import java.time.Duration
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
class TvPlayerControlsTest {
    private fun key(view: TvPlayerView, code: Int, repeat: Int = 0) {
        view.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, repeat))
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    @Test fun nativeSettingsRowsHaveRemoteFocusAndReadableAmberContrast() {
        val host = Robolectric.buildActivity(Activity::class.java).setup()
        val container = android.widget.LinearLayout(host.get()).apply { orientation = android.widget.LinearLayout.VERTICAL }
        host.get().setContentView(container)
        try {
            val row = host.get().layoutInflater.inflate(androidx.media3.ui.R.layout.exo_styled_settings_list_item, container, false)
            val choice = host.get().layoutInflater.inflate(androidx.media3.ui.R.layout.exo_styled_sub_settings_list_item, container, false)
            val title = row.findViewById<android.widget.TextView>(androidx.media3.ui.R.id.exo_main_text)
            title.text = "Audio"
            row.findViewById<android.widget.TextView>(androidx.media3.ui.R.id.exo_sub_text).text = "Automatic"
            assertNotNull(row.findViewById<View>(androidx.media3.ui.R.id.exo_icon))
            val option = choice.findViewById<android.widget.TextView>(androidx.media3.ui.R.id.exo_text)
            option.text = "Automatic"
            assertNotNull(choice.findViewById<View>(androidx.media3.ui.R.id.exo_check))
            container.addView(row); container.addView(choice)
            container.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY))
            container.layout(0, 0, 240, 160)
            assertTrue(row.requestFocus())
            assertEquals(TvPlayerView.DARK, title.currentTextColor)
            val image = Bitmap.createBitmap(row.width, row.height, Bitmap.Config.ARGB_8888)
            row.draw(Canvas(image))
            assertEquals(TvPlayerView.AMBER, image.getPixel(3, 3))
            assertSame(choice, row.focusSearch(View.FOCUS_DOWN))
            assertTrue(choice.requestFocus())
            assertFalse(row.hasFocus())
            assertEquals(android.graphics.Color.WHITE, title.currentTextColor)
            assertEquals(TvPlayerView.DARK, option.currentTextColor)
        } finally { host.pause().stop().destroy() }
    }

    @Test fun menuFocusesAmberControlAndVisibleArrowsDoNotSwitchChannels() {
        val host = Robolectric.buildActivity(Activity::class.java).setup()
        val engine = ExoPlayer.Builder(host.get()).build().apply { playWhenReady = true }
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            if (method.name == "getPlaybackState") Player.STATE_READY
            else if (method.name == "isCommandAvailable" && args!![0] == Player.COMMAND_GET_TIMELINE) false
            else method.invoke(engine, *(args ?: emptyArray()))
        } as Player
        val view = TvPlayerView(host.get())
        var next = 0; var previous = 0; val seeks = mutableListOf<Long>()
        view.nextChannel = { next++ }; view.previousChannel = { previous++ }; view.seek = { seeks.add(it) }
        host.get().setContentView(view)
        view.player = player
        view.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 960, 540)
        try {
            assertFalse(view.isControllerFullyVisible)
            key(view, KeyEvent.KEYCODE_DPAD_UP); key(view, KeyEvent.KEYCODE_DPAD_DOWN)
            key(view, KeyEvent.KEYCODE_DPAD_LEFT); key(view, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            assertEquals(1, next); assertEquals(1, previous)
            assertEquals(listOf(-30000L, 10000L), seeks)
            assertFalse(view.isControllerFullyVisible)
            key(view, KeyEvent.KEYCODE_MENU)
            view.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 960, 540)
            assertTrue(view.isControllerFullyVisible)
            val play = view.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)
            assertTrue("Enabled native Play/Pause must receive Menu focus", play.hasFocus())
            val image = Bitmap.createBitmap(play.width, play.height, Bitmap.Config.ARGB_8888)
            play.draw(Canvas(image))
            assertEquals(TvPlayerView.AMBER, image.getPixel(2, 2))
            key(view, KeyEvent.KEYCODE_DPAD_CENTER)
            assertFalse(engine.playWhenReady) // OK activates the native Pause button once.
            key(view, KeyEvent.KEYCODE_DPAD_UP); key(view, KeyEvent.KEYCODE_DPAD_DOWN)
            assertEquals(1, next); assertEquals(1, previous)
            key(view, KeyEvent.KEYCODE_BACK)
            assertFalse(view.isControllerFullyVisible)
            assertFalse(view.closeControls()) // The enclosing Back handler can now exit.
            key(view, KeyEvent.KEYCODE_DPAD_CENTER)
            assertTrue(engine.playWhenReady)
            assertFalse(view.isControllerFullyVisible)
        } finally { view.player = null; engine.release(); host.pause().stop().destroy() }
    }

    @Test fun timeoutResetsOnInteractionAndHiddenRepeatsDoNotRevealController() {
        val host = Robolectric.buildActivity(Activity::class.java).setup()
        val player = ExoPlayer.Builder(host.get()).build()
        val view = TvPlayerView(host.get())
        host.get().setContentView(view); view.player = player
        try {
            key(view, KeyEvent.KEYCODE_MENU)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2000))
            assertTrue(view.isControllerFullyVisible)
            key(view, KeyEvent.KEYCODE_DPAD_UP)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2000))
            assertTrue(view.isControllerFullyVisible)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100))
            assertFalse(view.isControllerFullyVisible)
            key(view, KeyEvent.KEYCODE_DPAD_UP, 2)
            assertFalse(view.isControllerFullyVisible)
        } finally { view.player = null; player.release(); host.pause().stop().destroy() }
    }
}
