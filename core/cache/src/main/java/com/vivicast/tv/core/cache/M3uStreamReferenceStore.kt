package com.vivicast.tv.core.cache

import java.io.File
import java.security.MessageDigest
import java.util.Properties

interface M3uStreamReferenceStore {
    fun replaceProviderReferences(providerId: String, references: Map<String, String>)
    fun getStreamUrl(providerId: String, remoteId: String): String?
    fun deleteProviderReferences(providerId: String)
}

object NoOpM3uStreamReferenceStore : M3uStreamReferenceStore {
    override fun replaceProviderReferences(providerId: String, references: Map<String, String>) = Unit
    override fun getStreamUrl(providerId: String, remoteId: String): String? = null
    override fun deleteProviderReferences(providerId: String) = Unit
}

class FileM3uStreamReferenceStore(
    private val directory: File,
) : M3uStreamReferenceStore {
    @Synchronized
    override fun replaceProviderReferences(providerId: String, references: Map<String, String>) {
        directory.mkdirs()
        val properties = Properties()
        references.forEach { (remoteId, streamUrl) ->
            val key = remoteId.trim()
            val value = streamUrl.trim()
            if (key.isNotBlank() && value.isNotBlank()) {
                properties[key] = value
            }
        }
        providerFile(providerId).outputStream().use { output ->
            properties.store(output, null)
        }
    }

    @Synchronized
    override fun getStreamUrl(providerId: String, remoteId: String): String? {
        val file = providerFile(providerId)
        if (!file.exists()) return null
        val properties = Properties()
        file.inputStream().use { input -> properties.load(input) }
        return properties.getProperty(remoteId.trim())?.takeIf { it.isNotBlank() }
    }

    @Synchronized
    override fun deleteProviderReferences(providerId: String) {
        providerFile(providerId).delete()
    }

    private fun providerFile(providerId: String): File =
        File(directory, "${providerId.stableHash()}.properties")

    private fun String.stableHash(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
