package dev.zun.flux.data.repo

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256/GCM symmetric cipher whose key is generated and held in the Android
 * Keystore. Used for the offline image cache so cached image bytes never sit
 * in plain on disk. Each [encrypt] call produces a fresh random IV; the on-disk
 * payload format is:
 *
 *     [1 byte version][12 byte IV][ciphertext including 16-byte GCM tag]
 *
 * The Keystore key never leaves secure hardware on devices that have
 * StrongBox or a TEE-backed Keystore implementation.
 */
class EncryptedFileVault private constructor(private val keyAlias: String) {

    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        check(iv.size == GCM_IV_BYTES) { "Unexpected IV length ${iv.size}" }
        val out = ByteArray(HEADER_BYTES + ciphertext.size)
        out[0] = VERSION
        System.arraycopy(iv, 0, out, 1, GCM_IV_BYTES)
        System.arraycopy(ciphertext, 0, out, HEADER_BYTES, ciphertext.size)
        return out
    }

    fun decrypt(encrypted: ByteArray): ByteArray {
        require(encrypted.size > HEADER_BYTES + GCM_TAG_BYTES) { "Ciphertext too small" }
        require(encrypted[0] == VERSION) { "Unsupported vault version: ${encrypted[0]}" }
        val iv = encrypted.copyOfRange(1, HEADER_BYTES)
        val ct = encrypted.copyOfRange(HEADER_BYTES, encrypted.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    /** First-byte sniff: cheap pre-flight before [decrypt] on possibly-legacy files. */
    fun looksEncrypted(firstByte: Byte): Boolean = firstByte == VERSION

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

    companion object {
        private const val DEFAULT_KEY_ALIAS = "flux_offline_cache_key"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private const val HEADER_BYTES = 1 + GCM_IV_BYTES
        private const val VERSION: Byte = 1

        /**
         * Returns a working vault on Android, or null when running under
         * Robolectric (no AndroidKeyStore provider). Callers treat null as
         * "encryption disabled" and skip the encrypt/decrypt round-trip.
         */
        fun from(@Suppress("UNUSED_PARAMETER") context: Context): EncryptedFileVault? {
            val isRobolectric = runCatching {
                Class.forName("org.robolectric.RuntimeEnvironment")
            }.isSuccess
            if (isRobolectric) return null
            return EncryptedFileVault(DEFAULT_KEY_ALIAS)
        }
    }
}
