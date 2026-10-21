package com.example.iptv.data.parser

import org.junit.Assert.*
import org.junit.Test

class M3UParserEdgeTest {
    private fun parse(info: String, url: String = "https://provider.example/live/1") =
        "#EXTM3U\n#EXTINF:-1 $info\n# comment\n\n$url".byteInputStream().use { M3UParser().parse(it).single() }

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
