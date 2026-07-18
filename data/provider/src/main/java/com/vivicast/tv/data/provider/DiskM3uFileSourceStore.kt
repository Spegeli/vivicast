package com.vivicast.tv.data.provider

import com.vivicast.tv.core.security.KeystoreCipher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Durable, on-disk store for the raw content of a File-mode M3U playlist ([M3uSourceMode.File]).
 *
 * File-mode playlists have no fetchable URL, so their imported text is the only way to rebuild the
 * catalog on a later refresh or after a backup restore. This keeps that text per provider under
 * [baseDir] (app-internal storage) instead of the previous RAM-only holder, so it survives app
 * restarts and can be embedded in the encrypted backup payload.
 *
 * #3: the content (a full channel list, whose stream URLs commonly embed the account) is AES/GCM-encrypted
 * at rest via [cipher] — like every other provider secret — instead of the previous cleartext. Encryption is
 * transparent to every caller (provider save, refresh, editor connection-test, backup export/restore), so the
 * backup still round-trips: the exporter reads plaintext via [read] and the restorer writes it back via
 * [write], which re-encrypts under the target device's Keystore key (portable across devices).
 *
 * ponytail: plain files under a flat dir; providerId is a UUID so it is a safe filename. No index/DB —
 * the provider table already lists which ids exist.
 */
class DiskM3uFileSourceStore(
    private val baseDir: File,
    private val cipher: KeystoreCipher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun write(providerId: String, content: String) {
        withContext(ioDispatcher) {
            baseDir.mkdirs()
            fileFor(providerId).writeBytes(cipher.encrypt(content.toByteArray(Charsets.UTF_8)))
        }
    }

    suspend fun read(providerId: String): String? =
        withContext(ioDispatcher) {
            val bytes = fileFor(providerId).takeIf { it.isFile }?.readBytes() ?: return@withContext null
            // Legacy cleartext files (pre-#3) aren't AES/GCM-framed → decrypt returns null → read as plaintext;
            // the next write re-encrypts them.
            val decrypted = cipher.decrypt(bytes) ?: return@withContext String(bytes, Charsets.UTF_8)
            String(decrypted, Charsets.UTF_8)
        }

    suspend fun delete(providerId: String) {
        withContext(ioDispatcher) {
            fileFor(providerId).delete()
        }
    }

    private fun fileFor(providerId: String): File = File(baseDir, "$providerId.m3u")
}
