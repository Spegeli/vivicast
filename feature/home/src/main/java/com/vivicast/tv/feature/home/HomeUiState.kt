package com.vivicast.tv.feature.home

import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.ChannelHistory
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.PlaybackProgress

/**
 * Immutable presentation state for the home screen. Holds the enriched
 * "continue watching" and "recent channels" data resolved from the repositories.
 * Localized strings, image loading and focus/selection handling stay in the UI.
 */
internal data class HomeUiState(
    val continueItems: List<ContinueHomeItem> = emptyList(),
    val recentChannels: List<RecentChannelHomeItem> = emptyList(),
)

internal sealed interface ContinueHomeItem {
    val id: String
    val title: String
    val meta: String
    val progress: PlaybackProgress
    val hasImage: Boolean
    val imageSourceKey: String?

    data class MovieItem(
        override val progress: PlaybackProgress,
        val movie: Movie,
    ) : ContinueHomeItem {
        override val id: String = "movie:${movie.providerId}:${movie.id}"
        override val title: String = movie.name
        override val meta: String = "${progress.progressPercent} %"
        override val hasImage: Boolean = !movie.posterUrl.isNullOrBlank()
        override val imageSourceKey: String? = movie.posterUrl
    }

    data class EpisodeItem(
        override val progress: PlaybackProgress,
        val episode: Episode,
    ) : ContinueHomeItem {
        override val id: String = "episode:${episode.providerId}:${episode.id}"
        override val title: String = episode.name
        override val meta: String = "${progress.progressPercent} % | S${episode.seasonNumber}E${episode.episodeNumber}"
        override val hasImage: Boolean = !episode.thumbnailUrl.isNullOrBlank()
        override val imageSourceKey: String? = episode.thumbnailUrl
    }
}

internal data class RecentChannelHomeItem(
    val history: ChannelHistory,
    val channel: Channel,
)
