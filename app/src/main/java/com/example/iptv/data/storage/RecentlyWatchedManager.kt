package com.example.iptv.data.storage

import android.content.Context
import com.example.iptv.data.model.M3UChannel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Singleton
class RecentlyWatchedManager @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var loaded = false
    private var writable = true
    private var scope = ""
    private var playlist = emptyMap<String, M3UChannel>()
    private var accounts = emptyMap<String, List<String>>()
    private val _recentChannels = MutableStateFlow<List<M3UChannel>>(emptyList())
    val recentChannels = _recentChannels.asStateFlow()

    suspend fun activate(server: String, username: String, channels: List<M3UChannel>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!loaded) {
                val stored = prefs.getString(KEY, null)
                if (stored != null) {
                    try { accounts = RecentHistoryCodec.decode(stored) }
                    catch (_: Exception) { writable = false } // Preserve corrupt/future data unchanged.
                }
                loaded = true
            }
            val address = server.trim().trimEnd('/').let { if ("://" in it) it else "https://$it" }
            val normalized = address.toHttpUrlOrNull()?.toString() ?: address
            scope = MessageDigest.getInstance("SHA-256")
                .digest((normalized + "\n" + username).toByteArray()).joinToString("") { "%02x".format(it) }
            playlist = channels.associateBy { it.streamUrl }
            if (scope !in accounts && writable) {
                val legacy = prefs.getString(LEGACY_KEY, null)
                val ownerServer = prefs.getString("server_url", null)?.trim()?.trimEnd('/')?.let {
                    (if ("://" in it) it else "https://$it").toHttpUrlOrNull()?.toString()
                }
                val ownsLegacy = prefs.getString("username", null) == username && ownerServer == normalized &&
                    !prefs.getBoolean("recent_legacy_migrated", false)
                val migrated = try {
                    legacy?.takeIf { ownsLegacy }?.let { RecentHistoryCodec.migrateLegacy(it, playlist.keys) }.orEmpty()
                } catch (_: Exception) { emptyList() }
                accounts = accounts + (scope to migrated)
                persist()
                if (migrated.isNotEmpty()) prefs.edit().putBoolean("recent_legacy_migrated", true).apply()
            }
            publish()
        }
    }

    suspend fun addChannel(channel: M3UChannel, libraryReference: String? = null, owner: String? = null) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (scope.isEmpty() || (owner != null && scope != owner)) return@withLock
            val reference = if (channel.streamUrl in playlist) channel.streamUrl else libraryReference ?: return@withLock
            if (reference != channel.streamUrl) playlist = playlist + (reference to channel)
            accounts = accounts + (scope to RecentHistoryCodec.prepend(reference, accounts[scope].orEmpty()))
            persist()
            publish()
        }
    }

    suspend fun references(owner: String): Set<String> = withContext(Dispatchers.IO) {
        mutex.withLock { if (scope == owner) accounts[scope].orEmpty().toSet() else emptySet() }
    }
    suspend fun resolve(owner: String, entries: Map<String, M3UChannel>) = withContext(Dispatchers.IO) {
        mutex.withLock { if (scope == owner) { playlist = playlist + entries; publish() } }
    }

    suspend fun getRecentChannels(): List<M3UChannel> = withContext(Dispatchers.IO) {
        mutex.withLock { accounts[scope].orEmpty().mapNotNull { playlist[it] } }
    }

    private fun persist() {
        if (writable) prefs.edit().putString(KEY, RecentHistoryCodec.encode(accounts)).apply()
    }

    private fun publish() { _recentChannels.value = accounts[scope].orEmpty().mapNotNull { playlist[it] } }

    private companion object {
        const val KEY = "recent_history_v1"
        const val LEGACY_KEY = "recent_channels"
    }
}
