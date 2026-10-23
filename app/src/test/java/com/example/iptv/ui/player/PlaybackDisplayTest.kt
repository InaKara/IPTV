package com.example.iptv.ui.player

import android.app.Application
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.FlagSet
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class PlaybackDisplayTest {
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun displayStaysAwakeOnlyForForegroundRequestedPlaybackAndCleansUp() {
        var play = true
        var state = Player.STATE_BUFFERING
        var error: PlaybackException? = null
        var suppressed = false
        var listener: Player.Listener? = null
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "getPlayWhenReady" -> play
                "isPlaying" -> play && state == Player.STATE_READY && !suppressed
                "getPlaybackState" -> state
                "getPlayerError" -> error
                "addListener" -> { listener = args!![0] as Player.Listener; null }
                "removeListener" -> { assertSame(listener, args!![0]); listener = null; null }
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        val view = View(RuntimeEnvironment.getApplication())
        owner.registry.currentState = Lifecycle.State.CREATED
        val binding = PlaybackDisplay(view, player, owner.lifecycle)
        val events = Player.Events(FlagSet.Builder().build())
        fun changed() = listener!!.onEvents(player, events)
        assertFalse(view.keepScreenOn)
        owner.registry.currentState = Lifecycle.State.STARTED
        assertTrue(view.keepScreenOn) // Initial buffering counts while play is requested.
        state = Player.STATE_READY; changed(); assertTrue(view.keepScreenOn)
        suppressed = true; changed(); assertFalse(view.keepScreenOn)
        suppressed = false; changed(); assertTrue(view.keepScreenOn)
        play = false; changed(); assertFalse(view.keepScreenOn)
        play = true; state = Player.STATE_BUFFERING; changed(); assertTrue(view.keepScreenOn)
        owner.registry.currentState = Lifecycle.State.CREATED; assertFalse(view.keepScreenOn)
        owner.registry.currentState = Lifecycle.State.STARTED; assertTrue(view.keepScreenOn)
        state = Player.STATE_ENDED; changed(); assertFalse(view.keepScreenOn)
        state = Player.STATE_READY; error = PlaybackException("synthetic", null, PlaybackException.ERROR_CODE_UNSPECIFIED)
        changed(); assertFalse(view.keepScreenOn)
        error = null; changed(); assertTrue(view.keepScreenOn)
        binding.close(); assertFalse(view.keepScreenOn); assertNull(listener)
        owner.registry.currentState = Lifecycle.State.CREATED
        owner.registry.currentState = Lifecycle.State.STARTED
        assertFalse(view.keepScreenOn)
    }
}
