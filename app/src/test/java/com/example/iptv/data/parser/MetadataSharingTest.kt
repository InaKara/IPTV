package com.example.iptv.data.parser

import com.example.iptv.data.model.ChannelTextSharing
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.ui.browser.BrowserCatalog
import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class MetadataSharingTest {
    private fun playlist(rows: List<M3UChannel>) = buildString {
        appendLine("#EXTM3U")
        rows.forEach { row ->
            append("#EXTINF:-1 group-title=\"${row.groupTitle}\"")
            listOf("user-agent" to row.userAgent, "tvg-name" to row.tvgName, "tvg-id" to row.tvgId,
                "tvg-logo" to row.logoUrl, "tvg-chno" to row.providerNumber).forEach { (key, value) ->
                if (value != null) append(" $key=\"$value\"")
            }
            appendLine(",${row.name}")
            appendLine(row.streamUrl)
        }
    }

    private fun parse(parser: M3UParser, rows: List<M3UChannel>) =
        playlist(rows).byteInputStream().use { parser.parse(it) }

    @Test fun parserSharesExactRepeatedMetadataAndPreservesCanonicalOccurrences() {
        val base = M3UChannel("News, HD", " TR ", "https://logo.example/icon.svg?q=1",
            "https://provider.example/live/1?token=synthetic#part", " Agent ", "guide-id", "News, HD", false, "007")
        val rows = listOf(base, base.copy(), base.copy(groupTitle = "tr", userAgent = "agent", tvgName = "news, HD"),
            base.copy(groupTitle = "", userAgent = "", tvgName = ""),
            base.copy(groupTitle = "   ", userAgent = null, tvgName = null, logoUrl = null, tvgId = null, providerNumber = null),
            base.copy(name = "Film Ω", groupTitle = "VOD", tvgName = "Film Ω", streamUrl = "https://provider.example/movie/1.mp4", isVod = true))
        val actual = parse(M3UParser(), rows)
        assertEquals(rows, actual)
        assertSame(actual[0].groupTitle, actual[1].groupTitle)
        assertSame(actual[0].userAgent, actual[1].userAgent)
        actual.filter { it.name == it.tvgName }.forEach { assertSame(it.name, it.tvgName) }
        val expectedIds = BrowserCatalog.create(rows).channels.map { it.id }
        assertEquals(expectedIds, BrowserCatalog.create(actual).channels.map { it.id })
        assertNotEquals(expectedIds[0], expectedIds[1]) // Keep identical stream occurrences distinct.
    }

    @Test fun parserPoolsAreBoundedAndIndependentAcrossLoadsAndFailures() {
        val parser = M3UParser()
        val rows = (0..ChannelTextSharing.GROUP_LIMIT).map { index ->
            M3UChannel("Name $index", "Group $index", null, "https://provider.example/live/$index",
                "Agent $index", null, "Name $index", false)
        }.let { it + it.first() + it.last() }
        val first = parse(parser, rows)
        assertEquals(rows, first)
        assertSame(first[0].groupTitle, first[first.lastIndex - 1].groupTitle)
        assertSame(first[0].userAgent, first[first.lastIndex - 1].userAgent)
        assertNotSame(first[ChannelTextSharing.GROUP_LIMIT].groupTitle, first.last().groupTitle)
        assertNotSame(first[ChannelTextSharing.GROUP_LIMIT].userAgent, first.last().userAgent)
        // Agent capacity is independently smaller than category capacity.
        val agents = rows.take(ChannelTextSharing.AGENT_LIMIT + 1).let { it + it.last() }
        val agentResult = parse(parser, agents)
        assertNotSame(agentResult[ChannelTextSharing.AGENT_LIMIT].userAgent, agentResult.last().userAgent)
        assertSame(agentResult[ChannelTextSharing.AGENT_LIMIT].groupTitle, agentResult.last().groupTitle)

        val bytes = playlist(rows).toByteArray()
        val interrupted = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (available() < bytes.size / 2) throw IOException("Synthetic interrupted load")
                return super.read(buffer, offset, minOf(length, 127))
            }
        }
        try { interrupted.use { parser.parse(it) }; fail("Interrupted input accepted") }
        catch (_: IOException) { }
        val second = parse(parser, rows)
        assertEquals(rows, second)
        assertNotSame(first[0].groupTitle, second[0].groupTitle)
        assertNotSame(first[0].userAgent, second[0].userAgent)
        assertSame(second[0].groupTitle, second[second.lastIndex - 1].groupTitle)
    }
}
