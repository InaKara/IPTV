package com.example.iptv.data.storage

import com.example.iptv.data.model.ChannelTextSharing
import com.example.iptv.data.model.M3UChannel
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

/** A fresh reader owns its small pools. Gson still handles the unchanged snapshot schema. */
internal fun snapshotReaderGson(): Gson = GsonBuilder()
    .registerTypeAdapterFactory(SharedChannelAdapterFactory()).create()

private class SharedChannelAdapterFactory : TypeAdapterFactory {
    private val text = ChannelTextSharing()

    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (type.rawType != M3UChannel::class.java) return null
        val delegate = gson.getDelegateAdapter(this, type)
        return object : TypeAdapter<T>() {
            override fun read(reader: JsonReader): T? {
                val value = delegate.read(reader) ?: return null
                val channel = value as M3UChannel
                // Leave malformed required fields for the existing whole-snapshot validation.
                val name: String? = channel.name
                val group: String? = channel.groupTitle
                val stream: String? = channel.streamUrl
                if (name == null || group == null || stream == null) return value
                val sharedGroup = text.group(group)
                val sharedAgent = text.userAgent(channel.userAgent)
                val sharedName = text.tvgName(name, channel.tvgName)
                if (sharedGroup === group && sharedAgent === channel.userAgent && sharedName === channel.tvgName) return value
                // Replace one entry while streaming, never retain a second full channel graph.
                @Suppress("UNCHECKED_CAST")
                return channel.copy(groupTitle = sharedGroup, userAgent = sharedAgent, tvgName = sharedName) as T
            }

            override fun write(writer: JsonWriter, value: T?) = delegate.write(writer, value)
        }
    }
}
