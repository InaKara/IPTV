package com.example.iptv.ui.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl

/** Sample-buffer target only; indexes, decoders and app data still need separate heap headroom. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun playbackLoadControl(isVod: Boolean): DefaultLoadControl {
    if (!isVod) return DefaultLoadControl()
    return DefaultLoadControl.Builder()
        .setTargetBufferBytes(24 * 1024 * 1024)
        .setPrioritizeTimeOverSizeThresholds(false)
        .setBufferDurationsMs(10_000, 30_000,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
        .build()
}
