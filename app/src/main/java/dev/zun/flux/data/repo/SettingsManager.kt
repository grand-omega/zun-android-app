// androidx.security-crypto is deprecated by Google with no drop-in replacement;
// suppress until we migrate to Keystore/Tink directly.
@file:Suppress("DEPRECATION")

package dev.zun.flux.data.repo

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class ConnectionMode {
    AUTO,
    LAN_ONLY,
    TAILSCALE_ONLY,
}

enum class ActiveRoute {
    NONE,
    LAN,
    TAILSCALE,
}

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
        get() = prefs.getLong(KEY_LOCKOUT_DURATION, 300_000L)
        set(value) = prefs.edit { putLong(KEY_LOCKOUT_DURATION, value) }

    /**
     * The currently active base URL — written by NetworkResolver after probing.
     * Retrofit's baseUrl and Coil image URLs both read this. Treat as read-only
     * outside of NetworkResolver.
     */
    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)
        set(value) = prefs.edit { putString(KEY_SERVER_URL, value) }

    var activeRoute: ActiveRoute
        get() = enumPref(KEY_ACTIVE_ROUTE, ActiveRoute.NONE)
        set(value) = prefs.edit { putString(KEY_ACTIVE_ROUTE, value.name) }

    var connectionMode: ConnectionMode
        get() = enumPref(KEY_CONNECTION_MODE, ConnectionMode.AUTO)
        set(value) = prefs.edit { putString(KEY_CONNECTION_MODE, value.name) }

    var lanUrl: String?
        get() {
            val v = prefs.getString(KEY_LAN_URL, null)
            if (v != null) return v
            // One-time migration: legacy single-URL installs become LAN-only.
            val legacy = prefs.getString(KEY_SERVER_URL, null)
            if (!legacy.isNullOrBlank()) {
                prefs.edit { putString(KEY_LAN_URL, legacy) }
            }
            return prefs.getString(KEY_LAN_URL, null)
        }
        set(value) = prefs.edit { putString(KEY_LAN_URL, value) }

    var tailscaleUrl: String?
        get() = prefs.getString(KEY_TAILSCALE_URL, null)
        set(value) = prefs.edit { putString(KEY_TAILSCALE_URL, value) }

    var apiToken: String?
        get() = prefs.getString(KEY_API_TOKEN, null)
        set(value) = prefs.edit { putString(KEY_API_TOKEN, value) }

    /**
     * Wall-clock millis of the most recent successful biometric unlock. Persisted so
     * the [lockoutDurationMs] grace window survives process death — Android often kills
     * the app while the Photo Picker (or any heavyweight foreground activity) is on
     * top, and an in-memory timestamp would fall back to 0 and re-prompt every time.
     */
    var lastAuthTimestamp: Long
        get() = prefs.getLong(KEY_LAST_AUTH_TIMESTAMP, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_AUTH_TIMESTAMP, value) }

    val isConfigured: Boolean
        get() = (!lanUrl.isNullOrBlank() || !tailscaleUrl.isNullOrBlank()) && !apiToken.isNullOrBlank()

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T = runCatching {
        enumValueOf<T>(prefs.getString(key, default.name) ?: default.name)
    }.getOrDefault(default)

    companion object {
        private const val KEY_LOCKOUT_DURATION = "lockout_duration_ms"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_ACTIVE_ROUTE = "active_route"
        private const val KEY_CONNECTION_MODE = "connection_mode"
        private const val KEY_LAN_URL = "lan_url"
        private const val KEY_TAILSCALE_URL = "tailscale_url"
        private const val KEY_API_TOKEN = "api_token"
        private const val KEY_LAST_AUTH_TIMESTAMP = "last_auth_timestamp"
    }
}
