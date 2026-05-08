package dev.zun.flux.data.local

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import dev.zun.flux.data.repo.KeystoreSecureStore
import java.security.SecureRandom

/**
 * 32-byte random passphrase for the SQLCipher-encrypted [AppDatabase], persisted
 * encrypted-at-rest via [KeystoreSecureStore]. The wrapping AES-256/GCM key
 * lives in the Android Keystore and never leaves secure hardware on devices
 * with StrongBox or a TEE-backed Keystore implementation.
 *
 * Generated lazily on first call and reused for the lifetime of the install.
 * Returned as a Base64 string so it round-trips cleanly through both the
 * [KeystoreSecureStore] string API and SQLCipher's UTF-8 PRAGMA-key path.
 */
internal object DatabasePassphrase {
    private const val KEY = "flux_db_passphrase_v1"
    private const val PASSPHRASE_BYTES = 32

    /** Plain-prefs sentinel marking that a passphrase has been generated. Used
     *  only to distinguish first-run from "Keystore lost the key" — contains
     *  nothing sensitive itself. */
    private const val GENERATED_PREFS = "flux_db_passphrase_meta"
    private const val GENERATED_KEY = "generated"

    fun get(context: Context): String {
        val app = context.applicationContext
        val store = KeystoreSecureStore(app)
        store.get(KEY)?.let { return it }

        // store.get() returned null. Two cases:
        //   1. First run — no passphrase has ever been generated.
        //   2. Keystore lost the wrap-key (biometric reset on some OEMs, key
        //      corruption). Silently regenerating would leave the existing
        //      SQLCipher DB permanently unreadable under the new key.
        val meta = app.getSharedPreferences(GENERATED_PREFS, Context.MODE_PRIVATE)
        if (meta.getBoolean(GENERATED_KEY, false)) {
            throw DatabasePassphraseLostException()
        }

        val bytes = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val passphrase = Base64.encodeToString(bytes, Base64.NO_WRAP)
        store.put(KEY, passphrase)
        meta.edit { putBoolean(GENERATED_KEY, true) }
        return passphrase
    }
}

/**
 * Thrown when the SQLCipher passphrase was previously generated but is no
 * longer retrievable from the Keystore. Callers should treat this as a hard
 * failure: any existing local DB is encrypted under the lost key and is
 * unrecoverable. Recovery is wipe-and-reinit; the app should surface this
 * to the user rather than silently re-keying.
 */
class DatabasePassphraseLostException : IllegalStateException("Database passphrase was generated but is no longer retrievable from the Keystore")
