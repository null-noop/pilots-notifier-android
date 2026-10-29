package com.nullnoop.pocket_pilots_notif

import android.content.Context
import android.content.SharedPreferences

class ServerSettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) {
            val sanitized = value.trim()
            prefs.edit().putString(KEY_SERVER_URL, if (sanitized.isEmpty()) DEFAULT_SERVER_URL else sanitized).apply()
        }

    companion object {
        private const val PREFS_NAME = "pocket_pilots_settings"
        const val KEY_SERVER_URL = "remote_server_url"
        const val DEFAULT_SERVER_URL = "https://localhost:8000"
    }
}
