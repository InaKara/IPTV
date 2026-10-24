package com.example.iptv.ui.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory

/** Use the narrow FFmpeg extension only after Android's own audio renderers. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun audioFallbackRenderers(context: Context): DefaultRenderersFactory =
    DefaultRenderersFactory(context)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
