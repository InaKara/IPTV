package com.example.iptv.data.repository

import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.parser.M3UParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ChannelRepositoryImpl @Inject constructor(
    private val parser: M3UParser
) : ChannelRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun getChannels(serverUrl: String, username: String, password: String): Result<List<M3UChannel>> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = serverUrl.trim().trimEnd('/')
                val cleanServerUrl = if (baseUrl.startsWith("http")) baseUrl else "http://$baseUrl"
                val url = "$cleanServerUrl/get.php?username=$username&password=$password&type=m3u_plus"

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Server error: ${response.code}"))
                }

                val body = response.body ?: return@withContext Result.failure(IOException("Empty response body"))

                val channels = body.byteStream().use { inputStream ->
                    parser.parse(inputStream)
                }
                Result.success(channels)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
