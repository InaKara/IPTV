package com.example.iptv.ui.player

import android.app.Application
import androidx.media3.common.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class PlaybackAudioDiagnosticsTest {
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun distinguishesAbsentUnsupportedAndSelectedAudioWithoutLeakingMetadata() {
        assertEquals(listOf("tracks=0 supported=0 selected=0"), audioTrackDiagnostics(Tracks.EMPTY))
        val track = TrackGroup(Format.Builder().setSampleMimeType("audio/mpeg-L2").setChannelCount(2).setSampleRate(48000)
            .setId("https://provider.example/private?password=synthetic").setLabel("private channel").build())
        val unsupported = Tracks(listOf(Tracks.Group(track, false, intArrayOf(C.FORMAT_UNSUPPORTED_TYPE), booleanArrayOf(false))))
        assertEquals(listOf("tracks=1 supported=0 selected=0", "codec=mp2 channels=2 rate=48000 supported=0 selected=0"),
            audioTrackDiagnostics(unsupported))
        val supported = Tracks(listOf(Tracks.Group(track, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true))))
        assertEquals("tracks=1 supported=1 selected=1", audioTrackDiagnostics(supported).first())
        assertTrue(audioTrackDiagnostics(supported).none { "private" in it || "provider" in it || "synthetic" in it })
        val unknown = TrackGroup(Format.Builder().setSampleMimeType("audio/private-secret").build())
        assertTrue(audioTrackDiagnostics(Tracks(listOf(Tracks.Group(unknown, false,
            intArrayOf(C.FORMAT_UNSUPPORTED_TYPE), booleanArrayOf(false))))).last().startsWith("codec=other "))
    }
}
