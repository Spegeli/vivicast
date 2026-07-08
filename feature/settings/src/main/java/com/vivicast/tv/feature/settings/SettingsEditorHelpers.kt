package com.vivicast.tv.feature.settings

/**
 * Small pure helpers shared by the playlist and EPG source editors/panels — the duplicate-name and
 * source-URL comparisons were copy-pasted between the two sides.
 */

/**
 * Whether [candidate] collides (case-insensitively, trimmed) with another item's name. [currentId]
 * excludes the item being edited so re-saving it with its own name is not a duplicate.
 */
internal fun <T> isDuplicateNameOf(
    candidate: String,
    currentId: String?,
    items: List<T>,
    id: (T) -> String?,
    name: (T) -> String,
): Boolean {
    val trimmed = candidate.trim()
    if (trimmed.isEmpty()) return false
    return items.any { id(it) != currentId && name(it).trim().equals(trimmed, ignoreCase = true) }
}

/**
 * Normalized source URL for duplicate detection: trimmed, lower-cased, trailing slash and the
 * compression suffix dropped, so e.g. `epg-de.xml` / `epg-de.xml.gz` / a trailing-slash variant count
 * as the same URL. Used for both M3U and EPG source URLs.
 */
internal fun normalizeSourceUrl(url: String): String =
    url.trim().lowercase().removeSuffix("/").removeSuffix(".gz").removeSuffix(".xz")
