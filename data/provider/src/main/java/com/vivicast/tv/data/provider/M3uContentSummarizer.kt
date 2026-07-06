package com.vivicast.tv.data.provider

import com.vivicast.tv.iptv.m3u.DefaultM3uContentClassifier
import com.vivicast.tv.iptv.m3u.DefaultM3uParser
import com.vivicast.tv.iptv.m3u.M3uChannel
import com.vivicast.tv.iptv.m3u.M3uContentClassification
import com.vivicast.tv.iptv.m3u.M3uContentClassifier
import com.vivicast.tv.iptv.m3u.M3uParser

/** Preview counts for a provider's content: live channels, movies, and distinct series (not episodes). */
data class ContentSummary(
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
    fun summarize(content: String): ContentSummary {
        if (content.isBlank()) return ContentSummary()
        return summarizeChannels(parser.parse(content).channels)
    }

    /** Same counts from an already-parsed channel list — lets the connection test avoid a re-parse. */
    fun summarizeChannels(channels: List<M3uChannel>): ContentSummary {
        var channelCount = 0
        var movies = 0
        val seriesTitles = HashSet<String>()
        channels.forEach { channel ->
            when (val classification = classifier.classify(channel)) {
                is M3uContentClassification.LiveChannel -> channelCount++
                is M3uContentClassification.Movie -> movies++
                is M3uContentClassification.SeriesEpisode ->
                    seriesTitles.add(classification.info.seriesTitleRaw.trim().lowercase())
            }
        }
        return ContentSummary(channels = channelCount, movies = movies, series = seriesTitles.size)
    }
}
