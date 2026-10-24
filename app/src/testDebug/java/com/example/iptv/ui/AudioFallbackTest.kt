package com.example.iptv.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import com.example.iptv.ui.player.audioFallbackRenderers
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioFallbackTest {
    private inline fun <reified T> listener(): T = Proxy.newProxyInstance(
        T::class.java.classLoader, arrayOf(T::class.java)
    ) { _, _, _ -> null } as T

    @Test fun actualPlayerFactoryKeepsPlatformAudioBeforeSingleSoftwareFallback() {
        val renderers = audioFallbackRenderers(RuntimeEnvironment.getApplication()).createRenderers(
            Handler(Looper.getMainLooper()), listener(), listener(), listener(), listener()
        )
        val audio = renderers.filter { it.trackType == C.TRACK_TYPE_AUDIO }
        assertEquals(2, audio.size)
        assertTrue(audio[0] is MediaCodecAudioRenderer)
        assertTrue(audio[1] is FfmpegAudioRenderer)
        assertEquals(1, renderers.count { it is FfmpegAudioRenderer })
        assertTrue(renderers.any { it.trackType == C.TRACK_TYPE_VIDEO })
    }
}
