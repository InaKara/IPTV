package com.example.iptv.ui

import org.junit.Assert.*
import org.junit.Test

class SessionPolicyTest {
    @Test fun duplicateValuesDoNotAffectPositionOrWrapping() {
        assertEquals(2, wrappedChannelIndex(1, 1, 3))
        assertEquals(0, wrappedChannelIndex(2, 1, 3))
        assertEquals(2, wrappedChannelIndex(0, -1, 3))
    }
    @Test fun invalidOrEmptySelectionDoesNotJumpToAnArbitraryChannel() {
        assertNull(wrappedChannelIndex(-1, 1, 3))
        assertNull(wrappedChannelIndex(0, 1, 0))
        assertNull(wrappedChannelIndex(3, -1, 3))
    }
}
