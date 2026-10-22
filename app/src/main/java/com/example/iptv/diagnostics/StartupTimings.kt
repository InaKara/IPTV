package com.example.iptv.diagnostics

import com.example.iptv.data.model.M3UChannel
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Debug-only timings. The output API accepts no provider, account or exception strings. */
class StartupTimings(
    private val enabled: Boolean = false,
    private val clock: () -> Long = System::nanoTime,
    private val output: (String) -> Unit = {}
) {
    private val sequence = AtomicLong()
    private val active = AtomicReference<Attempt?>()

    fun begin(): Attempt? {
        if (!enabled) return null
        val attempt = Attempt(sequence.incrementAndGet(), clock, output) { active.compareAndSet(it, null) }
        active.getAndSet(attempt)?.finish(Outcome.CANCELLED)
        attempt.record(Phase.CONNECT, attempt.started)
        return attempt
    }

    fun current(): Attempt? = active.get()

    fun forPlaylist(channels: List<M3UChannel>): Attempt? = active.get()?.takeIf { it.playlist === channels }

    enum class Phase { CONNECT, PROVIDER_WAIT, DOWNLOAD_PARSE, HISTORY, GROUPING }
    enum class Outcome { BROWSE_READY, FAILED, CANCELLED }

    class Attempt internal constructor(
        private val id: Long,
        private val clock: () -> Long,
        private val output: (String) -> Unit,
        private val finished: (Attempt) -> Unit
    ) {
        val started = clock()
        @Volatile var playlist: List<M3UChannel>? = null
        private val complete = AtomicBoolean()

        fun now(): Long = clock()

        fun record(phase: Phase, since: Long) {
            if (!complete.get()) write(phase.name, since)
        }

        fun finish(outcome: Outcome) {
            if (complete.compareAndSet(false, true)) {
                write(outcome.name, started)
                finished(this)
                playlist = null
            }
        }

        private fun write(phase: String, since: Long) {
            val ms = TimeUnit.NANOSECONDS.toMillis((clock() - since).coerceAtLeast(0))
            val count = playlist?.size?.let { " channels=$it" }.orEmpty()
            output("attempt=$id phase=${phase.lowercase(Locale.ROOT)} ms=$ms$count")
        }
    }
}
