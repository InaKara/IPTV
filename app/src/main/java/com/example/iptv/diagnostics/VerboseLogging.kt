package com.example.iptv.diagnostics

import android.content.Context
import android.util.Log
import com.example.iptv.BuildConfig
import com.example.iptv.data.storage.PREFERENCES_FILE
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class DiagnosticEvent { LIBRARY_OPEN, CACHE_READ, LIST_BEGIN, LIST_OK, LIST_FAILED, DETAIL_BEGIN, DETAIL_OK,
    DETAIL_FAILED, ALIAS_LINKS, PLAY_PREPARE, PLAY_STATE, AUDIO_CODEC_ERROR, AUDIO_SINK_ERROR, AUDIO_INITIALIZED }

/** Only typed events, numbers and tightly validated existing fixed records reach the sink. */
class DiagnosticGate(private val debug: Boolean, private val enabled: () -> Boolean,
    private val sink: (String, String) -> Unit) {
    fun event(event: DiagnosticEvent, durationMs: Long = 0, count: Int = 0) {
        if (debug && enabled()) sink("AypiDetail", "event=${event.name.lowercase(java.util.Locale.ROOT)} ms=${durationMs.coerceAtLeast(0)} count=${count.coerceAtLeast(0)}")
    }
    fun startup(record: String) {
        if (debug && enabled() && STARTUP.matches(record)) sink("AypiStartup", record)
    }
    fun audio(record: String) {
        if (debug && enabled() && (TRACKS.matches(record) || CODEC.matches(record))) sink("AypiAudio", record)
    }
    private companion object {
        val STARTUP = Regex("attempt=[0-9]+ phase=(connect|cache|provider_wait|download_parse|history|grouping|browse_ready|failed|cancelled) ms=[0-9]+( channels=[0-9]+)?")
        val TRACKS = Regex("tracks=[0-9]+ supported=[0-9]+ selected=[0-9]+")
        val CODEC = Regex("codec=(mp2|mp3|aac|ac3|eac3|dts|opus|vorbis|flac|pcm|other) channels=-?[0-9]+ rate=-?[0-9]+ supported=[01] selected=[01]")
    }
}

@Singleton
class VerboseLogging @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(BuildConfig.DEBUG && runCatching { prefs.getBoolean("verbose_logging", false) }.getOrDefault(false))
    val enabled = mutable.asStateFlow()
    val gate = DiagnosticGate(BuildConfig.DEBUG, { mutable.value }) { tag, record -> Log.i(tag, record); Unit }
    fun setEnabled(value: Boolean) {
        mutable.value = BuildConfig.DEBUG && value
        prefs.edit().putBoolean("verbose_logging", mutable.value).apply()
    }
}
