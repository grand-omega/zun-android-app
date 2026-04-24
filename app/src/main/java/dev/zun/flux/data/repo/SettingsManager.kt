// androidx.security-crypto is deprecated by Google with no drop-in replacement;
// suppress until we migrate to Keystore/Tink directly.
@file:Suppress("DEPRECATION")

package dev.zun.flux.data.repo

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var lockoutDurationMs: Long
        get() = prefs.getLong(KEY_LOCKOUT_DURATION, 60_000L)
        set(value) = prefs.edit().putLong(KEY_LOCKOUT_DURATION, value).apply()

    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var apiToken: String?
        get() = prefs.getString(KEY_API_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_API_TOKEN, value).apply()

    /**
     * Wall-clock millis of the most recent successful biometric unlock. Persisted so
     * the [lockoutDurationMs] grace window survives process death — Android often kills
     * the app while the Photo Picker (or any heavyweight foreground activity) is on
     * top, and an in-memory timestamp would fall back to 0 and re-prompt every time.
     */
    var lastAuthTimestamp: Long
        get() = prefs.getLong(KEY_LAST_AUTH_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_AUTH_TIMESTAMP, value).apply()

    val isConfigured: Boolean
        get() = !serverUrl.isNullOrBlank() && !apiToken.isNullOrBlank()

    companion object {
        private const val KEY_LOCKOUT_DURATION = "lockout_duration_ms"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_TOKEN = "api_token"
        private const val KEY_LAST_AUTH_TIMESTAMP = "last_auth_timestamp"
    }
}
