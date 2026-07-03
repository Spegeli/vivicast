package com.vivicast.tv.iptv.m3u

/**
 * Conservative content classifier for already-parsed [M3uChannel] entries. It never mutates parser
 * output and never talks to Room/network — it only reads the fields the generic [M3uParser] produced
 * and decides whether an entry is Live-TV, a Movie, or a Series episode.
 *
 * Guiding rule: **when in doubt, Live**. URL path segments (`/live/`, `/movie/`, `/series/`) and
 * `group-title` are treated as optional, provider-specific hints — never as hard requirements — so a
 * `.mkv`/`.mp4` VOD entry is still recognised without a `/movie/` path, and a `.ts`/`.m3u8` or 24/7
 * channel stays Live even if its title contains words like "Cinema", "Film" or "Serie".
 */
interface M3uContentClassifier {
    fun classify(channel: M3uChannel): M3uContentClassification
}

sealed interface M3uContentClassification {
    /** Live-TV channel (also the conservative fallback for anything ambiguous). */
    object LiveChannel : M3uContentClassification

    /** Video-on-demand movie. */
    object Movie : M3uContentClassification

    /** A single episode of a series, with the season/episode info parsed from the title. */
    data class SeriesEpisode(val info: M3uSeriesEpisodeInfo) : M3uContentClassification
}

data class M3uSeriesEpisodeInfo(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val seriesTitleRaw: String,
    val episodeTitle: String?,
)

class DefaultM3uContentClassifier : M3uContentClassifier {

    override fun classify(channel: M3uChannel): M3uContentClassification {
        val title = (channel.tvgName ?: channel.name).trim()
        val group = channel.categoryName?.trim().orEmpty()
        val path = channel.streamUrl.sanitizedUrlPath()
        val extension = path.fileExtension()

        val hasVodExtension = extension in VOD_EXTENSIONS
        val seriesMatch = SERIES_REGEX.find(title) ?: ALT_SERIES_REGEX.find(title)
        val hasSeriesPattern = seriesMatch != null

        val strongLive = extension in LIVE_EXTENSIONS ||
            path.contains(LIVE_PATH) ||
            title.containsTwentyFourSeven() ||
            group.containsTwentyFourSeven()

        // 1. A strong Live signal (.ts/.m3u8, /live/, 24-7) wins — unless there is an unambiguously
        //    stronger VOD+series signal (a series episode delivered as a VOD file).
        if (strongLive && !(hasVodExtension && hasSeriesPattern)) {
            return M3uContentClassification.LiveChannel
        }

        // 2. Series pattern backed by a VOD extension, a /series/ path, or a series group hint.
        if (hasSeriesPattern && (hasVodExtension || path.contains(SERIES_PATH) || group.containsAny(SERIES_GROUP_HINTS))) {
            return seriesEpisode(title, requireNotNull(seriesMatch))
        }

        // 3. VOD extension without any series pattern → Movie (works without a /movie/ path).
        if (hasVodExtension) {
            return M3uContentClassification.Movie
        }

        // 4. Explicit /movie/ path and no Live signal → Movie.
        if (path.contains(MOVIE_PATH) && !strongLive) {
            return M3uContentClassification.Movie
        }

        // 5. Weak-only hints (or nothing at all) → Live (conservative fallback).
        return M3uContentClassification.LiveChannel
    }

    private fun seriesEpisode(title: String, match: MatchResult): M3uContentClassification.SeriesEpisode {
        val seriesTitleRaw = title.substring(0, match.range.first).trim().trim(*TITLE_SEPARATORS)
        val episodeTitle = title.substring(match.range.last + 1).trim().trim(*TITLE_SEPARATORS).ifBlank { null }
        return M3uContentClassification.SeriesEpisode(
            M3uSeriesEpisodeInfo(
                seasonNumber = match.groupInt("season"),
                episodeNumber = match.groupInt("episode"),
                seriesTitleRaw = seriesTitleRaw,
                episodeTitle = episodeTitle,
            ),
        )
    }

    private companion object {
        // Mandatory patterns: S01E01, S01 E01, S01.E01, S01-E01. Named groups season/episode.
        val SERIES_REGEX = Regex("""(?i)\bS(?<season>\d{1,2})\s*[-_. ]?\s*E(?<episode>\d{1,3})\b""")

        // Optional short form: 1x01. Same named groups so extraction is uniform.
        val ALT_SERIES_REGEX = Regex("""(?i)\b(?<season>\d{1,2})x(?<episode>\d{1,3})\b""")

        val VOD_EXTENSIONS = setOf("mkv", "mp4", "avi", "m4v", "mov", "webm")
        val LIVE_EXTENSIONS = setOf("ts", "m3u8")

        const val LIVE_PATH = "/live/"
        const val MOVIE_PATH = "/movie/"
        const val SERIES_PATH = "/series/"

        val SERIES_GROUP_HINTS = listOf("srs", "series", "serien", "staffel")

        val TITLE_SEPARATORS = charArrayOf('-', '_', '.', ' ')

        fun String.sanitizedUrlPath(): String {
            val trimmed = trim()
            if (trimmed.isEmpty()) return ""
            return trimmed.substringBefore('#').substringBefore('?').lowercase()
        }

        fun String.fileExtension(): String {
            val fileName = substringAfterLast('/')
            if (!fileName.contains('.')) return ""
            return fileName.substringAfterLast('.', "")
        }

        fun String.containsTwentyFourSeven(): Boolean {
            val lower = lowercase()
            return lower.contains("24/7") || lower.contains("24-7") || lower.contains("24x7")
        }

        fun String.containsAny(needles: List<String>): Boolean {
            val lower = lowercase()
            return needles.any { lower.contains(it) }
        }

        fun MatchResult.groupInt(name: String): Int =
            groups[name]?.value?.toIntOrNull() ?: 0
    }
}
