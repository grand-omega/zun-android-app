@file:Suppress("DEPRECATION") // legacy EncryptedSharedPreferences read for one-time migration

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

/**
 * Persisted user settings. Splits storage by sensitivity:
 *
 *  - Non-sensitive (URLs, mode, timestamps) → plain SharedPreferences. Cheap reads,
 *    no Keystore round-trip on every access.
 *  - Sensitive (API token) → [KeystoreSecureStore], encrypted with an AES-256/GCM
 *    key held in the Android Keystore.
 *
 * On first run after upgrading, [migrateLegacySecureSettings] copies any values
 * from the deprecated [EncryptedSharedPreferences] file to the new layout and
 * removes the legacy file.
 */
class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val secureStore = KeystoreSecureStore(context)

    init {
        migrateLegacySecureSettings(context)
    }

    var lockoutDurationMs: Long
        get() = prefs.getLong(KEY_LOCKOUT_DURATION, 300_000L)
        set(value) = prefs.edit { putLong(KEY_LOCKOUT_DURATION, value) }

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
        get() = prefs.getString(KEY_LAN_URL, null)
        set(value) = prefs.edit { putString(KEY_LAN_URL, value) }

    var tailscaleUrl: String?
        get() = prefs.getString(KEY_TAILSCALE_URL, null)
        set(value) = prefs.edit { putString(KEY_TAILSCALE_URL, value) }

    var apiToken: String?
        get() = secureStore.get(KEY_API_TOKEN)
        set(value) = secureStore.put(KEY_API_TOKEN, value)

    var lastAuthTimestamp: Long
        get() = prefs.getLong(KEY_LAST_AUTH_TIMESTAMP, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_AUTH_TIMESTAMP, value) }

    val isConfigured: Boolean
        get() = (!lanUrl.isNullOrBlank() || !tailscaleUrl.isNullOrBlank()) && !apiToken.isNullOrBlank()

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T = runCatching {
        enumValueOf<T>(prefs.getString(key, default.name) ?: default.name)
    }.getOrDefault(default)

    private fun migrateLegacySecureSettings(context: Context) {
        if (prefs.getBoolean(KEY_LEGACY_MIGRATED, false)) return
        val legacyFile = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        if (legacyFile.all.isEmpty()) {
            prefs.edit { putBoolean(KEY_LEGACY_MIGRATED, true) }
            return
        }
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val legacy = EncryptedSharedPreferences.create(
                context,
                LEGACY_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            legacy.getString(KEY_LAN_URL, null)?.let { lanUrl = it }
            legacy.getString(KEY_TAILSCALE_URL, null)?.let { tailscaleUrl = it }
            legacy.getString(KEY_SERVER_URL, null)?.let { serverUrl = it }
            legacy.getString(KEY_ACTIVE_ROUTE, null)?.let { prefs.edit { putString(KEY_ACTIVE_ROUTE, it) } }
            legacy.getString(KEY_CONNECTION_MODE, null)?.let { prefs.edit { putString(KEY_CONNECTION_MODE, it) } }
            if (legacy.contains(KEY_LOCKOUT_DURATION)) {
                lockoutDurationMs = legacy.getLong(KEY_LOCKOUT_DURATION, 300_000L)
            }
            if (legacy.contains(KEY_LAST_AUTH_TIMESTAMP)) {
                lastAuthTimestamp = legacy.getLong(KEY_LAST_AUTH_TIMESTAMP, 0L)
            }
            legacy.getString(KEY_API_TOKEN, null)?.let { apiToken = it }
        }
        // Best-effort wipe of the legacy file so secrets don't linger on disk.
        context.deleteSharedPreferences(LEGACY_PREFS_NAME)
        prefs.edit { putBoolean(KEY_LEGACY_MIGRATED, true) }
    }

    companion object {
        private const val LEGACY_PREFS_NAME = "secure_settings"
        private const val KEY_LEGACY_MIGRATED = "legacy_secure_migrated"

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
