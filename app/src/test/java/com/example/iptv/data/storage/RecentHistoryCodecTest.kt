package com.example.iptv.data.storage

import org.junit.Assert.*
import org.junit.Test

class RecentHistoryCodecTest {
    @Test fun schemaRoundTripsAndDeduplicatesReferences() {
        val result = RecentHistoryCodec.decode(RecentHistoryCodec.encode(mapOf("scope" to listOf("a", "a", "b"))))
        assertEquals(listOf("a", "b"), result["scope"])
    }

    @Test fun movingToFrontRetainsLimit() {
        val history = (1..20).map { "$it" }
        assertEquals(listOf("10") + history.filterNot { it == "10" }, RecentHistoryCodec.prepend("10", history))
        assertEquals(20, RecentHistoryCodec.prepend("new", history).size)
        assertFalse("20" in RecentHistoryCodec.prepend("new", history))
    }

    @Test fun migrationUsesOnlyValidReferencesInActivePlaylist() {
        val json = """[{"streamUrl":"a","oldField":{"unsupported":"value"}},{"name":"missing"},{"streamUrl":{}},{"streamUrl":"other-account"},{"streamUrl":"a"}]"""
        assertEquals(listOf("a"), RecentHistoryCodec.migrateLegacy(json, setOf("a")))
    }

    @Test fun futureAndMalformedSchemasAreRejectedInsteadOfSilentlyReset() {
        listOf("""{"version":2,"accounts":{}}""", """{"version":1,"accounts":{"s":[null]}}""", "{").forEach {
            assertThrows(Exception::class.java) { RecentHistoryCodec.decode(it) }
        }
    }
}
