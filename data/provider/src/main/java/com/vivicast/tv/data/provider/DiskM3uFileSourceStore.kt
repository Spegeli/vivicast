package com.vivicast.tv.data.provider

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
 * ponytail: plain files under a flat dir; providerId is a UUID so it is a safe filename. No index/DB —
 * the provider table already lists which ids exist.
 */
class DiskM3uFileSourceStore(
    private val baseDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun write(providerId: String, content: String) {
        withContext(ioDispatcher) {
            baseDir.mkdirs()
            fileFor(providerId).writeText(content)
        }
    }

    suspend fun read(providerId: String): String? =
        withContext(ioDispatcher) {
            fileFor(providerId).takeIf { it.isFile }?.readText()
        }

    suspend fun delete(providerId: String) {
        withContext(ioDispatcher) {
            fileFor(providerId).delete()
        }
    }

    private fun fileFor(providerId: String): File = File(baseDir, "$providerId.m3u")
}
