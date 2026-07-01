package com.vivicast.tv.data.playback

import com.vivicast.tv.core.player.PlaybackMediaType
import com.vivicast.tv.core.player.PlaybackOrigin
import com.vivicast.tv.core.player.PlaybackRequest
import com.vivicast.tv.core.player.PlaybackReturnTarget
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.Movie

/**
 * Builds [PlaybackRequest]s for on-demand media (Movie, Episode) by resolving the stream via
 * [PlaybackStreamResolver] and applying the resume-progress from [PlaybackRepository]. Pure data
 * layer: no player controller, no navigation, no Android/Compose types. The App host still owns
 * `playerController.play(...)` and the `onStarted` side effect.
 *
 * [clock] is injectable so the generated [playbackId] is deterministic in tests. Channel / catch-up
 * request building and progress writing stay App-hosted until P1-03c / P1-03d.
 */
class PlaybackRequestFactory(
    private val playbackStreamResolver: PlaybackStreamResolver,
    private val playbackRepository: PlaybackRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun movieRequest(
        movie: Movie,
        resumeProgress: Boolean,
        origin: PlaybackOrigin,
    ): PlaybackRequest? {
        val stream = playbackStreamResolver.resolve(
            PlaybackStreamRequest(
                providerId = movie.providerId,
                mediaId = movie.id,
                mediaType = MediaType.Movie,
                remoteId = movie.remoteId,
                containerExtension = movie.containerExtension,
            ),
        ).resolvedStreamOrNull() ?: return null
        val progress = if (resumeProgress) {
            playbackRepository.getProgress(movie.providerId, MediaType.Movie, movie.id)
                ?.takeUnless { it.isCompleted }
        } else {
            null
        }

        return PlaybackRequest(
            playbackId = playbackId(stream.providerId, stream.mediaType, stream.mediaId),
            providerId = stream.providerId,
            mediaId = stream.mediaId,
            mediaType = PlaybackMediaType.Movie,
            providerStableKey = stream.providerStableKey,
            mediaStableKey = movie.stableKey,
            origin = origin,
            returnTarget = PlaybackReturnTarget.MovieDetail,
            title = movie.name,
            streamUrl = stream.url,
            seekable = true,
            startPositionMillis = progress?.positionMillis ?: 0L,
        )
    }

    suspend fun episodeRequest(
        episode: Episode,
        origin: PlaybackOrigin,
    ): PlaybackRequest? {
        val stream = playbackStreamResolver.resolve(
            PlaybackStreamRequest(
                providerId = episode.providerId,
                mediaId = episode.id,
                mediaType = MediaType.Episode,
                remoteId = episode.remoteId,
                containerExtension = episode.containerExtension,
            ),
        ).resolvedStreamOrNull() ?: return null
        val progress = playbackRepository.getProgress(episode.providerId, MediaType.Episode, episode.id)
            ?.takeUnless { it.isCompleted }

        return PlaybackRequest(
            playbackId = playbackId(stream.providerId, stream.mediaType, stream.mediaId),
            providerId = stream.providerId,
            mediaId = stream.mediaId,
            mediaType = PlaybackMediaType.Episode,
            providerStableKey = stream.providerStableKey,
            mediaStableKey = episode.stableKey,
            origin = origin,
            returnTarget = PlaybackReturnTarget.SeriesDetail,
            title = episode.name,
            streamUrl = stream.url,
            seekable = true,
            startPositionMillis = progress?.positionMillis ?: 0L,
        )
    }

    private fun playbackId(providerId: String, mediaType: MediaType, mediaId: String): String =
        "$providerId:${mediaType.name.lowercase()}:$mediaId:${clock()}"
}

@Suppress("DEPRECATION")
private fun PlaybackStreamResult.resolvedStreamOrNull() =
    (this as? PlaybackStreamResult.Resolved)?.stream
