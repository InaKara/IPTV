package com.example.iptv.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.iptv.data.model.M3UChannel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentlyWatchedManager @Inject constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("aypi_tivi_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_RECENT_CHANNELS = "recent_channels"
        private const val MAX_RECENT = 20
    }

    fun addChannel(channel: M3UChannel) {
        val recent = getRecentChannels().toMutableList()
        
        // Remove if already exists (to move to front)
        recent.removeAll { it.streamUrl == channel.streamUrl }
        
        // Add to front
        recent.add(0, channel)
        
        // Keep only last MAX_RECENT
        val trimmed = recent.take(MAX_RECENT)
        
        // Save to preferences
        val json = gson.toJson(trimmed)
        prefs.edit().putString(KEY_RECENT_CHANNELS, json).apply()
    }

    fun getRecentChannels(): List<M3UChannel> {
        val json = prefs.getString(KEY_RECENT_CHANNELS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<M3UChannel>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_RECENT_CHANNELS).apply()
    }
}
