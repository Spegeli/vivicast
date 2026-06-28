package com.vivicast.tv.di

import com.vivicast.tv.core.cache.M3uStreamReferenceStore
import com.vivicast.tv.core.cache.M3uStreamReference
import com.vivicast.tv.core.security.SecureKey
import com.vivicast.tv.core.security.SecureValueStore
import java.security.MessageDigest
import java.util.Base64

class SecureM3uStreamReferenceStore(
    private val secureValueStore: SecureValueStore,
) : M3uStreamReferenceStore {
    override suspend fun replaceProviderReferences(providerId: String, references: Map<String, M3uStreamReference>) {
        val cleaned = references
            .mapKeys { (remoteId, _) -> remoteId.trim() }
            .mapValues { (_, reference) ->
                reference.copy(
                    streamUrl = reference.streamUrl.trim(),
                    catchupMode = reference.catchupMode?.trim(),
                    catchupSource = reference.catchupSource?.trim(),
                )
            }
            .filter { (remoteId, reference) -> remoteId.isNotBlank() && reference.streamUrl.isNotBlank() }

        deleteProviderReferences(providerId)

        cleaned.forEach { (remoteId, reference) ->
            secureValueStore.write(streamKey(providerId, remoteId), reference.streamUrl)
            reference.catchupMode?.takeIf { it.isNotBlank() }?.let {
                secureValueStore.write(catchupModeKey(providerId, remoteId), it)
            }
            reference.catchupSource?.takeIf { it.isNotBlank() }?.let {
                secureValueStore.write(catchupSourceKey(providerId, remoteId), it)
            }
        }
        secureValueStore.write(indexKey(providerId), cleaned.keys.joinToString(separator = "\n") { it.encoded() })
    }

    override suspend fun getReference(providerId: String, remoteId: String): M3uStreamReference? {
        val normalizedRemoteId = remoteId.trim()
        if (normalizedRemoteId.isBlank()) return null
        val streamUrl = secureValueStore.read(streamKey(providerId, normalizedRemoteId))?.takeIf { it.isNotBlank() }
            ?: return null
        return M3uStreamReference(
            streamUrl = streamUrl,
            catchupMode = secureValueStore.read(catchupModeKey(providerId, normalizedRemoteId))?.takeIf { it.isNotBlank() },
            catchupSource = secureValueStore.read(catchupSourceKey(providerId, normalizedRemoteId))?.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun deleteProviderReferences(providerId: String) {
        val remoteIds = secureValueStore.read(indexKey(providerId))
            ?.lineSequence()
            ?.mapNotNull { it.decodedOrNull() }
            ?.toList()
            .orEmpty()

        remoteIds.forEach { remoteId ->
            secureValueStore.delete(streamKey(providerId, remoteId))
            secureValueStore.delete(catchupModeKey(providerId, remoteId))
            secureValueStore.delete(catchupSourceKey(providerId, remoteId))
        }
        secureValueStore.delete(indexKey(providerId))
    }

    private fun indexKey(providerId: String): SecureKey =
        SecureKey("$KEY_PREFIX:${providerId.stableHash()}:index")

    private fun streamKey(providerId: String, remoteId: String): SecureKey =
        SecureKey("$KEY_PREFIX:${providerId.stableHash()}:stream:${remoteId.stableHash()}")

    private fun catchupModeKey(providerId: String, remoteId: String): SecureKey =
        SecureKey("$KEY_PREFIX:${providerId.stableHash()}:catchup-mode:${remoteId.stableHash()}")

    private fun catchupSourceKey(providerId: String, remoteId: String): SecureKey =
        SecureKey("$KEY_PREFIX:${providerId.stableHash()}:catchup-source:${remoteId.stableHash()}")

    private fun String.stableHash(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun String.encoded(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(toByteArray(Charsets.UTF_8))

    private fun String.decodedOrNull(): String? =
        runCatching { String(Base64.getUrlDecoder().decode(this), Charsets.UTF_8) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    private companion object {
        const val KEY_PREFIX = "m3u-stream-reference"
    }
}
