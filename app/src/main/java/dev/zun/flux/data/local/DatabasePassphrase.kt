package dev.zun.flux.data.local

import android.content.Context
import android.util.Base64
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

    fun get(context: Context): String {
        val store = KeystoreSecureStore(context.applicationContext)
        store.get(KEY)?.let { return it }
        val bytes = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val passphrase = Base64.encodeToString(bytes, Base64.NO_WRAP)
        store.put(KEY, passphrase)
        return passphrase
    }
}
