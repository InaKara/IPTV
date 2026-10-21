package com.example.iptv.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.iptv.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialsManager @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    var serverUrl: String
        get() {
            val savedUrl = prefs.getString("server_url", null)?.trim()?.trimEnd('/')
            val defaultUrl = BuildConfig.DEFAULT_SERVER_URL.trim().trimEnd('/')
            val retiredServers = BuildConfig.LEGACY_SERVER_URLS.split(',')
                .filter { it.isNotBlank() }.map { normalizeServer(it) }
            return if (savedUrl.isNullOrBlank() ||
                (defaultUrl.isNotBlank() && normalizeServer(savedUrl) in retiredServers)) {
                defaultUrl
            } else {
                savedUrl
            }
        }
        set(value) = prefs.edit().putString("server_url", value.trim().trimEnd('/')).apply()

    var username: String
        get() = prefs.getString("username", BuildConfig.DEFAULT_USERNAME) ?: BuildConfig.DEFAULT_USERNAME
        set(value) = prefs.edit().putString("username", value).apply()

    var password: String
        get() = prefs.getString("password", BuildConfig.DEFAULT_PASSWORD) ?: BuildConfig.DEFAULT_PASSWORD
        set(value) = prefs.edit().putString("password", value).apply()

    fun clear() {
        prefs.edit().remove("server_url").remove("username").remove("password").apply()
    }

    fun save(server: String, user: String, pass: String) {
        prefs.edit().putString("server_url", server.trim().trimEnd('/'))
            .putString("username", user).putString("password", pass).apply()
    }

    private fun normalizeServer(value: String): String = value.trim().trimEnd('/')
        .removePrefix("http://").removePrefix("https://").lowercase()
}
