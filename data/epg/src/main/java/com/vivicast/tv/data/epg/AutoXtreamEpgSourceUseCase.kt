package com.vivicast.tv.data.epg

import kotlinx.coroutines.flow.first
import java.net.URI
import java.net.URLDecoder

/**
 * Creates (or reuses) and links an EPG source for a just-saved Xtream provider whose `xmltv.php`
 * endpoint has already been validated by the caller. Pure of OkHttp/Android — the network reachability
 * check runs App-side before this; here we only touch the repository, so it is unit-testable with a fake.
 *
 * Identity for dedup is **server + username** (NOT the display name, which the user may rename): the same
 * account maps to the same source even after a password rotation. On a match the existing source's URL is
 * refreshed while its name / isActive / timeShift are preserved; otherwise a new source is created,
 * display-named after the username (suffixed `#1`, `#2`, … when that name is taken, because
 * [EpgSourceRepository.saveSource] rejects duplicate names). The source is linked to the provider at the
 * next free priority.
 */
class AutoXtreamEpgSourceUseCase(
    private val repository: EpgSourceRepository,
) {
    /**
     * @param xmltvUrl the built (and already-validated) `xmltv.php` URL — also the dedup identity source.
     * @return the created/reused source id + whether it was reused (so the caller can enqueue a refresh
     *   and log the outcome), or null if nothing was written.
     */
    suspend fun ensureFor(providerId: String, username: String, xmltvUrl: String): AutoXtreamEpgResult? {
        if (providerId.isBlank() || username.isBlank() || xmltvUrl.isBlank()) return null

        val sources = repository.getEpgSources()
        val match = sources.firstOrNull { source ->
            sameEndpoint(repository.getSourceUrl(source.id), xmltvUrl)
        }

        val sourceId = if (match != null) {
            // Reuse: refresh the URL (password may have rotated) but preserve the user's settings.
            repository.saveSource(
                EpgSourceEditRequest(
                    sourceId = match.id,
                    name = match.name,
                    url = xmltvUrl,
                    timeShiftMinutes = match.timeShiftMinutes,
                    isActive = match.isActive,
                ),
            ).id
        } else {
            repository.saveSource(
                EpgSourceEditRequest(
                    name = uniqueName(username.trim(), sources.map { it.name }),
                    url = xmltvUrl,
                ),
            ).id
        }

        repository.linkSourceToProvider(providerId, sourceId, nextPriority(providerId, sourceId))
        return AutoXtreamEpgResult(epgSourceId = sourceId, reused = match != null)
    }

    private suspend fun nextPriority(providerId: String, sourceId: String): Int {
        val links = repository.observeProviderEpgSources(providerId).first()
        // Already linked (rare reuse case): keep its slot. Else append after the current max — max+1, not
        // count+1, so a non-contiguous priority set can't collide with the unique (providerId, priority).
        links.firstOrNull { it.epgSourceId == sourceId }?.let { return it.priority }
        return (links.maxOfOrNull { it.priority } ?: 0) + 1
    }

    private companion object {
        /** First free name in `base`, `base #1`, `base #2`, … (case-insensitive vs [taken]). */
        fun uniqueName(base: String, taken: List<String>): String {
            val existing = taken.map { it.trim().lowercase() }.toSet()
            val name = base.ifBlank { "Xtream EPG" }
            if (name.lowercase() !in existing) return name
            var i = 1
            while ("$name #$i".lowercase() in existing) i++
            return "$name #$i"
        }

        /**
         * Same Xtream endpoint = same host (case-insensitive) + port + path + `username` query. Scheme is
         * ignored (http/https on one host = same server). Non-xmltv URLs (no username query) never match.
         * ponytail: URLDecoder maps `+`→space; a manually-typed URL using `+` for a literal plus could
         * mismatch — auto-built URLs use `%2B`/`%20`, so this only bites hand-entered edge cases.
         */
        fun sameEndpoint(storedUrl: String?, candidateUrl: String): Boolean {
            val a = storedUrl?.let(::parseEndpoint) ?: return false
            val b = parseEndpoint(candidateUrl) ?: return false
            return a.host.equals(b.host, ignoreCase = true) &&
                a.port == b.port &&
                a.path == b.path &&
                a.username != null && a.username == b.username
        }

        fun parseEndpoint(url: String): Endpoint? = runCatching {
            val uri = URI(url.trim())
            val host = uri.host ?: return null
            Endpoint(host = host, port = uri.port, path = uri.path.orEmpty(), username = uri.rawQuery.username())
        }.getOrNull()

        fun String?.username(): String? =
            this?.split('&')?.firstNotNullOfOrNull { pair ->
                val idx = pair.indexOf('=')
                if (idx >= 0 && pair.substring(0, idx) == "username") {
                    URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                } else {
                    null
                }
            }
    }

    private data class Endpoint(val host: String, val port: Int, val path: String, val username: String?)
}

/** Outcome of [AutoXtreamEpgSourceUseCase.ensureFor]: the linked EPG source id + whether it was reused. */
data class AutoXtreamEpgResult(val epgSourceId: String, val reused: Boolean)
