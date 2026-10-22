package com.example.iptv.data.parser

import org.junit.Assert.*
import org.junit.Test

class M3UParserEdgeTest {
    private fun parse(info: String, url: String = "https://provider.example/live/1") =
        "#EXTM3U\n#EXTINF:-1 $info\n# comment\n\n$url".byteInputStream().use { M3UParser().parse(it).single() }

    @Test fun providerNumbersAreOptionalTrimmedAndNeverInferred() {
        assertEquals("007.2", parse("tvg-chno=\" 007.2 \" tvg-id=\"other\",News").providerNumber)
        assertEquals("9", parse("tvg-chno=9,News").providerNumber)
        assertNull(parse("tvg-chno=\"   \" tvg-id=\"42\",News").providerNumber)
        assertNull(parse("tvg-id=\"42\",News").providerNumber)
        assertEquals("News", parse("tvg-chno=\"5\",News").name)
    }

    @Test fun preservesNameCommasAndQuotedMetadata() {
        val channel = parse("group-title=\"News, World\" tvg-name=\"metadata, name\",News, International")
        assertEquals("News, International", channel.name)
        assertEquals("News, World", channel.groupTitle)
        assertEquals("metadata, name", channel.tvgName)
    }

    @Test fun nameAttributesDoNotOverwriteMetadata() {
        assertEquals("News", parse("group-title=\"News\",Label group-title=\"Fake\"").groupTitle)
    }

    @Test fun blankNameUsesFallback() { assertEquals("Unknown Channel", parse(",").name) }

    @Test fun queryAndFragmentDoNotHideVodExtension() {
        assertTrue(parse(",Film", "https://provider.example/video.MP4?token=sample#part").isVod)
    }

    @Test fun queryDoesNotMakeLiveStreamVod() {
        assertFalse(parse(",Live", "https://provider.example/live/1?redirect=/movie/clip.mp4").isVod)
    }
}
