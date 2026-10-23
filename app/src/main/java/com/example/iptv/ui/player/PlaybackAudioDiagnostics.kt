package com.example.iptv.ui.player

import androidx.media3.common.C
import androidx.media3.common.Tracks

/** Only a fixed codec vocabulary and counts leave the player; no IDs, labels, URLs or causes. */
internal fun audioTrackDiagnostics(tracks: Tracks): List<String> {
    val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val count = audio.sumOf { it.length }
    val supported = audio.sumOf { group -> (0 until group.length).count { group.isTrackSupported(it) } }
    val selected = audio.sumOf { group -> (0 until group.length).count { group.isTrackSelected(it) } }
    return listOf("tracks=$count supported=$supported selected=$selected") + audio.flatMap { group ->
        (0 until group.length).map { index ->
            val format = group.getTrackFormat(index)
            val codec = when (format.sampleMimeType) {
                "audio/mpeg-L2" -> "mp2"
                "audio/mpeg" -> "mp3"
                "audio/mp4a-latm" -> "aac"
                "audio/ac3" -> "ac3"
                "audio/eac3", "audio/eac3-joc" -> "eac3"
                "audio/vnd.dts", "audio/vnd.dts.hd" -> "dts"
                "audio/opus" -> "opus"
                "audio/vorbis" -> "vorbis"
                "audio/flac" -> "flac"
                "audio/raw" -> "pcm"
                else -> "other"
            }
            "codec=$codec channels=${format.channelCount.coerceIn(-1, 64)} " +
                "rate=${format.sampleRate.coerceIn(-1, 384000)} supported=${if (group.isTrackSupported(index)) 1 else 0} " +
                "selected=${if (group.isTrackSelected(index)) 1 else 0}"
        }
    }.take(16)
}
