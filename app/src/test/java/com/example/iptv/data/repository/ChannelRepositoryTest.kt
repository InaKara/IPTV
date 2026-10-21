package com.example.iptv.data.repository

import com.example.iptv.data.parser.M3UParser
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class ChannelRepositoryTest {
    @Test fun queryParametersRoundTripReservedAndUnicodeCharacters() {
        val url = playlistUrl("provider.example:8080/", "a+汉 &=#", "a&b#c?= %")
        assertEquals("https", url.scheme)
        assertEquals("/get.php", url.encodedPath)
        assertEquals("a+汉 &=#", url.queryParameter("username"))
        assertEquals("a&b#c?= %", url.queryParameter("password"))
        assertEquals("m3u_plus", url.queryParameter("type"))
    }

    @Test fun explicitHttpRemainsExplicit() { assertEquals("http", playlistUrl("http://provider.example/", "u", "p").scheme) }

    @Test fun rejectsCredentialBearingBaseAddress() {
        assertThrows(IllegalArgumentException::class.java) { playlistUrl("https://u:p@provider.example", "u", "p") }
        assertThrows(IllegalArgumentException::class.java) { playlistUrl("https://provider.example?token=x", "u", "p") }
    }

    @Test fun errorResponseClosesAndConnectionCanBeReused() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setBody("denied"))
            server.enqueue(MockResponse().setBody("#EXTM3U\n#EXTINF:-1,News\nhttps://provider.example/1"))
            val client = OkHttpClient()
            try {
                val repository = ChannelRepositoryImpl(M3UParser(), client)
                assertTrue(repository.getChannels(server.url("/").toString(), "u", "p").isFailure)
                assertEquals("News", repository.getChannels(server.url("/").toString(), "u", "p").getOrThrow().single().name)
                server.takeRequest()
                assertEquals(1, server.takeRequest().sequenceNumber)
            } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
        }
    }

    @Test fun cancellationStopsPendingCall() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val client = OkHttpClient()
            try {
                val task = launch { ChannelRepositoryImpl(M3UParser(), client).getChannels(server.url("/").toString(), "u", "p") }
                withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
                withTimeout(2000) { task.cancelAndJoin() }
                assertTrue(task.isCancelled)
                withTimeout(2000) {
                    while (client.dispatcher.runningCallsCount() != 0) delay(10)
                }
            } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
        }
    }
}
