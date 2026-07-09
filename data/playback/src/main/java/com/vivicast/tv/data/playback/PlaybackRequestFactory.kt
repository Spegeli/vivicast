package com.vivicast.tv.data.playback

import com.vivicast.tv.core.player.PlaybackMediaType
import com.vivicast.tv.core.player.PlaybackOrigin
import com.vivicast.tv.core.player.PlaybackRequest
import com.vivicast.tv.core.player.PlaybackReturnTarget
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgProgram
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.Movie

/**
 * Builds [PlaybackRequest]s for on-demand media (Movie, Episode) by resolving the stream via
 * [PlaybackStreamResolver] and applying the resume-progress from [PlaybackRepository]. Pure data
 * layer: no player controller, no navigation, no Android/Compose types. The App host still owns
 * `playerController.play(...)` and the `onStarted` side effect.
 *
 * [clock] is injectable so the generated [playbackId] and the catch-up window guard are
 * deterministic in tests. Live channels are always seekable — the player controller auto-detects a
 * native DVR window at playback; no timeshift preference is threaded in. Progress writing is P1-03d.
 */
class PlaybackRequestFactory(
    private val playbackStreamResolver: PlaybackStreamResolver,
    private val playbackRepository: PlaybackRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
    // Per-provider User-Agent for playback; null/blank → the player uses the global User-Agent.
    private val providerUserAgent: suspend (providerId: String) -> String? = { null },
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
            userAgent = providerUserAgent(stream.providerId),
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
            userAgent = providerUserAgent(stream.providerId),
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

    suspend fun channelRequest(
        channel: Channel,
        origin: PlaybackOrigin,
    ): PlaybackRequest? {
        val stream = playbackStreamResolver.resolve(
            PlaybackStreamRequest(
                providerId = channel.providerId,
                mediaId = channel.id,
                mediaType = MediaType.Channel,
                remoteId = channel.remoteId,
            ),
        ).resolvedStreamOrNull() ?: return null

        return PlaybackRequest(
            playbackId = playbackId(stream.providerId, stream.mediaType, stream.mediaId),
            providerId = stream.providerId,
            userAgent = providerUserAgent(stream.providerId),
            mediaId = stream.mediaId,
            mediaType = PlaybackMediaType.Channel,
            providerStableKey = stream.providerStableKey,
            mediaStableKey = channel.stableKey,
            origin = origin,
            returnTarget = PlaybackReturnTarget.LiveTv,
            title = channel.name,
            streamUrl = stream.url,
            // Live channels are always seekable; the controller auto-detects a native DVR window at playback.
            seekable = true,
        )
    }

    suspend fun catchUpRequest(
        channel: Channel,
        program: EpgProgram,
        origin: PlaybackOrigin,
    ): PlaybackRequest? {
        if (!channel.canStartCatchUp(program, nowMillis = clock())) return null
        val stream = playbackStreamResolver.resolve(
            PlaybackStreamRequest(
                providerId = channel.providerId,
                mediaId = channel.id,
                mediaType = MediaType.Channel,
                remoteId = channel.remoteId,
                catchupStartMillis = program.startTime,
                catchupEndMillis = program.endTime,
            ),
        ).resolvedStreamOrNull() ?: return null

        return PlaybackRequest(
            playbackId = playbackId(stream.providerId, stream.mediaType, stream.mediaId),
            providerId = stream.providerId,
            userAgent = providerUserAgent(stream.providerId),
            mediaId = stream.mediaId,
            mediaType = PlaybackMediaType.CatchUp,
            providerStableKey = stream.providerStableKey,
            mediaStableKey = channel.stableKey,
            origin = origin,
            returnTarget = PlaybackReturnTarget.LiveTv,
            title = "${channel.name} - ${program.title}",
            streamUrl = stream.url,
            seekable = true,
            epgProgramStableKey = program.stableKey,
        )
    }

    private fun playbackId(providerId: String, mediaType: MediaType, mediaId: String): String =
        "$providerId:${mediaType.name.lowercase()}:$mediaId:${clock()}"
}

private const val MILLIS_PER_DAY = 86_400_000L

private fun Channel.canStartCatchUp(program: EpgProgram, nowMillis: Long): Boolean {
    if (!isCatchupAvailable || !program.isCatchupAvailable) return false
    if (providerId != program.providerId || id != program.channelId) return false
    if (program.startTime >= program.endTime || program.endTime > nowMillis) return false
    if (catchupDays <= 0) return false
    val earliestAllowedStart = nowMillis - catchupDays * MILLIS_PER_DAY
    return program.startTime >= earliestAllowedStart
}

@Suppress("DEPRECATION")
private fun PlaybackStreamResult.resolvedStreamOrNull() =
    (this as? PlaybackStreamResult.Resolved)?.stream
