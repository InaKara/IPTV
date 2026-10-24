package com.example.iptv.data.library

import com.example.iptv.data.model.M3UChannel
import com.example.iptv.ui.browser.BrowserCatalog
import org.junit.Assert.*
import org.junit.Test

class LibraryPolicyTest {
    private val account = LibraryAccount.create("https://provider.example", "u +&汉", "p /?&")
    private fun channel(url: String, group: String = "Films") = M3UChannel("Same name", group, null, url, "custom agent", "old", "old", true)
    @Test fun largeCatalogMatcherDoesNoConstructionScanAndCachesExactCandidates() {
        val movie = LibraryEntry(LibraryKind.MOVIE, "42", "Film")
        val target = com.example.iptv.ui.browser.BrowserChannel("preserved", channel(account.stream(movie)))
        val unrelated = target.copy(id = "unrelated", channel = channel(account.stream(movie.copy(id = "7"))))
        var allowTraversal = false
        var reads = 0
        val catalog = object : AbstractList<com.example.iptv.ui.browser.BrowserChannel>() {
            override val size = 100_000
            override fun get(index: Int): com.example.iptv.ui.browser.BrowserChannel {
                check(allowTraversal) { "Opening navigation must not traverse the large playlist" }
                reads++
                return if (index == 90_000) target else unrelated
            }
        }
        val matcher = LibraryIdentity(account, catalog)
        assertEquals(0, reads)
        allowTraversal = true
        assertEquals(target, matcher.resolve(movie))
        assertEquals(100_000, reads)
        allowTraversal = false
        assertEquals(target, matcher.resolve(movie))
        assertEquals(account.key(LibraryKind.MOVIE, "42"), matcher.progressKey(target.channel, target.id))
        assertEquals(100_000, reads)
        val encoded = target.copy(channel = target.channel.copy(streamUrl = target.channel.streamUrl.replace("/42.mp4", "/%34%32.mp4")))
        assertEquals(encoded, LibraryIdentity(account, listOf(encoded)).resolve(movie))
        assertNull(LibraryIdentity(account, listOf(target, encoded)).legacy(movie))
    }
    @Test fun encodedRoutesKeepTypesAccountsAndCredentialsSeparate() {
        val movie = LibraryEntry(LibraryKind.MOVIE, "42", "Title")
        val request = account.api("get_vod_info", "vod_id" to "42")
        assertEquals("u +&汉", request.queryParameter("username")); assertEquals("p /?&", request.queryParameter("password"))
        assertEquals("https", request.scheme)
        val rotated = LibraryAccount.create("https://provider.example/", "u +&汉", "replacement")
        assertEquals(account.owner, rotated.owner); assertNotEquals(account.cache, rotated.cache)
        assertNotEquals(account.key(LibraryKind.MOVIE, "42"), account.key(LibraryKind.EPISODE, "42"))
        assertNotEquals(account.key(LibraryKind.SERIES, "42"), account.key(LibraryKind.EPISODE, "42"))
        assertFalse(account.toString().contains(account.password))
        val legacy = channel(account.stream(movie))
        val catalog = BrowserCatalog.create(listOf(legacy))
        assertEquals(catalog.channels.single(), LibraryIdentity(account, catalog.channels).resolve(movie))
        assertThrows(IllegalArgumentException::class.java) { LibraryAccount.create("https://user:secret@provider.example", "u", "p") }
        assertThrows(IllegalArgumentException::class.java) { LibraryAccount.create("https://provider.example?token=x", "u", "p") }
        assertThrows(IllegalArgumentException::class.java) { account.stream(movie.copy(id = "../live/1")) }
    }
    @Test fun aliasesRequireUniqueExactRoutesAndNeverMergeNamesOrDuplicates() {
        val movie = LibraryEntry(LibraryKind.MOVIE, "42", "Same name")
        val legacy = channel(account.stream(movie))
        val catalog = BrowserCatalog.create(listOf(legacy, legacy, channel("https://other.example/movie/u/p/42.mp4")))
        val identity = LibraryIdentity(account, catalog.channels)
        assertNull(identity.legacy(movie))
        assertEquals(account.key(LibraryKind.MOVIE, "42"), identity.resolve(movie).id)
        assertEquals(catalog.channels.first().id, identity.progressKey(legacy, catalog.channels.first().id))
        val unrelated = LibraryIdentity(account, catalog.channels.drop(2))
        assertNull(unrelated.legacy(movie))
        assertNull(unrelated.route(channel(account.stream(movie).replace("https:", "http:"))))
        val otherUser = LibraryAccount.create("https://provider.example", "different", "p /?&")
        assertNull(LibraryIdentity(otherUser, catalog.channels).legacy(movie))
    }
    @Test fun streamedOptionalMetadataAndSelectedSeriesPreserveProviderLabels() {
        val rows = mutableListOf<LibraryEntry>()
        LibraryParser.listing("""[{"stream_id":1,"name":"Alpha","rating":"0","category_id":"2","is_adult":false},
            {"stream_id":"2","name":"Beta","rating":"8.4","added":"123","container_extension":"mkv","cover":null},
            {"stream_id":3,"name":"Missing","rating":"broken"}]""".byteInputStream(), LibraryKind.MOVIE, rows::add)
        assertEquals(listOf(null, 8.4, null), rows.map { it.rating })
        assertEquals("mkv", rows[1].extension); assertEquals(123L, rows[1].timestamp)
        val shows = mutableListOf<LibraryEntry>()
        LibraryParser.listing("""[{"series_id":10,"name":"Show","last_modified":"321","added":"999"}]""".byteInputStream(), LibraryKind.SERIES, shows::add)
        assertEquals(321L, shows.single().timestamp)
        val episodes = mutableListOf<LibraryEntry>()
        val details = LibraryParser.details("""{"info":{"plot":"A story","releaseDate":"2024-01-01","cast":"Person"},
            "episodes":{"Specials":[{"id":"91","title":"Pilot","info":{"duration":"00:42:00"}}],
            "2":[{"id":"92","title":"Return","episode_num":"7"}]}}""".byteInputStream(), "10", episodes::add)
        assertEquals("A story", details.synopsis); assertEquals("2024", details.year)
        assertEquals(listOf("Specials", "2"), episodes.map { it.season })
        assertEquals(listOf("", "7"), episodes.map { it.episode }); assertEquals("00:42:00", episodes[0].runtime)
        assertTrue(episodes.all { it.seriesId == "10" && it.kind == LibraryKind.EPISODE })
        assertThrows(Exception::class.java) { LibraryParser.listing("{\"user_info\":{}}".byteInputStream(), LibraryKind.MOVIE) {} }
        assertThrows(Exception::class.java) { LibraryParser.listing("[] false".byteInputStream(), LibraryKind.MOVIE) {} }
    }
    @Test fun progressThresholdsCompletionResetAndActualTimelineCapability() {
        val empty = WatchProgress()
        val short = WatchProgress.record(empty, 29_999, 100_000, true, false, 1)
        assertFalse(short.resumable)
        val continuing = WatchProgress.record(short, 30_000, 100_000, true, false, 2)
        assertTrue(continuing.resumable)
        assertEquals(20_000, continuing.resumePosition(20_000, true))
        assertEquals(0L, continuing.resumePosition(100_000, false))
        assertEquals(0L, continuing.resumePosition(-1, true))
        assertFalse(WatchProgress.record(empty, 94_999, 100_000, true, false, 3).watched)
        val watched = WatchProgress.record(empty, 95_000, 100_000, true, false, 3)
        assertTrue(watched.watched); assertFalse(watched.resumable)
        assertTrue(WatchProgress.record(watched, 10_000, 100_000, true, false, 4).watched)
        assertTrue(WatchProgress.record(empty, 5, 0, false, true, 5).watched)
        assertFalse(WatchProgress.record(empty, 40_000, 0, true, false, 6).resumable)
        assertEquals(0L, empty.resumePosition(100_000, true))
    }
}
