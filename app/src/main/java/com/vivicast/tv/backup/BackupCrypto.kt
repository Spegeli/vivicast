package com.vivicast.tv.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// Binary encrypted backup container. The whole .vcbak is opaque bytes: only a small fixed header
// (magic + format version + KDF iterations + salt + nonce — which must be plaintext to derive the
// key and initialise the cipher) is not ciphertext. Everything else — all backup data, source URLs
// and credentials — lives inside the AES-GCM ciphertext, so the file reads as gibberish in an editor.
//
// Layout: [MAGIC(4)][formatVersion(1)][iterations(int, 4, big-endian)][salt(16)][nonce(12)][ciphertext+tag]
private val MAGIC = byteArrayOf(0x56, 0x56, 0x43, 0x42) // "VVCB"
private const val CONTAINER_FORMAT_VERSION = 1
private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
private const val KDF_KEY_BITS = 256
private const val KDF_SALT_BYTES = 16
private const val KDF_ITERATIONS = 120_000
private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_NONCE_BYTES = 12
private const val GCM_TAG_BITS = 128
private const val VERSION_FIELD_BYTES = 1
private const val ITERATIONS_FIELD_BYTES = 4
private const val HEADER_BYTES =
    4 + VERSION_FIELD_BYTES + ITERATIONS_FIELD_BYTES + KDF_SALT_BYTES + GCM_NONCE_BYTES

fun encryptBackupPayload(
    payloadJson: String,
    passphrase: CharArray,
    random: SecureRandom = SecureRandom(),
    iterations: Int = KDF_ITERATIONS,
): ByteArray {
    require(payloadJson.isNotBlank()) { "payloadJson must not be blank" }
    require(passphrase.isNotEmpty()) { "passphrase must not be empty" }
    require(iterations > 0) { "iterations must be positive" }

    val salt = ByteArray(KDF_SALT_BYTES).also(random::nextBytes)
    val nonce = ByteArray(GCM_NONCE_BYTES).also(random::nextBytes)
    val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, backupKey(passphrase, salt, iterations), GCMParameterSpec(GCM_TAG_BITS, nonce))
    val ciphertext = cipher.doFinal(payloadJson.toByteArray(Charsets.UTF_8)) // trailing 16-byte GCM tag included

    return ByteBuffer.allocate(HEADER_BYTES + ciphertext.size)
        .put(MAGIC)
        .put(CONTAINER_FORMAT_VERSION.toByte())
        .putInt(iterations)
        .put(salt)
        .put(nonce)
        .put(ciphertext)
        .array()
}

fun decryptBackupPayload(
    container: ByteArray,
    passphrase: CharArray,
): String? =
    runCatching {
        if (!isEncryptedBackupContainer(container)) return null
        val buffer = ByteBuffer.wrap(container)
        buffer.position(MAGIC.size)
        // Migration seam: reject a newer container format we cannot parse; read the current or older.
        if (buffer.get().toInt() > CONTAINER_FORMAT_VERSION) return null
        val iterations = buffer.int
        if (iterations <= 0) return null
        val salt = ByteArray(KDF_SALT_BYTES).also(buffer::get)
        val nonce = ByteArray(GCM_NONCE_BYTES).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, backupKey(passphrase, salt, iterations), GCMParameterSpec(GCM_TAG_BITS, nonce))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

/** True if [container] starts with the Vivicast encrypted-backup magic (before any passphrase). */
fun isEncryptedBackupContainer(container: ByteArray): Boolean =
    container.size >= HEADER_BYTES && MAGIC.indices.all { container[it] == MAGIC[it] }

private fun backupKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
    val spec = PBEKeySpec(passphrase, salt, iterations, KDF_KEY_BITS)
    return try {
        SecretKeySpec(SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded, "AES")
    } finally {
        spec.clearPassword()
    }
}
