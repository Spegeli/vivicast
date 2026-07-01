package com.vivicast.tv.data.playback

import com.vivicast.tv.core.player.PlaybackMediaType
import com.vivicast.tv.core.player.PlaybackStatus
import com.vivicast.tv.core.player.VivicastPlayerState
import com.vivicast.tv.domain.model.ChannelHistory
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.PlaybackProgress

/**
 * Writes playback progress / channel history from the current [VivicastPlayerState], applying the
 * automatic-save rules from [PlaybackProgressRules]. Pure data layer: no player controller, no
 * WatchNext, no Android/Compose types. The App keeps owning the player-state loop, the WatchNext
 * trigger and the [automaticProgressSaveTimes] throttle map (passed in unchanged).
 *
 * [clock] is injectable so the written timestamps are deterministic in tests. ID formats are kept
 * byte-for-byte identical to the previous App implementation to preserve existing DB / resume keys.
 */
class PlaybackProgressRecorder(
    private val playbackRepository: PlaybackRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun record(
        state: VivicastPlayerState,
        automaticProgressSaveTimes: MutableMap<String, Long>,
        forceSave: Boolean = false,
    ) {
        val request = state.request ?: return

        if (request.mediaType == PlaybackMediaType.Channel) {
            if (state.status != PlaybackStatus.Playing && state.status != PlaybackStatus.Paused) return
            val now = clock()
            playbackRepository.saveChannelHistory(
                ChannelHistory(
                    id = channelHistoryId(request.providerId, request.mediaId),
                    providerId = request.providerId,
                    channelId = request.mediaId,
                    watchedAt = now,
                    durationWatchedMillis = state.positionMillis.coerceAtLeast(0L),
                    updatedAt = now,
                    channelStableKey = request.mediaStableKey,
                ),
            )
            return
        }

        val mediaType = request.mediaType.toDomainProgressMediaType() ?: return
        val mediaEnded = state.status == PlaybackStatus.Ended
        if (state.status != PlaybackStatus.Playing && state.status != PlaybackStatus.Paused && !mediaEnded) return
        val positionMillis = state.positionMillis.coerceAtLeast(0L)
        val durationMillis = state.durationMillis.coerceAtLeast(0L)

        val now = clock()
        val existing = playbackRepository.getProgress(request.providerId, mediaType, request.mediaId)
        if (!shouldSaveAutomaticPlaybackProgress(
                existing = existing,
                lastSavedAtMillis = automaticProgressSaveTimes[request.playbackId],
                nowMillis = now,
                positionMillis = positionMillis,
                durationMillis = durationMillis,
                force = state.status == PlaybackStatus.Paused || mediaEnded || forceSave,
                allowCreateBelowMinimum = mediaEnded,
            )
        ) {
            return
        }

        val progressPercent = automaticPlaybackProgressPercent(positionMillis, durationMillis)
        playbackRepository.saveProgress(
            PlaybackProgress(
                id = existing?.id ?: playbackProgressId(request.providerId, mediaType, request.mediaId),
                providerId = request.providerId,
                mediaType = mediaType,
                mediaId = request.mediaId,
                positionMillis = positionMillis,
                durationMillis = durationMillis,
                progressPercent = progressPercent,
                isCompleted = existing?.isCompleted == true ||
                    mediaEnded ||
                    progressPercent >= PLAYBACK_COMPLETION_THRESHOLD_PERCENT,
                lastWatchedAt = now,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                mediaStableKey = request.mediaStableKey,
            ),
        )
        automaticProgressSaveTimes[request.playbackId] = now
    }
}

private fun playbackProgressId(providerId: String, mediaType: MediaType, mediaId: String): String =
    "$providerId:progress:${mediaType.name.lowercase()}:$mediaId"

private fun channelHistoryId(providerId: String, channelId: String): String =
    "$providerId:history:channel:$channelId"

private fun PlaybackMediaType.toDomainProgressMediaType(): MediaType? =
    when (this) {
        PlaybackMediaType.Movie -> MediaType.Movie
        PlaybackMediaType.Episode -> MediaType.Episode
        PlaybackMediaType.Channel,
        PlaybackMediaType.CatchUp -> null
    }
