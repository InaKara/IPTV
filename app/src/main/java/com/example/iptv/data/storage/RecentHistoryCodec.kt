package com.example.iptv.data.storage

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Stable persistence schema: account hashes and stream references, never serialized UI/domain objects. */
internal object RecentHistoryCodec {
    const val VERSION = 1
    const val LIMIT = 20

    fun decode(json: String): Map<String, List<String>> {
        val root = JsonParser.parseString(json).asJsonObject
        require(root["version"]?.asInt == VERSION) { "Unsupported history version" }
        val accounts = root.getAsJsonObject("accounts") ?: error("Missing history accounts")
        return accounts.entrySet().associate { (scope, values) ->
            require(values.isJsonArray) { "Invalid history account" }
            scope to values.asJsonArray.map { value ->
                require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Invalid stream reference" }
                value.asString.also { require(it.isNotBlank()) }
            }.distinct().take(LIMIT)
        }
    }

    fun encode(accounts: Map<String, List<String>>): String = JsonObject().apply {
        addProperty("version", VERSION)
        add("accounts", JsonObject().apply {
            accounts.forEach { (scope, urls) ->
                add(scope, JsonArray().apply { urls.distinct().take(LIMIT).forEach { add(it) } })
            }
        })
    }.toString()

    fun migrateLegacy(json: String, availableUrls: Set<String>): List<String> =
        JsonParser.parseString(json).asJsonArray.mapNotNull { item ->
            // Read only the stable URL field; malformed/obsolete channel fields cannot create invalid models.
            val url = item.takeIf { it.isJsonObject }?.asJsonObject?.get("streamUrl")
            url?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                ?.takeIf { it in availableUrls }
        }.distinct().take(LIMIT)

    fun prepend(url: String, history: List<String>): List<String> =
        (listOf(url) + history.filterNot { it == url }).take(LIMIT)
}
