package com.vivicast.tv.system

import android.content.ContentValues
import android.content.Context
import android.media.tv.TvContract
import android.net.Uri
import android.os.Build
import com.vivicast.tv.core.security.PinSecurityState
import com.vivicast.tv.core.security.PinSecurityStateStore
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.playback.PlaybackRepository
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.PlaybackProgress
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderStatus
import com.vivicast.tv.domain.model.Series

class WatchNextSynchronizer(
    private val providerRepository: ProviderRepository,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val pinSecurityStateStore: PinSecurityStateStore,
    private val publisher: WatchNextPublisher,
) {
    suspend fun sync() {
        val pinState = pinSecurityStateStore.read()
        val progress = playbackRepository.getWatchNextProgress()
        publisher.sync(buildCandidates(progress, pinState))
    }

    private suspend fun buildCandidates(
        progressEntries: List<PlaybackProgress>,
        pinState: PinSecurityState,
    ): List<WatchNextCandidate> {
        val candidates = mutableListOf<WatchNextCandidate>()
        val publishedSeriesIds = mutableSetOf<String>()

        progressEntries.sortedByDescending { it.lastWatchedAt }.forEach { progress ->
            if (progress.isPending) return@forEach
            val provider = providerRepository.getProvider(progress.providerId) ?: return@forEach
            when (progress.mediaType) {
                MediaType.Movie -> addMovieCandidate(provider, progress, pinState, candidates)
                MediaType.Episode -> addEpisodeCandidate(provider, progress, pinState, candidates, publishedSeriesIds)
                MediaType.Channel,
                MediaType.Series -> Unit
            }
        }

        return candidates
    }

    private suspend fun addMovieCandidate(
        provider: Provider,
        progress: PlaybackProgress,
        pinState: PinSecurityState,
        candidates: MutableList<WatchNextCandidate>,
    ) {
        if (!provider.canPublishMovies() || progress.isCompleted) return
        val movie = mediaRepository.getMovie(provider.id, progress.mediaId) ?: return
        if (movie.isProtected(pinState)) return

        candidates += WatchNextCandidate(
            internalProviderId = watchNextId("MOVIE", provider.stableKey, movie.stableKey),
            title = movie.name,
            subtitle = movie.year ?: "Film",
            description = movie.plot,
            imageUri = movie.posterUrl ?: movie.backdropUrl,
            intentUri = stableDeepLink("movie", provider.stableKey, movie.stableKey),
            type = WatchNextContentType.Movie,
            kind = WatchNextKind.Continue,
            lastPlaybackPositionMillis = progress.positionMillis.coerceAtLeast(0L),
            durationMillis = progress.durationMillis.coerceAtLeast(0L),
            lastEngagementTimeMillis = progress.lastWatchedAt.coerceAtLeast(0L),
        )
    }

    private suspend fun addEpisodeCandidate(
        provider: Provider,
        progress: PlaybackProgress,
        pinState: PinSecurityState,
        candidates: MutableList<WatchNextCandidate>,
        publishedSeriesIds: MutableSet<String>,
    ) {
        if (!provider.canPublishSeries()) return
        val currentEpisode = mediaRepository.getEpisode(provider.id, progress.mediaId) ?: return
        val candidateEpisode = if (progress.isCompleted) {
            mediaRepository.getNextEpisode(currentEpisode) ?: return
        } else {
            currentEpisode
        }
        if (!publishedSeriesIds.add(candidateEpisode.seriesId)) return
        val series = mediaRepository.getSeries(provider.id, candidateEpisode.seriesId)
        if (candidateEpisode.isProtected(series, pinState)) return

        candidates += WatchNextCandidate(
            internalProviderId = watchNextId("EPISODE", provider.stableKey, candidateEpisode.stableKey),
            title = series?.name ?: candidateEpisode.name,
            subtitle = "S${candidateEpisode.seasonNumber}E${candidateEpisode.episodeNumber} ${candidateEpisode.name}",
            description = candidateEpisode.plot ?: series?.plot,
            imageUri = candidateEpisode.thumbnailUrl ?: series?.posterUrl ?: series?.backdropUrl,
            intentUri = stableDeepLink("episode", provider.stableKey, candidateEpisode.stableKey),
            type = WatchNextContentType.TvEpisode,
            kind = if (progress.isCompleted) WatchNextKind.Next else WatchNextKind.Continue,
            lastPlaybackPositionMillis = if (progress.isCompleted) 0L else progress.positionMillis.coerceAtLeast(0L),
            durationMillis = if (progress.isCompleted) 0L else progress.durationMillis.coerceAtLeast(0L),
            lastEngagementTimeMillis = progress.lastWatchedAt.coerceAtLeast(0L),
            seasonNumber = candidateEpisode.seasonNumber,
            episodeNumber = candidateEpisode.episodeNumber,
            seriesId = series?.stableKey ?: candidateEpisode.seriesId,
        )
    }
}

data class WatchNextCandidate(
    val internalProviderId: String,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val imageUri: String?,
    val intentUri: String,
    val type: WatchNextContentType,
    val kind: WatchNextKind,
    val lastPlaybackPositionMillis: Long,
    val durationMillis: Long,
    val lastEngagementTimeMillis: Long,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val seriesId: String? = null,
)

enum class WatchNextContentType {
    Movie,
    TvEpisode,
}

enum class WatchNextKind {
    Continue,
    Next,
}

interface WatchNextPublisher {
    suspend fun sync(candidates: List<WatchNextCandidate>)
}

class AndroidTvWatchNextPublisher(
    context: Context,
) : WatchNextPublisher {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun sync(candidates: List<WatchNextCandidate>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        runCatching {
            resolver.delete(
                TvContract.WatchNextPrograms.CONTENT_URI,
                "${TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID} LIKE ?",
                arrayOf("$WATCH_NEXT_ID_PREFIX%"),
            )
            candidates.forEach { candidate ->
                resolver.insert(TvContract.WatchNextPrograms.CONTENT_URI, candidate.toContentValues())
            }
        }
    }

    private fun WatchNextCandidate.toContentValues(): ContentValues =
        ContentValues().apply {
            put(TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID, internalProviderId)
            put(TvContract.WatchNextPrograms.COLUMN_TITLE, title)
            put(TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION, subtitle)
            description?.let { put(TvContract.WatchNextPrograms.COLUMN_LONG_DESCRIPTION, it) }
            imageUri?.let {
                put(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI, it)
                put(TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI, it)
            }
            put(TvContract.WatchNextPrograms.COLUMN_INTENT_URI, intentUri)
            put(TvContract.WatchNextPrograms.COLUMN_TYPE, type.toTvProgramType())
            put(TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE, kind.toTvWatchNextType())
            put(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, lastPlaybackPositionMillis)
            if (durationMillis > 0L) put(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS, durationMillis)
            if (lastEngagementTimeMillis > 0L) {
                put(TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, lastEngagementTimeMillis)
            }
            seriesId?.let { put(TvContract.WatchNextPrograms.COLUMN_SERIES_ID, it) }
            seasonNumber?.let { put(TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER, it.toString()) }
            episodeNumber?.let { put(TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER, it.toString()) }
        }

}

private fun Movie.isProtected(pinState: PinSecurityState): Boolean =
    pinState.hasPin && (pinState.protectMovies || (pinState.protectAdultContent && isAdult))

private fun Episode.isProtected(series: Series?, pinState: PinSecurityState): Boolean =
    pinState.hasPin && (
        pinState.protectSeries ||
            (pinState.protectAdultContent && (isAdult || series?.isAdult == true))
        )

private fun Provider.canPublishMovies(): Boolean =
    isActive && includeMovies && status.canPublishSystemEntries()

private fun Provider.canPublishSeries(): Boolean =
    isActive && includeSeries && status.canPublishSystemEntries()

private fun ProviderStatus.canPublishSystemEntries(): Boolean =
    this == ProviderStatus.Active ||
        this == ProviderStatus.ActiveWithPartialErrors ||
        this == ProviderStatus.Refreshing

private fun WatchNextContentType.toTvProgramType(): Int =
    when (this) {
        WatchNextContentType.Movie -> TvContract.WatchNextPrograms.TYPE_MOVIE
        WatchNextContentType.TvEpisode -> TvContract.WatchNextPrograms.TYPE_TV_EPISODE
    }

private fun WatchNextKind.toTvWatchNextType(): Int =
    when (this) {
        WatchNextKind.Continue -> TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE
        WatchNextKind.Next -> TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT
    }

private fun watchNextId(mediaType: String, providerStableKey: String, mediaStableKey: String): String =
    "$WATCH_NEXT_ID_PREFIX$mediaType:$providerStableKey:$mediaStableKey"

private fun stableDeepLink(host: String, providerStableKey: String, mediaStableKey: String): String =
    Uri.Builder()
        .scheme("vivicast")
        .authority(host)
        .appendPath(providerStableKey)
        .appendPath(mediaStableKey)
        .build()
        .toString()

private const val WATCH_NEXT_ID_PREFIX = "vivicast:"
