package com.example.iptv.ui.player

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player

/** Owns only this player's view flag; no device settings or wake-lock permissions are changed. */
internal class PlaybackDisplay(private val view: View, private val player: Player, private val lifecycle: Lifecycle) :
    Player.Listener, LifecycleEventObserver, AutoCloseable {
    private val previous = view.keepScreenOn
    init { player.addListener(this); lifecycle.addObserver(this); update() }

    private fun update() {
        view.keepScreenOn = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && player.playerError == null &&
            (player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING))
    }

    override fun onEvents(player: Player, events: Player.Events) = update()
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) = update()
    override fun close() {
        player.removeListener(this)
        lifecycle.removeObserver(this)
        view.keepScreenOn = previous
    }
}
