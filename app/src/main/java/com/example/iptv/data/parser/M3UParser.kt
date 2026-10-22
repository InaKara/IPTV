package com.example.iptv.data.parser

import com.example.iptv.data.model.M3UChannel
import java.util.regex.Pattern
import javax.inject.Inject

class M3UParser @Inject constructor() {

    // Regex pattern to extract attributes from #EXTINF line
    // Matches key="value" or key=value
    private val attributePattern = Pattern.compile("([a-zA-Z0-9\\-]+)=\"([^\"]*)\"|([a-zA-Z0-9\\-]+)=([^\\s]*)")

    fun parse(inputStream: java.io.InputStream): List<M3UChannel> {
        val channels = mutableListOf<M3UChannel>()
        val reader = inputStream.bufferedReader()

        var currentChannelBuilder: M3UChannelBuilder? = null

        reader.forEachLine { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("#EXTINF:")) {
                currentChannelBuilder = M3UChannelBuilder()

                // Parse attributes
                var quoted = false
                val separator = trimmedLine.indexOfFirst { char ->
                    if (char == '"') quoted = !quoted
                    char == ',' && !quoted
                }
                val metadata = if (separator >= 0) trimmedLine.substring(0, separator) else trimmedLine
                val matcher = attributePattern.matcher(metadata)
                while (matcher.find()) {
                    val key = matcher.group(1) ?: matcher.group(3)
                    val value = matcher.group(2) ?: matcher.group(4)

                    when (key) {
                        "tvg-id" -> currentChannelBuilder?.tvgId = value
                        "tvg-name" -> currentChannelBuilder?.tvgName = value
                        "tvg-chno" -> currentChannelBuilder?.providerNumber = value.trim().takeIf { it.isNotEmpty() }
                        "tvg-logo" -> currentChannelBuilder?.logoUrl = value
                        "group-title" -> currentChannelBuilder?.groupTitle = value
                        "user-agent" -> currentChannelBuilder?.userAgent = value
                    }
                }

                // Preserve commas in names and ignore commas inside quoted metadata.
                if (separator >= 0) {
                    trimmedLine.substring(separator + 1).trim().takeIf { it.isNotEmpty() }
                        ?.let { currentChannelBuilder?.name = it }
                }

            } else if (!trimmedLine.startsWith("#") && trimmedLine.isNotEmpty()) {
                // This line is the URL
                currentChannelBuilder?.let { builder ->
                    builder.streamUrl = trimmedLine
                    // VOD detection
                    builder.isVod = isVodContent(builder.groupTitle, builder.streamUrl)
                    channels.add(builder.build())
                }
                currentChannelBuilder = null
            }
        }

        return channels
    }

    private fun isVodContent(groupTitle: String, streamUrl: String): Boolean {
        val group = groupTitle.lowercase()
        if (VOD_KEYWORDS.any { group.contains(it) }) return true
        val url = streamUrl.substringBefore('?').substringBefore('#').lowercase()
        if (url.contains("/movie/") || url.contains("/series/") || url.contains("/vod/")) return true
        if (url.endsWith(".mp4") || url.endsWith(".mkv") || url.endsWith(".avi") || url.endsWith(".mov")) return true
        return false
    }

    private companion object {
        val VOD_KEYWORDS = listOf("vod", "movie", "series", "film", "on demand")
    }

    private class M3UChannelBuilder {
        var name: String = "Unknown Channel"
        var groupTitle: String = "Uncategorized"
        var logoUrl: String? = null
        var streamUrl: String = ""
        var userAgent: String? = null
        var tvgId: String? = null
        var tvgName: String? = null
        var isVod: Boolean = false
        var providerNumber: String? = null

        fun build(): M3UChannel {
            return M3UChannel(name, groupTitle, logoUrl, streamUrl, userAgent, tvgId, tvgName, isVod, providerNumber)
        }
    }
}
