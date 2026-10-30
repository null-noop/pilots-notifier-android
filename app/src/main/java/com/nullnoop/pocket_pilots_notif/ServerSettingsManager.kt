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

    var pairCode: String?
        get() = prefs.getString(KEY_PAIR_CODE, null)
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_PAIR_CODE)
                else putString(KEY_PAIR_CODE, value.trim().replace("-", "").uppercase())
            }.apply()
        }

    val deviceId: String
        get() {
            val saved = prefs.getString(KEY_DEVICE_ID, null)
            if (!saved.isNullOrBlank()) return saved
            val generated = java.util.UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
            return generated
        }

    companion object {
        private const val PREFS_NAME = "pocket_pilots_settings"
        const val KEY_SERVER_URL = "remote_server_url"
        private const val KEY_PAIR_CODE = "pair_code"
        private const val KEY_DEVICE_ID = "device_id"
        const val DEFAULT_SERVER_URL = "https://pilots-api.nullnoop.dev"
    }
}
