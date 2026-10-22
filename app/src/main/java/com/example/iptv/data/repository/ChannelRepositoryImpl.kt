package com.example.iptv.data.repository

import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.parser.M3UParser
import com.example.iptv.diagnostics.StartupTimings
import com.example.iptv.diagnostics.StartupTimings.Phase
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal fun playlistUrl(server: String, username: String, password: String): HttpUrl {
    val address = server.trim().trimEnd('/')
    val base = (if ("://" in address) address else "https://$address").toHttpUrl()
    require(base.username.isEmpty() && base.password.isEmpty() && base.query == null && base.fragment == null) {
        "Use a provider base address without credentials or query parameters"
    }
    return base.newBuilder().addPathSegment("get.php")
        .addQueryParameter("username", username)
        .addQueryParameter("password", password)
        .addQueryParameter("type", "m3u_plus").build()
}

@Singleton
class ChannelRepositoryImpl @Inject constructor(
    private val parser: M3UParser,
    private val client: OkHttpClient,
    private val startup: StartupTimings = StartupTimings()
) : ChannelRepository {
    override suspend fun getChannels(serverUrl: String, username: String, password: String): Result<List<M3UChannel>> {
        val timing = startup.current()
        val request = try {
            Request.Builder().url(playlistUrl(serverUrl, username, password)).build()
        } catch (error: IllegalArgumentException) {
            return Result.failure(error)
        }
        return suspendCancellableCoroutine { continuation ->
            val waiting = timing?.now() ?: 0L
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    timing?.record(Phase.PROVIDER_WAIT, waiting)
                    continuation.resume(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    timing?.record(Phase.PROVIDER_WAIT, waiting)
                    val receiving = timing?.now() ?: 0L
                    // OkHttp's worker owns parsing and closes every response, including errors.
                    val result = try {
                        response.use {
                            if (!it.isSuccessful) throw IOException("Provider HTTP status ${it.code}")
                            val body = it.body ?: throw IOException("Empty playlist response")
                            Result.success(parser.parse(body.byteStream()))
                        }
                    } catch (error: Exception) {
                        Result.failure(error)
                    } finally {
                        // The parser consumes the streaming body: network reads and parsing overlap.
                        timing?.record(Phase.DOWNLOAD_PARSE, receiving)
                    }
                    continuation.resume(result)
                }
            })
        }
    }
}
