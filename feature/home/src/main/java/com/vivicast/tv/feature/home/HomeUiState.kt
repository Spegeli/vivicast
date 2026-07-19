package com.vivicast.tv.feature.home

import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.ChannelHistory
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.PlaybackProgress
import com.vivicast.tv.domain.model.Series

/**
 * Immutable presentation state for the home screen. Home is only rows (no hero): per-type "resume" rows for
 * movies and series plus a recent-channels row. Each row is shown only when its content type exists in an
 * active playlist; [emptyReason] is set only when no row is shown at all. [loaded] gates the very first
 * frame so the empty state never flashes before the repositories emit.
 *
 * Localized strings, image loading and focus/selection handling stay in the UI.
 */
internal data class HomeUiState(
    val loaded: Boolean = false,
    val movieItems: List<MovieContinueHomeItem> = emptyList(),
    val seriesItems: List<SeriesContinueHomeItem> = emptyList(),
    val recentChannels: List<RecentChannelHomeItem> = emptyList(),
    val hasLive: Boolean = false,
    val hasMovies: Boolean = false,
    val hasSeries: Boolean = false,
    val emptyReason: HomeEmptyReason? = null,
)

/** Why Home shows the global empty state (only when no per-type row is shown). */
internal enum class HomeEmptyReason {
    /** No playlists at all. */
    NoPlaylist,

    /** Playlists exist but all are disabled. */
    AllDisabled,

    /** At least one active playlist, but no catalog content of any type yet (fresh/empty import). */
    EmptyCatalog,
}

/** An in-progress movie to resume. */
internal data class MovieContinueHomeItem(
    val progress: PlaybackProgress,
    val movie: Movie,
) {
    val id: String = "movie:${movie.providerId}:${movie.id}"
    val title: String = movie.name
    val hasImage: Boolean = !movie.posterUrl.isNullOrBlank()
}

/**
 * A series to resume (series-centric): [episode] is the resume target — the in-progress episode, or the
 * next episode (with [progressPercent] = 0) when the last relevant one was completed.
 */
internal data class SeriesContinueHomeItem(
    val series: Series,
    val episode: Episode,
    val progressPercent: Int,
) {
    val id: String = "series:${series.providerId}:${series.id}"
    val title: String = series.name
    val hasImage: Boolean = !series.posterUrl.isNullOrBlank()
}

internal data class RecentChannelHomeItem(
    val history: ChannelHistory,
    val channel: Channel,
)
