package dev.zun.flux.data.repo

import android.content.Context
import androidx.core.content.edit

/**
 * Persisted user settings. Splits storage by sensitivity:
 *
 *  - Non-sensitive (URLs, timestamps) → plain SharedPreferences. Cheap reads,
 *    no Keystore round-trip on every access.
 *  - Sensitive (API token) → [KeystoreSecureStore], encrypted with an AES-256/GCM
 *    key held in the Android Keystore.
 */
class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val secureStore = KeystoreSecureStore(context)

    init {
        // One-time migration from the removed LAN/Tailscale split. Literal keys:
        // their constants no longer exist.
        if (prefs.getString(KEY_SERVER_URL, null).isNullOrBlank()) {
            val legacy = prefs.getString("lan_url", null)?.takeUnless { it.isBlank() }
                ?: prefs.getString("tailscale_url", null)?.takeUnless { it.isBlank() }
            if (legacy != null) prefs.edit { putString(KEY_SERVER_URL, legacy) }
        }
        prefs.edit {
            remove("active_route")
            remove("connection_mode")
            remove("lan_url")
            remove("tailscale_url")
        }
    }

    var lockoutDurationMs: Long
        get() = prefs.getLong(KEY_LOCKOUT_DURATION, 300_000L)
        set(value) = prefs.edit { putLong(KEY_LOCKOUT_DURATION, value) }

    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)
        set(value) = prefs.edit { putString(KEY_SERVER_URL, value) }

    var apiToken: String?
        get() = secureStore.get(KEY_API_TOKEN)
        set(value) = secureStore.put(KEY_API_TOKEN, value)

    var lastAuthTimestamp: Long
        get() = prefs.getLong(KEY_LAST_AUTH_TIMESTAMP, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_AUTH_TIMESTAMP, value) }

    var gallerySortNewestFirst: Boolean
        get() = prefs.getBoolean(KEY_GALLERY_SORT_NEWEST_FIRST, true)
        set(value) = prefs.edit { putBoolean(KEY_GALLERY_SORT_NEWEST_FIRST, value) }

    val isConfigured: Boolean
        get() = !serverUrl.isNullOrBlank() && !apiToken.isNullOrBlank()

    companion object {
        private const val KEY_LOCKOUT_DURATION = "lockout_duration_ms"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_TOKEN = "api_token"
        private const val KEY_LAST_AUTH_TIMESTAMP = "last_auth_timestamp"
        private const val KEY_GALLERY_SORT_NEWEST_FIRST = "gallery_sort_newest_first"
    }
}
