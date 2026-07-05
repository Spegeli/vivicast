package com.vivicast.tv.data.provider

import com.vivicast.tv.iptv.m3u.DefaultM3uContentClassifier
import com.vivicast.tv.iptv.m3u.DefaultM3uParser
import com.vivicast.tv.iptv.m3u.M3uContentClassification
import com.vivicast.tv.iptv.m3u.M3uContentClassifier
import com.vivicast.tv.iptv.m3u.M3uParser

/** Preview counts for raw M3U content: live channels, movies, and distinct series (not episodes). */
data class M3uContentSummary(
    val channels: Int = 0,
    val movies: Int = 0,
    val series: Int = 0,
)

/**
 * Parses + classifies raw M3U content into a channels/movies/series preview count using the same
 * [M3uContentClassifier] the import uses, so the add dialog shows what will actually land in each tab.
 * Series are counted as distinct series (by normalized title), not episodes.
 *
 * Synchronous and O(entries) with a regex per entry — memoize or call off the main thread for large
 * playlists (tens of thousands of entries).
 */
class M3uContentSummarizer(
    private val parser: M3uParser = DefaultM3uParser(),
    private val classifier: M3uContentClassifier = DefaultM3uContentClassifier(),
) {
    fun summarize(content: String): M3uContentSummary {
        if (content.isBlank()) return M3uContentSummary()
        var channels = 0
        var movies = 0
        val seriesTitles = HashSet<String>()
        parser.parse(content).channels.forEach { channel ->
            when (val classification = classifier.classify(channel)) {
                is M3uContentClassification.LiveChannel -> channels++
                is M3uContentClassification.Movie -> movies++
                is M3uContentClassification.SeriesEpisode ->
                    seriesTitles.add(classification.info.seriesTitleRaw.trim().lowercase())
            }
        }
        return M3uContentSummary(channels = channels, movies = movies, series = seriesTitles.size)
    }
}
