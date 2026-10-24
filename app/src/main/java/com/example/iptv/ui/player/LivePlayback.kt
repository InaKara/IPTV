package com.example.iptv.ui.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.example.iptv.data.model.M3UChannel
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/** Only reinterpret the configured provider's exact authenticated live route. */
internal fun liveHlsAlternative(channel: M3UChannel, server: String, user: String, pass: String): String? {
    if (channel.isVod || user.isEmpty() || pass.isEmpty()) return null
    val base = (if ("://" in server) server else "https://$server").toHttpUrlOrNull() ?: return null
    val original = channel.streamUrl.toHttpUrlOrNull() ?: return null
    if (base.username.isNotEmpty() || base.password.isNotEmpty() || base.query != null || base.fragment != null ||
        original.username.isNotEmpty() || original.password.isNotEmpty() || original.query != null || original.fragment != null ||
        original.scheme != base.scheme || original.host != base.host || original.port != base.port) return null
    val prefix = base.pathSegments.dropLastWhile { it.isEmpty() }
    if (original.pathSegments.take(prefix.size) != prefix) return null
    val path = original.pathSegments.drop(prefix.size).let { if (it.firstOrNull() == "live") it.drop(1) else it }
    if (path.size != 3 || path[0] != user || path[1] != pass) return null
    val id = path[2].removeSuffix(".ts")
    if (id.isEmpty() || !id.all { it in '0'..'9' }) return null
    return base.newBuilder().apply {
        encodedPath("/")
        prefix.forEach { addPathSegment(it) }
        addPathSegment("live").addPathSegment(user).addPathSegment(pass).addPathSegment("$id.m3u8")
    }.build().toString()
}

@androidx.annotation.OptIn(UnstableApi::class)
internal class PlaybackRoute(val channel: M3UChannel, alternative: String?) {
    val initialItem: MediaItem = MediaItem.Builder().setUri(alternative ?: channel.streamUrl)
        .apply { if (alternative != null) setMimeType(MimeTypes.APPLICATION_M3U8) }.build()
    var usingAlternative = alternative != null
        private set

    fun fallback(): MediaItem? {
        if (!usingAlternative) return null
        usingAlternative = false
        return MediaItem.fromUri(channel.streamUrl)
    }

    fun rejectsTimeline(timeline: Timeline, index: Int): Boolean {
        if (!usingAlternative || timeline.isEmpty || index !in 0 until timeline.windowCount) return false
        val window = timeline.getWindow(index, Timeline.Window())
        return !window.isPlaceholder && !window.isLive
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun canSeekPlayback(player: Player, isVod: Boolean): Boolean =
    (isVod || player.isCurrentMediaItemLive) && player.isCurrentMediaItemSeekable && player.duration > 0 &&
        player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)

@androidx.annotation.OptIn(UnstableApi::class)
internal fun liveDefaultPosition(player: Player): Long? {
    if (!player.isCurrentMediaItemLive || player.currentTimeline.isEmpty || player.duration <= 0) return null
    val index = player.currentMediaItemIndex
    if (index !in 0 until player.currentTimeline.windowCount) return null
    val window = player.currentTimeline.getWindow(index, Timeline.Window())
    return window.defaultPositionMs.takeIf { !window.isPlaceholder && it >= 0 }?.coerceAtMost(player.duration)
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun canGoLive(player: Player): Boolean = liveDefaultPosition(player) != null &&
    player.isCommandAvailable(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)

@androidx.annotation.OptIn(UnstableApi::class)
internal fun seekPlaybackBy(player: Player, offset: Long, isVod: Boolean): Boolean {
    val target = seekTarget(player.currentPosition, offset, player.duration, canSeekPlayback(player, isVod)) ?: return false
    player.seekTo(target)
    return true
}

/** The controller and media keys use the same current-window policy. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun guardedPlayback(engine: Player, isVod: Boolean): Player = object : ForwardingPlayer(engine) {
    override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon().apply {
        if (!canSeekPlayback(engine, isVod)) removeAll(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD)
        if (!isVod && !canGoLive(engine)) remove(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
    }.build()
    override fun isCommandAvailable(command: Int) = availableCommands.contains(command)
    override fun getSeekBackIncrement() = PlayerViewModel.DEDICATED_SEEK_MS
    override fun getSeekForwardIncrement() = PlayerViewModel.DEDICATED_SEEK_MS
    override fun seekTo(positionMs: Long) {
        val target = seekTarget(0, positionMs, engine.duration, canSeekPlayback(engine, isVod)) ?: return
        engine.seekTo(target)
    }
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex == engine.currentMediaItemIndex) seekTo(positionMs)
    }
    override fun seekBack() { seekPlaybackBy(engine, -PlayerViewModel.DEDICATED_SEEK_MS, isVod) }
    override fun seekForward() { seekPlaybackBy(engine, PlayerViewModel.DEDICATED_SEEK_MS, isVod) }
    override fun seekToDefaultPosition() {
        if (isVod || canGoLive(engine)) engine.seekToDefaultPosition()
    }
    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        if (mediaItemIndex == engine.currentMediaItemIndex) seekToDefaultPosition()
    }
}

/** At most one immediate recovery per pause, with a cooldown for repeated stream failures. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class LiveWindowRecovery {
    private var lastAttempt: Long? = null
    fun paused() { lastAttempt = null }
    fun recover(player: Player, error: PlaybackException, isVod: Boolean, now: Long): Boolean {
        if (isVod || error.errorCode != PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW || !canGoLive(player)) return false
        if (lastAttempt?.let { now - it < 30_000 } == true) return false
        lastAttempt = now
        player.seekToDefaultPosition()
        player.prepare() // Retains the caller's current play/pause intent.
        return true
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun handlePlaybackFailure(player: Player, error: PlaybackException, route: PlaybackRoute,
    recovery: LiveWindowRecovery, now: Long, onRecovered: () -> Unit): Boolean {
    if (recovery.recover(player, error, route.channel.isVod, now)) {
        onRecovered()
        return true
    }
    val original = route.fallback() ?: return false
    player.setMediaItem(original)
    player.prepare()
    return true
}

internal fun liveLagText(lagMs: Long?): String {
    if (lagMs == null) return "—"
    val seconds = lagMs.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.ROOT, "-%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.ROOT, "-%02d:%02d", seconds / 60, seconds % 60)
}
