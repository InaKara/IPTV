package com.example.iptv.data.model

/** Owned by one load, never by the parser, repository or process. Values are never normalized. */
internal class ChannelTextSharing {
    private val groups = HashMap<String, String>()
    private val agents = HashMap<String, String>()

    fun group(value: String): String = share(value, groups, GROUP_LIMIT)
    fun userAgent(value: String?): String? = value?.let { share(it, agents, AGENT_LIMIT) }
    fun tvgName(name: String, value: String?): String? = if (name == value) name else value

    private fun share(value: String, pool: MutableMap<String, String>, limit: Int): String {
        pool[value]?.let { return it }
        if (pool.size < limit) pool[value] = value
        return value
    }

    companion object {
        // Bound auxiliary references even for playlists with a different value on every entry.
        const val GROUP_LIMIT = 512
        const val AGENT_LIMIT = 32
    }
}
