package com.example.iptv.data.storage

import android.content.Context
import com.example.iptv.ui.browser.GridDensity
import com.example.iptv.ui.browser.privateReference
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class BrowserPreferenceState(val density: GridDensity, val favorites: Set<String>, val account: String,
                                  val filterAdult: Boolean = true)

@Singleton
class BrowserPreferences @Inject constructor(
    @ApplicationContext context: Context,
    private val credentials: CredentialsManager
) {
    private val prefs = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var loaded = false
    private var writable = true
    private var accounts = emptyMap<String, Set<String>>()
    private var account = ""
    private var density = GridDensity.STANDARD
    private var filterAdult = true

    suspend fun load(): BrowserPreferenceState = withContext(Dispatchers.IO) {
        mutex.withLock {
            filterAdult = runCatching { prefs.getBoolean("filter_adult", true) }.getOrDefault(true)
            density = runCatching { GridDensity.valueOf(prefs.getString("grid_density", null).orEmpty()) }
                .getOrDefault(GridDensity.STANDARD)
            if (!loaded) {
                val stored = prefs.getString("favorites_v1", null)
                if (stored != null) {
                    try {
                        val root = JsonParser.parseString(stored).asJsonObject
                        require(root["version"].asInt == 1)
                        accounts = root.getAsJsonObject("accounts").entrySet().associate { (key, value) ->
                            key to value.asJsonArray.map { require(it.isJsonPrimitive && it.asJsonPrimitive.isString); it.asString }.toSet()
                        }
                    } catch (_: Exception) { writable = false }
                }
                loaded = true
            }
            val address = credentials.serverUrl.trim().trimEnd('/').let { if ("://" in it) it else "https://$it" }
            account = privateReference((address.toHttpUrlOrNull()?.toString() ?: address) + "\n" + credentials.username)
            snapshot()
        }
    }

    suspend fun toggle(id: String): BrowserPreferenceState = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (account.isNotEmpty() && writable) {
                val current = accounts[account].orEmpty()
                accounts = accounts + (account to if (id in current) current - id else current + id)
                val values = JsonObject().apply {
                    accounts.forEach { (key, ids) -> add(key, JsonArray().apply { ids.forEach { add(it) } }) }
                }
                val root = JsonObject().apply { addProperty("version", 1); add("accounts", values) }
                prefs.edit().putString("favorites_v1", root.toString()).apply()
            }
            snapshot()
        }
    }

    suspend fun density(value: GridDensity): BrowserPreferenceState = withContext(Dispatchers.IO) {
        mutex.withLock {
            density = value
            prefs.edit().putString("grid_density", value.name).apply()
            snapshot()
        }
    }

    suspend fun adultFilter(value: Boolean): BrowserPreferenceState = withContext(Dispatchers.IO) {
        mutex.withLock {
            filterAdult = value
            prefs.edit().putBoolean("filter_adult", value).apply()
            snapshot()
        }
    }

    private fun snapshot() = BrowserPreferenceState(density, accounts[account].orEmpty(), account, filterAdult)
}
