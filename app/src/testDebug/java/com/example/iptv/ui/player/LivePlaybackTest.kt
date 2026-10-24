package com.example.iptv.ui.player

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.FlagSet
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistTracker
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import com.example.iptv.data.model.M3UChannel
import java.lang.reflect.Proxy
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class MutablePlayback : AutoCloseable {
    val engine = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
    var live = true
    var seekable = true
    var duration = 60_000L
    var defaultPosition = 45_000L
    var position = 20_000L
    var known = true
    var placeholder = false
    var windowStart = 0L
    var playing = true
    var prepares = 0
    var defaultSeeks = 0
    val seeks = mutableListOf<Long>()
    val items = mutableListOf<MediaItem>()
    val listeners = mutableListOf<Player.Listener>()
    val item = MediaItem.fromUri("https://fixture.invalid/live.m3u8")
    val timeline: Timeline get() = if (!known) Timeline.EMPTY else object : androidx.media3.exoplayer.source.ForwardingTimeline(
        SinglePeriodTimeline(C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET,
            if (duration == C.TIME_UNSET) C.TIME_UNSET else duration * 1000,
            windowStart * 1000, defaultPosition * 1000, seekable, live, true,
            null, item, if (live) MediaItem.LiveConfiguration.UNSET else null)
    ) {
        override fun getWindow(index: Int, window: Timeline.Window, projection: Long): Timeline.Window =
            super.getWindow(index, window, projection).also { it.isPlaceholder = placeholder }
    }
    val commands get() = engine.availableCommands.buildUpon().addAll(Player.COMMAND_GET_TIMELINE,
        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD,
        Player.COMMAND_SEEK_TO_DEFAULT_POSITION).build()
    val player: Player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
        when (method.name) {
            "getCurrentTimeline" -> timeline
            "getCurrentMediaItemIndex", "getCurrentPeriodIndex" -> 0
            "getCurrentMediaItem" -> item
            "isCurrentMediaItemLive" -> live
            "isCurrentMediaItemSeekable" -> seekable
            "getDuration" -> duration
            "getCurrentPosition", "getContentPosition" -> position
            "getBufferedPosition", "getContentBufferedPosition" -> duration
            "getPlayWhenReady", "isPlaying" -> playing
            "getPlaybackState" -> Player.STATE_READY
            "getAvailableCommands" -> commands
            "isCommandAvailable" -> commands.contains(args!![0] as Int)
            "addListener" -> { listeners.add(args!![0] as Player.Listener); null }
            "removeListener" -> { listeners.remove(args!![0] as Player.Listener); null }
            "seekTo" -> { position = args!!.last() as Long; seeks.add(position); null }
            "seekToDefaultPosition" -> { position = defaultPosition; defaultSeeks++; null }
            "setMediaItem" -> { items.add(args!![0] as MediaItem); null }
            "prepare" -> { prepares++; null }
            "pause" -> { playing = false; null }
            "play" -> { playing = true; null }
            else -> method.invoke(engine, *(args ?: emptyArray()))
        }
    } as Player
    fun changed() {
        val events = Player.Events(FlagSet.Builder().addAll(Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_AVAILABLE_COMMANDS_CHANGED, Player.EVENT_POSITION_DISCONTINUITY).build())
        listeners.toList().forEach { it.onEvents(player, events) }
    }
    override fun close() { engine.release() }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LivePlaybackTest {
    @Test fun nativeSeekCommandsClampMovingLiveWindowsAndRetainVodGuards() {
        MutablePlayback().use { scene ->
            val live = guardedPlayback(scene.player, false)
            assertEquals(10000L, live.seekBackIncrement); assertEquals(10000L, live.seekForwardIncrement)
            live.seekBack(); assertEquals(10000L, scene.position)
            live.seekForward(); assertEquals(20000L, scene.position)
            live.seekTo(Long.MAX_VALUE); assertEquals(60000L, scene.position)
            scene.duration = 30000; scene.defaultPosition = 15000; scene.windowStart = 40000
            live.seekBack(); assertEquals(20000L, scene.position) // Uses the new window's bounds.
            live.seekTo(-10); assertEquals(0L, scene.position)
            live.seekToDefaultPosition(); assertEquals(15000L, scene.position)
            scene.seekable = false
            assertFalse(live.isCommandAvailable(Player.COMMAND_SEEK_BACK))
            live.seekForward(); assertEquals(15000L, scene.position)
            scene.known = false
            assertFalse(live.isCommandAvailable(Player.COMMAND_SEEK_TO_DEFAULT_POSITION))
            live.seekToDefaultPosition(); assertEquals(1, scene.defaultSeeks)
            scene.live = false; scene.seekable = true; scene.duration = C.TIME_UNSET
            val vod = guardedPlayback(scene.player, true)
            assertFalse(vod.isCommandAvailable(Player.COMMAND_SEEK_BACK))
            scene.duration = 90000
            assertTrue(vod.isCommandAvailable(Player.COMMAND_SEEK_FORWARD))
            assertTrue(seekPlaybackBy(scene.player, 30000, true)); assertEquals(45000L, scene.position)
            assertFalse(live.isCommandAvailable(Player.COMMAND_SEEK_FORWARD)) // Seekable file is not a live window.
        }
    }

    @Test fun expiredWindowRecoveryAndOneTimeFallbackRetainPlaybackIntent() {
        MutablePlayback().use { scene ->
            val channel = M3UChannel("Fixture", "Group", null, "https://fixture.invalid/alice/pass/42.ts", null, "id", null, false)
            val route = PlaybackRoute(channel, "https://fixture.invalid/live/alice/pass/42.m3u8")
            val recovery = LiveWindowRecovery()
            val behind = PlaybackException("synthetic", null, PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW)
            var notices = 0
            assertTrue(handlePlaybackFailure(scene.player, behind, route, recovery, 0) { notices++ })
            assertEquals(1, notices); assertEquals(1, scene.defaultSeeks); assertTrue(scene.playing)
            assertTrue(route.usingAlternative)
            scene.playing = false; recovery.paused()
            assertTrue(handlePlaybackFailure(scene.player, behind, route, recovery, 100) { notices++ })
            assertFalse(scene.playing); assertEquals(2, notices)
            // Repeated immediate failure cannot spin recovery forever; exact original gets one attempt.
            assertTrue(handlePlaybackFailure(scene.player, behind, route, recovery, 200) { notices++ })
            assertFalse(route.usingAlternative)
            assertEquals(channel.streamUrl, scene.items.single().localConfiguration!!.uri.toString())
            assertSame(channel, route.channel)
            assertFalse(handlePlaybackFailure(scene.player, behind, route, recovery, 300) { notices++ })
            val unrelated = PlaybackException("synthetic", null, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
            assertFalse(handlePlaybackFailure(scene.player, unrelated, route, recovery, 40000) { notices++ })
            val failedSource = PlaybackRoute(channel, "https://fixture.invalid/42.m3u8")
            assertTrue(handlePlaybackFailure(scene.player, unrelated, failedSource, LiveWindowRecovery(), 40000) { notices++ })
            assertEquals(channel.streamUrl, scene.items.last().localConfiguration!!.uri.toString())
            assertFalse(scene.playing)
            assertFalse(handlePlaybackFailure(scene.player, unrelated, failedSource, recovery, 40001) { notices++ })
            assertTrue(recovery.recover(scene.player, behind, false, 40100)) // A later expiry can recover again.
            val vod = PlaybackRoute(channel.copy(isVod = true), null)
            assertFalse(handlePlaybackFailure(scene.player, behind, vod, recovery, 40000) { notices++ })
            scene.placeholder = true
            val candidate = PlaybackRoute(channel, "https://fixture.invalid/42.m3u8")
            assertFalse(candidate.rejectsTimeline(scene.timeline, 0))
            scene.placeholder = false; scene.live = false
            assertTrue(candidate.rejectsTimeline(scene.timeline, 0)) // ENDLIST/VOD is not accepted as live.
        }
    }

    @Test fun realHlsSourcePublishesRollingSeekWindowAndRejectsInvalidManifest() {
        fun manifest(sequence: Int) = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:10\n#EXT-X-MEDIA-SEQUENCE:$sequence\n" +
            (sequence until sequence + 6).joinToString("") { "#EXTINF:10,\nsegment$it.ts\n" }
        val bytes = AtomicReference(manifest(1).toByteArray())
        var tracker: HlsPlaylistTracker? = null
        // A playlist tracker reuses its DataSource. Read the changing fixture on every open.
        val data = DataSource.Factory { object : DataSource {
            private var opened: ByteArrayDataSource? = null
            override fun addTransferListener(listener: TransferListener) = Unit
            override fun open(spec: DataSpec): Long = ByteArrayDataSource(bytes.get()).also { opened = it }.open(spec)
            override fun read(buffer: ByteArray, offset: Int, length: Int) = opened!!.read(buffer, offset, length)
            override fun getUri(): Uri? = opened?.uri
            override fun close() { opened?.close(); opened = null }
        } }
        fun source(): HlsMediaSource = HlsMediaSource.Factory(data)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(0))
            .setPlaylistTrackerFactory { data, policy, parser ->
                DefaultHlsPlaylistTracker(data, policy, parser).also { tracker = it }
            }.createMediaSource(MediaItem.fromUri("https://fixture.invalid/live.m3u8"))
        fun await(condition: () -> Boolean) {
            val deadline = System.nanoTime() + 3_000_000_000
            while (!condition() && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5)
            }
            assertTrue("Local HLS loader did not deliver expected result", condition())
        }
        val timelines = mutableListOf<Timeline>()
        val caller = MediaSource.MediaSourceCaller { _, timeline -> timelines.add(timeline) }
        val hls = source()
        try {
            hls.prepareSource(caller, null, PlayerId.UNSET)
            await { timelines.isNotEmpty() }
            val first = timelines.last().getWindow(0, Timeline.Window())
            assertTrue(first.isLive); assertTrue(first.isSeekable); assertEquals(60000L, first.durationMs)
            assertTrue(first.defaultPositionMs in 0..60000)
            bytes.set(manifest(3).toByteArray())
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
            tracker!!.refreshPlaylist(Uri.parse("https://fixture.invalid/live.m3u8"))
            await { timelines.any { it.getWindow(0, Timeline.Window()).positionInFirstPeriodMs > first.positionInFirstPeriodMs } }
            assertEquals(60000L, timelines.last().getWindow(0, Timeline.Window()).durationMs)
        } finally { hls.releaseSource(caller) }
        bytes.set("not an HLS manifest".toByteArray())
        val invalid = source()
        var failure: java.io.IOException? = null
        try {
            invalid.prepareSource(caller, null, PlayerId.UNSET)
            await {
                try { invalid.maybeThrowSourceInfoRefreshError() } catch (error: java.io.IOException) { failure = error }
                failure != null
            }
            assertNotNull(failure)
        } finally { invalid.releaseSource(caller) }
    }
}
