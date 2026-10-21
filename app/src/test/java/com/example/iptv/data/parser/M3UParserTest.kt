package com.example.iptv.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class M3UParserTest {

    private val parser = M3UParser()

    @Test
    fun parse_validM3U_returnsCorrectChannels() {
        val input = """
            #EXTM3U
            #EXTINF:0 user-agent="Firefox" tvg-id="NULL" tvg-name="****TURK ULUSAL****" tvg-logo="" group-title="TR TR ULUSAL",****TURK ULUSAL****
            http://provider.example:8080/username/password/25597
            #EXTINF:0 user-agent="Firefox" tvg-id="TRT.1.HD.tr" tvg-name="TR⚡TRT 1 HD" tvg-logo="https://logos.example/tvlogo/logo/Turkiye/trt-1-tr.png" group-title="TR TR ULUSAL",TR⚡TRT 1 HD
            http://provider.example:8080/username/password/60
        """.trimIndent()

        val channels = input.byteInputStream().use { parser.parse(it) }

        assertEquals(2, channels.size)

        // Validate first channel
        assertEquals("****TURK ULUSAL****", channels[0].name)
        assertEquals("TR TR ULUSAL", channels[0].groupTitle)
        assertEquals("http://provider.example:8080/username/password/25597", channels[0].streamUrl)
        assertEquals("NULL", channels[0].tvgId)
        
        // Validate second channel
        assertEquals("TR⚡TRT 1 HD", channels[1].name)
        assertEquals("TR TR ULUSAL", channels[1].groupTitle)
        assertEquals("http://provider.example:8080/username/password/60", channels[1].streamUrl)
        assertEquals("TRT.1.HD.tr", channels[1].tvgId)
        assertEquals("https://logos.example/tvlogo/logo/Turkiye/trt-1-tr.png", channels[1].logoUrl)
    }
}
