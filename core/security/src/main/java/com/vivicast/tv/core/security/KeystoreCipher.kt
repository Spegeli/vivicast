package com.vivicast.tv.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES/GCM encryption of byte blobs backed by an AndroidKeyStore key — for encrypting on-disk artifacts at
 * rest (the File-mode M3U source, #3), which are too large for the key/value [SecureValueStore].
 *
 * Framing: `[VERSION][12-byte IV][ciphertext+GCM tag]`. [decrypt] returns null for anything not framed with
 * our VERSION marker, so a caller can transparently read + migrate a legacy cleartext file.
 */
class KeystoreCipher {

    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        return ByteArray(1 + iv.size + ciphertext.size).also { framed ->
            framed[0] = VERSION
            System.arraycopy(iv, 0, framed, 1, iv.size)
            System.arraycopy(ciphertext, 0, framed, 1 + iv.size, ciphertext.size)
        }
    }

    /** Plaintext bytes, or null if [data] isn't VERSION-framed / can't be decrypted (i.e. legacy cleartext). */
    fun decrypt(data: ByteArray): ByteArray? {
        if (data.size < 1 + IV_LENGTH || data[0] != VERSION) return null
        return runCatching {
            val iv = data.copyOfRange(1, 1 + IV_LENGTH)
            val ciphertext = data.copyOfRange(1 + IV_LENGTH, data.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.doFinal(ciphertext)
        }.getOrNull()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "vivicast_file_source_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val IV_LENGTH = 12
        const val VERSION: Byte = 1
    }
}
