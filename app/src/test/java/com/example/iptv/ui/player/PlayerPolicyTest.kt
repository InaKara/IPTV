package com.example.iptv.ui.player

import org.junit.Assert.*
import org.junit.Test

class PlayerPolicyTest {
    @Test fun seekRequiresCapabilityAndKnownDuration() {
        assertNull(seekTarget(30, 10, -1, true))
        assertNull(seekTarget(30, 10, 0, true))
        assertNull(seekTarget(30, 10, 100, false))
    }
    @Test fun allSeekDirectionsClampIncludingOverflow() {
        assertEquals(0L, seekTarget(5, -30, 100, true))
        assertEquals(100L, seekTarget(95, 30, 100, true))
        assertEquals(100L, seekTarget(95, Long.MAX_VALUE, 100, true))
        assertEquals(0L, seekTarget(5, Long.MIN_VALUE, 100, true))
        assertEquals(60L, seekTarget(30, 30, 100, true))
    }
}
