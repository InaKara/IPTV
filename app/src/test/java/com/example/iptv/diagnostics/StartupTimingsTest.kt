package com.example.iptv.diagnostics

import com.example.iptv.data.model.M3UChannel
import com.example.iptv.diagnostics.StartupTimings.Outcome
import com.example.iptv.diagnostics.StartupTimings.Phase
import org.junit.Assert.*
import org.junit.Test

class StartupTimingsTest {
    private fun channel() = M3UChannel("private name", "private group", "https://private.example/logo",
        "https://private.example/private-account/private-password", null, null, null, false)

    @Test fun disabledDiagnosticsCreateNoAttemptOrOutput() {
        val lines = mutableListOf<String>()
        val timing = StartupTimings(false, output = lines::add)
        assertNull(timing.begin())
        assertNull(timing.current())
        assertTrue(lines.isEmpty())
    }

    @Test fun phaseDurationsAndTotalUseTheSameClockWithoutAddingOverlappingWork() {
        var clock = 0L
        val lines = mutableListOf<String>()
        val timing = StartupTimings(true, { clock }, lines::add)
        val attempt = timing.begin()!!
        clock = 3_000_000_000L
        attempt.record(Phase.PROVIDER_WAIT, 0)
        val receiving = attempt.now()
        clock = 5_000_000_000L
        attempt.record(Phase.DOWNLOAD_PARSE, receiving)
        attempt.playlist = listOf(channel())
        clock = 6_000_000_000L
        attempt.finish(Outcome.BROWSE_READY)
        assertEquals(listOf("attempt=1 phase=connect ms=0", "attempt=1 phase=provider_wait ms=3000",
            "attempt=1 phase=download_parse ms=2000", "attempt=1 phase=browse_ready ms=6000 channels=1"), lines)
        assertNull(timing.current())
        assertNull(attempt.playlist)
    }

    @Test fun retryCannotMixPlaylistsOrCompleteANewerAttempt() {
        val lines = mutableListOf<String>()
        val timing = StartupTimings(true, { 0L }, lines::add)
        val old = timing.begin()!!
        val oldList = listOf(channel())
        old.playlist = oldList
        val current = timing.begin()!!
        val currentList = oldList.toList()
        current.playlist = currentList
        assertNull(timing.forPlaylist(oldList))
        assertSame(current, timing.forPlaylist(currentList))
        old.finish(Outcome.FAILED)
        assertSame(current, timing.current())
        current.finish(Outcome.BROWSE_READY)
        current.finish(Outcome.CANCELLED)
        old.record(Phase.HISTORY, 0)
        assertEquals(1, lines.count { "phase=cancelled" in it })
        assertEquals(1, lines.count { "phase=browse_ready" in it })
        assertEquals(4, lines.size)
    }

    @Test fun outputContainsOnlyFixedPhasesAndNumericMetadata() {
        val lines = mutableListOf<String>()
        val timing = StartupTimings(true, { 42_000_000L }, lines::add)
        val attempt = timing.begin()!!
        attempt.playlist = listOf(channel())
        Phase.values().forEach { attempt.record(it, 0) }
        attempt.finish(Outcome.FAILED)
        val safeLine = Regex("attempt=\\d+ phase=(connect|provider_wait|download_parse|history|grouping|browse_ready|failed|cancelled) ms=\\d+( channels=\\d+)?")
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.all { safeLine.matches(it) })
        assertTrue(lines.none { "private" in it || "://" in it })
    }
}
