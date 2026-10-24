package com.example.iptv.ui.player

import com.example.iptv.data.model.M3UChannel
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class LiveSourcePolicyTest {
    @Test fun providerCandidateIsAccountBoundAndPreservesCanonicalChannel() {
        val base = "https://provider.invalid/base/"
        val user = "alice"
        val pass = "p/a ss"
        fun url(vararg path: String) = base.toHttpUrl().newBuilder().apply {
            path.forEach { addPathSegment(it) }
        }.build().toString()
        val channel = M3UChannel("Synthetic", "Group", null, url(user, pass, "0042.ts"), "FixtureAgent", "epg", "Name", false)
        val expected = url("live", user, pass, "0042.m3u8")
        fun resolve(c: M3UChannel = channel, server: String = base, u: String = user, p: String = pass) =
            liveHlsAlternative(c, server, u, p)
        assertEquals(expected, resolve())
        assertEquals(expected, resolve(channel.copy(streamUrl = url("live", user, pass, "0042.ts"))))
        assertEquals(expected, resolve(channel.copy(streamUrl = url(user, pass, "0042"))))
        assertEquals(url(user, pass, "0042.ts"), channel.streamUrl)
        assertNull(resolve(channel.copy(isVod = true)))
        assertNull(resolve(channel.copy(streamUrl = expected)))
        assertNull(resolve(channel.copy(streamUrl = url("movie", user, pass, "0042.ts"))))
        assertNull(resolve(channel.copy(streamUrl = url(user, pass, "movie.ts"))))
        assertNull(resolve(channel.copy(streamUrl = channel.streamUrl + "?token=synthetic")))
        assertNull(resolve(channel.copy(streamUrl = channel.streamUrl + "#fragment")))
        assertNull(resolve(channel.copy(streamUrl = channel.streamUrl.replace("provider.invalid", "external.invalid"))))
        assertNull(resolve(channel.copy(streamUrl = channel.streamUrl.replace("https:", "http:"))))
        assertNull(resolve(channel.copy(streamUrl = channel.streamUrl.replace("/base/", "/base-other/"))))
        assertNull(resolve(u = "another")); assertNull(resolve(p = "another"))
        assertNull(resolve(server = "https://provider.invalid:8443/base/"))
        assertNull(resolve(server = "https://embedded:credentials@provider.invalid/base/"))
        assertNull(resolve(server = "invalid address")); assertNull(resolve(u = ""))
    }
}
