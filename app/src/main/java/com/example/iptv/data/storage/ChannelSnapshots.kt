package com.example.iptv.data.storage

import android.content.Context
import android.util.AtomicFile
import com.example.iptv.data.model.M3UChannel
import com.example.iptv.data.repository.playlistUrl
import com.example.iptv.ui.browser.privateReference
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ChannelAccount(val server: String, val username: String, val password: String) {
    val complete: Boolean get() = server.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    // Canonical query encoding also makes credential changes invalidate their old snapshot.
    val key: String get() = privateReference(playlistUrl(server, username, password).toString())
    override fun toString() = "ChannelAccount(redacted)"
}

data class ChannelSnapshot(val channels: List<M3UChannel>, val downloadedAt: Long) {
    fun stale(now: Long): Boolean = downloadedAt <= 0 || downloadedAt > now || now - downloadedAt > MAX_AGE_MS
    companion object { const val MAX_AGE_MS = 24 * 60 * 60 * 1000L }
}

interface ChannelSnapshots {
    suspend fun read(account: ChannelAccount): ChannelSnapshot?
    suspend fun write(account: ChannelAccount, snapshot: ChannelSnapshot)
}

/** No-backup app storage, streamed JSON and atomic replacement; never logs authenticated data. */
@Singleton
class FileChannelSnapshots @Inject constructor(@ApplicationContext context: Context) : ChannelSnapshots {
    internal val directory = File(context.noBackupFilesDir, "playlists")
    private val gson = Gson()
    private val mutex = Mutex()
    private class Envelope(@SerializedName("version") val version: Int,
        @SerializedName("account") val account: String?, @SerializedName("downloadedAt") val downloadedAt: Long,
        @SerializedName("channels") val channels: List<M3UChannel?>?)

    private fun usable(channel: M3UChannel): Boolean {
        // Gson can produce null for missing required fields; an empty category is valid metadata.
        val group: String? = channel.groupTitle
        return !channel.name.isNullOrBlank() && group != null && !channel.streamUrl.isNullOrBlank()
    }

    override suspend fun read(account: ChannelAccount): ChannelSnapshot? = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val value = AtomicFile(File(directory, account.key)).openRead().bufferedReader(Charsets.UTF_8).use {
                    snapshotReaderGson().fromJson(it, Envelope::class.java)
                }
                require(value.version == 1 && value.account == account.key)
                val channels = value.channels?.map { requireNotNull(it) } ?: return@withLock null
                require(channels.isNotEmpty() && channels.all(::usable))
                ChannelSnapshot(channels, value.downloadedAt)
            } catch (_: Exception) { currentCoroutineContext().ensureActive(); null }
        }
    }

    override suspend fun write(account: ChannelAccount, snapshot: ChannelSnapshot) = withContext(Dispatchers.IO) {
        require(snapshot.channels.isNotEmpty() && snapshot.channels.all(::usable))
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            check(directory.isDirectory || directory.mkdirs())
            val file = AtomicFile(File(directory, account.key))
            val stream = file.startWrite()
            try {
                val writer = stream.bufferedWriter(Charsets.UTF_8)
                gson.toJson(Envelope(1, account.key, snapshot.downloadedAt, snapshot.channels), writer)
                writer.flush()
                currentCoroutineContext().ensureActive()
                file.finishWrite(stream)
            } catch (error: Exception) { file.failWrite(stream); throw error }
        }
    }
}
