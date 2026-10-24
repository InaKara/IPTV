package com.example.iptv.ui.library

import com.example.iptv.data.library.WatchProgress
import org.junit.Assert.*
import org.junit.Test

class ResumeLabelTest {
    @Test fun elapsedHoursMinutesStayConsistentWithResumeEligibilityAndClamping() {
        assertEquals("00:00", resumeTime(30_000))
        assertEquals("00:59", resumeTime(3_599_999))
        assertEquals("01:00", resumeTime(3_600_000))
        assertEquals("01:23", resumeTime(4_980_000))
        assertEquals("123:45", resumeTime((123 * 60L + 45) * 60_000))
        assertEquals("00:00", resumeTime(-1))
        val progress = WatchProgress(4_980_000, 7_200_000, false, true)
        assertTrue(progress.resumable)
        assertEquals("01:23", resumeTime(progress.resumePosition(7_200_000, true)))
        assertEquals("01:00", resumeTime(progress.resumePosition(3_600_000, true)))
        assertEquals(0L, progress.resumePosition(7_200_000, false))
        assertFalse(progress.copy(watched = true).resumable)
    }
}
