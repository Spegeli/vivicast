package com.vivicast.tv

import com.vivicast.tv.data.epg.smartMatchKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * In-memory index of a user-chosen local logos folder (LOGO_PRIORITY_LOCAL). Scans the folder + all
 * subfolders once (on folder-set / app-start / manual "Neu einlesen"), keeping only image files, and matches
 * a channel to a file via the shared [smartMatchKey] recognition (the same one the EPG matcher uses), so
 * common repos like tv-logos (`zdf-info-de.png`) just work against the usual tvg-id (`ZDFinfo.de@SD`) and
 * channel-name (`ZDFinfo (720p)`) formats — all reduce to `zdfinfo`.
 *
 * Holds only file paths (a few hundred bytes each) — never image bytes; Coil decodes the visible ones on
 * demand. On duplicate matches (e.g. `zdf.png` + `zdf.jpg`) the higher-priority extension wins
 * (png > jpg > jpeg > webp), ties broken by lexicographic path.
 *
 * ponytail: rebuilt fully each time (a name-list scan of a few thousand files is <100 ms); no incremental
 * watch. Add a FileObserver only if live folder edits without a manual re-scan ever matter.
 */
class LocalLogoIndex(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    @Volatile private var byKey: Map<String, File> = emptyMap()

    /** True once a folder has been scanned to a non-empty index (drives the resolve hook's fast path). */
    val isReady: Boolean get() = byKey.isNotEmpty()

    /**
     * Rescans [folder] (recursively) and replaces the index. A null/missing folder clears it. Returns the
     * number of indexed files (for the diagnostics event); a non-null folder that isn't a readable directory
     * returns -1 so the caller can distinguish "unreadable" from "empty".
     */
    suspend fun rebuild(folder: File?): Int {
        if (folder != null && !folder.isDirectory) {
            byKey = emptyMap()
            return -1
        }
        byKey = if (folder == null) emptyMap() else withContext(ioDispatcher) { scan(folder) }
        return byKey.size
    }

    /** File matching this channel by [smartMatchKey] on its tvg-id then its name, or null. */
    fun lookup(epgChannelId: String?, channelName: String): File? {
        if (byKey.isEmpty()) return null
        epgChannelId?.let { smartMatchKey(it) }?.takeIf { it.isNotEmpty() }
            ?.let { byKey[it] }
            ?.let { return it }
        return smartMatchKey(channelName).takeIf { it.isNotEmpty() }?.let { byKey[it] }
    }

    private fun scan(folder: File): Map<String, File> {
        val map = HashMap<String, File>()
        var seen = 0
        folder.walkTopDown()
            .maxDepth(MAX_DEPTH)
            .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in ACCEPTED_EXTENSIONS }
            .forEach { file ->
                if (seen++ >= MAX_FILES) return@forEach
                putPreferred(map, smartMatchKey(file.nameWithoutExtension), file)
            }
        return map
    }

    // Keep the winning file for a key: higher-priority extension first, then lexicographically smaller path.
    private fun putPreferred(map: HashMap<String, File>, key: String, file: File) {
        if (key.isEmpty()) return
        val current = map[key]
        if (current == null || preferenceRank(file) < preferenceRank(current) ||
            (preferenceRank(file) == preferenceRank(current) && file.path < current.path)
        ) {
            map[key] = file
        }
    }

    private fun preferenceRank(file: File): Int =
        ACCEPTED_EXTENSIONS.indexOf(file.extension.lowercase(Locale.ROOT)).let { if (it < 0) Int.MAX_VALUE else it }

    companion object {
        // Search / preference order — index 0 wins on duplicate keys.
        val ACCEPTED_EXTENSIONS = listOf("png", "jpg", "jpeg", "webp")
        private const val MAX_DEPTH = 12
        // Guard against a user pointing at a huge tree (e.g. their whole Pictures drive).
        private const val MAX_FILES = 20_000
    }
}
