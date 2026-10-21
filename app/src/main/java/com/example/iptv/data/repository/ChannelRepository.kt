package com.example.iptv.data.repository

import com.example.iptv.data.model.M3UChannel

interface ChannelRepository {
    suspend fun getChannels(serverUrl: String, username: String, password: String): Result<List<M3UChannel>>
}
