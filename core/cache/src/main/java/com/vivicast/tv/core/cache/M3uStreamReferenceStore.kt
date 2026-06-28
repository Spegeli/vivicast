package com.vivicast.tv.core.cache

import java.io.File
import java.security.MessageDigest
import java.util.Properties

interface M3uStreamReferenceStore {
    suspend fun replaceProviderReferences(providerId: String, references: Map<String, M3uStreamReference>)
    suspend fun getReference(providerId: String, remoteId: String): M3uStreamReference?
    suspend fun getStreamUrl(providerId: String, remoteId: String): String? =
        getReference(providerId, remoteId)?.streamUrl

    suspend fun deleteProviderReferences(providerId: String)
}

data class M3uStreamReference(
    val streamUrl: String,
    val catchupMode: String? = null,
    val catchupSource: String? = null,
)

object NoOpM3uStreamReferenceStore : M3uStreamReferenceStore {
    override suspend fun replaceProviderReferences(providerId: String, references: Map<String, M3uStreamReference>) = Unit
    override suspend fun getReference(providerId: String, remoteId: String): M3uStreamReference? = null
    override suspend fun deleteProviderReferences(providerId: String) = Unit
}

class FileM3uStreamReferenceStore(
    private val directory: File,
) : M3uStreamReferenceStore {
    override suspend fun replaceProviderReferences(providerId: String, references: Map<String, M3uStreamReference>) {
        directory.mkdirs()
        val properties = Properties()
        references.forEach { (remoteId, reference) ->
            val key = remoteId.trim()
            val streamUrl = reference.streamUrl.trim()
            if (key.isNotBlank() && streamUrl.isNotBlank()) {
                properties["$key.url"] = streamUrl
                reference.catchupMode?.trim()?.takeIf { it.isNotBlank() }?.let { properties["$key.catchupMode"] = it }
                reference.catchupSource?.trim()?.takeIf { it.isNotBlank() }?.let { properties["$key.catchupSource"] = it }
            }
        }
        providerFile(providerId).outputStream().use { output ->
            properties.store(output, null)
        }
    }

    override suspend fun getReference(providerId: String, remoteId: String): M3uStreamReference? {
        val file = providerFile(providerId)
        if (!file.exists()) return null
        val properties = Properties()
        file.inputStream().use { input -> properties.load(input) }
        val key = remoteId.trim()
        val streamUrl = properties.getProperty("$key.url")
            ?: properties.getProperty(key)
            ?: return null
        return M3uStreamReference(
            streamUrl = streamUrl.takeIf { it.isNotBlank() } ?: return null,
            catchupMode = properties.getProperty("$key.catchupMode")?.takeIf { it.isNotBlank() },
            catchupSource = properties.getProperty("$key.catchupSource")?.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun deleteProviderReferences(providerId: String) {
        providerFile(providerId).delete()
    }

    private fun providerFile(providerId: String): File =
        File(directory, "${providerId.stableHash()}.properties")

    private fun String.stableHash(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
