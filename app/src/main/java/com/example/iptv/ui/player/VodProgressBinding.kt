package com.example.iptv.ui.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import com.example.iptv.data.library.WatchProgress

/** Captures real player state before release; queued disk writes can outlive the screen. */
internal class VodProgressBinding(private val player: Player, private val vod: Boolean,
    saved: WatchProgress?, private val save: (Long, Long, Boolean, Boolean) -> Unit) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private var pending = saved
    private var closed = false
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (player.playbackState == Player.STATE_READY) {
                pending?.let { progress ->
                    pending = null
                    val target = progress.resumePosition(player.duration, player.isCurrentMediaItemSeekable)
                    if (target > 0) player.seekTo(target)
                }
            }
            if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) || events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) capture()
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            if (closed) return
            if (player.isPlaying) capture()
            handler.postDelayed(this, 10_000)
        }
    }
    init { if (vod) { player.addListener(listener); handler.postDelayed(tick, 10_000) } }
    private fun capture() {
        if (!vod || closed || pending != null || (player.duration <= 0 && player.playbackState != Player.STATE_ENDED)) return
        save(player.currentPosition, player.duration, player.isCurrentMediaItemSeekable, player.playbackState == Player.STATE_ENDED)
    }
    override fun close() {
        if (closed) return
        capture(); closed = true
        handler.removeCallbacks(tick)
        if (vod) player.removeListener(listener)
    }
}
