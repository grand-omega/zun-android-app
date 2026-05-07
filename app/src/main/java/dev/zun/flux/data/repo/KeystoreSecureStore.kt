package dev.zun.flux.data.repo

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted key→value store backed by an AES-256/GCM key in the Android Keystore.
 *
 * Replaces [androidx.security.crypto.EncryptedSharedPreferences] (deprecated by
 * Google 2024 with no drop-in replacement). The Keystore key never leaves
 * secure hardware on devices that support it; the ciphertext is stored in a
 * plain [SharedPreferences] file as `base64(IV || ciphertext)`.
 *
 * Used only for genuinely sensitive values (e.g. API tokens). Non-sensitive
 * settings live in [SettingsManager]'s plain prefs to avoid the AES round-trip
 * on every read.
 */
class KeystoreSecureStore(
    context: Context,
    prefsName: String = "secure_v2",
    private val keyAlias: String = "flux_secure_store_key",
) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun get(key: String): String? {
        val token = prefs.getString(key, null) ?: return null
        return runCatching {
            val combined = Base64.decode(token, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, GCM_IV_BYTES)
            val ciphertext = combined.copyOfRange(GCM_IV_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrNull()
    }

    fun put(key: String, value: String?) {
        if (value == null) {
            prefs.edit { remove(key) }
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = iv + ciphertext
        prefs.edit { putString(key, Base64.encodeToString(combined, Base64.NO_WRAP)) }
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
