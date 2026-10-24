package com.example.iptv.ui.player

import android.app.Application
import android.os.Looper
import androidx.media3.common.FlagSet
import androidx.media3.common.Player
import com.example.iptv.data.library.WatchProgress
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class VodProgressBindingTest {
    @Test fun periodicPauseEndAndReleasePersistRealStateWhileLiveNeverWrites() {
        var position = 0L; var duration = 100_000L; var playing = false; var state = Player.STATE_BUFFERING
        var seekable = true
        val listeners = mutableListOf<Player.Listener>()
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "getCurrentPosition" -> position; "getDuration" -> duration; "isPlaying" -> playing
                "getPlaybackState" -> state; "isCurrentMediaItemSeekable" -> seekable
                "addListener" -> { listeners.add(args!![0] as Player.Listener); null }
                "removeListener" -> { listeners.remove(args!![0] as Player.Listener); null }
                "seekTo" -> { position = args!!.last() as Long; null }
                else -> error("Unexpected ${method.name}")
            }
        } as Player
        val writes = mutableListOf<List<Any>>()
        val binding = VodProgressBinding(player, true, WatchProgress(40_000, 100_000, false, true)) { p, d, s, e -> writes.add(listOf(p, d, s, e)) }
        fun event(code: Int) = listeners.toList().forEach { it.onEvents(player, Player.Events(FlagSet.Builder().add(code).build())) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10)); assertTrue(writes.isEmpty())
        state = Player.STATE_READY; playing = true; event(Player.EVENT_PLAYBACK_STATE_CHANGED)
        assertEquals(40_000, position)
        position = 55_000; shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertEquals(55_000L, writes.last()[0])
        playing = false; position = 60_000; event(Player.EVENT_PLAY_WHEN_READY_CHANGED)
        assertEquals(60_000L, writes.last()[0])
        state = Player.STATE_ENDED; event(Player.EVENT_PLAYBACK_STATE_CHANGED); assertEquals(true, writes.last()[3])
        position = 62_000; binding.close(); assertEquals(62_000L, writes.last()[0]); assertTrue(listeners.isEmpty())
        val count = writes.size
        binding.close(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30)); assertEquals(count, writes.size)
        VodProgressBinding(player, false, null) { _, _, _, _ -> fail("Live must not write VOD progress") }.use {
            playing = true; shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20)); assertTrue(listeners.isEmpty())
        }
        state = Player.STATE_READY; seekable = false; duration = -1; position = 0
        VodProgressBinding(player, true, WatchProgress(40_000, 100_000, false, true)) { _, _, _, _ -> fail("Unknown timeline must not replace progress") }.use {
            event(Player.EVENT_PLAYBACK_STATE_CHANGED); assertEquals(0L, position)
        }
    }
}
