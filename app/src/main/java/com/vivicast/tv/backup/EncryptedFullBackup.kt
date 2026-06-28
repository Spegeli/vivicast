package com.vivicast.tv.backup

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

private const val EXPORT_MODE_ENCRYPTED_FULL = "ENCRYPTED_FULL"
private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
private const val KDF_KEY_BITS = 256
private const val KDF_SALT_BYTES = 16
private const val KDF_ITERATIONS = 120_000
private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
private const val CIPHER_NAME = "AES-GCM"
private const val GCM_NONCE_BYTES = 12
private const val GCM_TAG_BYTES = 16
private const val GCM_TAG_BITS = GCM_TAG_BYTES * 8

fun encryptFullBackupPayload(
    payloadJson: String,
    passphrase: CharArray,
    exportedAtMillis: Long = System.currentTimeMillis(),
    appVersion: String = "unknown",
    packageName: String = "com.vivicast.tv",
    databaseVersion: Int = 0,
    dataSections: List<String> = listOf("encryptedPayload"),
    random: SecureRandom = SecureRandom(),
    iterations: Int = KDF_ITERATIONS,
): String {
    require(payloadJson.isNotBlank()) { "payloadJson must not be blank" }
    require(passphrase.isNotEmpty()) { "passphrase must not be empty" }
    require(appVersion.isNotBlank()) { "appVersion must not be blank" }
    require(packageName.isNotBlank()) { "packageName must not be blank" }
    require(databaseVersion >= 0) { "databaseVersion must not be negative" }
    require(dataSections.isNotEmpty()) { "dataSections must not be empty" }
    require(iterations > 0) { "iterations must be positive" }

    val salt = random.bytes(KDF_SALT_BYTES)
    val nonce = random.bytes(GCM_NONCE_BYTES)
    val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, fullBackupKey(passphrase, salt, iterations), GCMParameterSpec(GCM_TAG_BITS, nonce))
    val encryptedWithTag = cipher.doFinal(payloadJson.toByteArray(Charsets.UTF_8))
    val ciphertext = encryptedWithTag.copyOfRange(0, encryptedWithTag.size - GCM_TAG_BYTES)
    val authTag = encryptedWithTag.copyOfRange(encryptedWithTag.size - GCM_TAG_BYTES, encryptedWithTag.size)

    return JSONObject()
        .put("schemaVersion", STANDARD_BACKUP_SCHEMA_VERSION)
        .put("exportMode", EXPORT_MODE_ENCRYPTED_FULL)
        .put("exportedAtMillis", exportedAtMillis)
        .put("appVersion", appVersion)
        .put("packageName", packageName)
        .put("databaseVersion", databaseVersion)
        .put("dataSections", JSONArray(dataSections))
        .put("kdf", JSONObject()
            .put("algorithm", KDF_ALGORITHM)
            .put("salt", salt.b64())
            .put("iterations", iterations))
        .put("cipher", JSONObject()
            .put("algorithm", CIPHER_NAME)
            .put("nonce", nonce.b64()))
        .put("ciphertext", ciphertext.b64())
        .put("authTag", authTag.b64())
        .toString(2)
}

fun decryptFullBackupPayload(
    containerJson: String,
    passphrase: CharArray,
): String? =
    runCatching {
        val container = JSONObject(containerJson)
        if (container.optInt("schemaVersion") != STANDARD_BACKUP_SCHEMA_VERSION) return null
        if (container.optString("exportMode") != EXPORT_MODE_ENCRYPTED_FULL) return null
        val kdf = container.getJSONObject("kdf")
        val cipherMetadata = container.getJSONObject("cipher")
        if (kdf.optString("algorithm") != KDF_ALGORITHM) return null
        if (cipherMetadata.optString("algorithm") != CIPHER_NAME) return null
        val iterations = kdf.optInt("iterations")
        if (iterations <= 0) return null

        val salt = kdf.getString("salt").b64Decode()
        val nonce = cipherMetadata.getString("nonce").b64Decode()
        val ciphertext = container.getString("ciphertext").b64Decode()
        val authTag = container.getString("authTag").b64Decode()
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, fullBackupKey(passphrase, salt, iterations), GCMParameterSpec(GCM_TAG_BITS, nonce))
        String(cipher.doFinal(ciphertext + authTag), Charsets.UTF_8)
    }.getOrNull()

private fun fullBackupKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
    val spec = PBEKeySpec(passphrase, salt, iterations, KDF_KEY_BITS)
    return try {
        SecretKeySpec(SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded, "AES")
    } finally {
        spec.clearPassword()
    }
}

private fun SecureRandom.bytes(size: Int): ByteArray =
    ByteArray(size).also(::nextBytes)

private fun ByteArray.b64(): String =
    Base64.getEncoder().encodeToString(this)

private fun String.b64Decode(): ByteArray =
    Base64.getDecoder().decode(this)
