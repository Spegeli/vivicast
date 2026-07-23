package com.vivicast.tv.data.media

import androidx.room.withTransaction
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.model.ChannelHistoryEntity
import com.vivicast.tv.core.database.model.FavoriteEntity
import com.vivicast.tv.core.database.model.PlaybackProgressEntity
import com.vivicast.tv.domain.ids.UserDataIds
import com.vivicast.tv.domain.model.MediaType

/**
 * Binds restored-but-unbound user-data (favorites / playback progress / channel history) to the freshly
 * imported catalog after a playlist refresh.
 *
 * Backup restore writes these rows keyed by `stableKey` with `isPending = true`, because the catalog row ids
 * only exist once an import has run. This reconcile resolves each pending row's real catalog row id by
 * stableKey, rewrites `mediaId`/`channelId` + the PK to the live-write format ([UserDataIds] — a divergent PK
 * would let the next live write insert a DUPLICATE), and clears `isPending`. Unresolved rows stay pending
 * (the item may return in a later import — do not drop, or a transient/partial import loses user data).
 *
 * `isPending`-gated → a no-op on normal refreshes (empty selection). Must run AFTER the catalog merge has
 * committed. See plans/backup-restore-groups-lost.md.
 */
internal class PendingUserDataReconciler(
    private val database: VivicastDatabase,
) {
    private val favoritesDao = database.favoritesDao()
    private val playbackDao = database.playbackDao()
    private val catalogDao = database.catalogDao()

    suspend fun reconcile(providerId: String) {
        val favorites = favoritesDao.getPendingFavorites(providerId)
        val progress = playbackDao.getPendingProgress(providerId)
        val history = playbackDao.getPendingChannelHistory(providerId)
        if (favorites.isEmpty() && progress.isEmpty() && history.isEmpty()) return

        database.withTransaction {
            favorites.forEach { bindFavorite(providerId, it) }
            progress.forEach { bindProgress(providerId, it) }
            history.forEach { bindHistory(providerId, it) }
        }
    }

    private suspend fun bindFavorite(providerId: String, favorite: FavoriteEntity) {
        val mediaType = favorite.mediaType.toMediaType() ?: return
        val rowId = resolveRowId(providerId, favorite.mediaType, favorite.mediaStableKey) ?: return
        val corrected = favorite.copy(
            id = UserDataIds.favoriteId(providerId, mediaType, rowId),
            mediaId = rowId,
            isPending = false,
        )
        if (corrected.id != favorite.id) favoritesDao.deleteFavorite(favorite.id)
        favoritesDao.upsertFavorite(corrected)
    }

    private suspend fun bindProgress(providerId: String, progress: PlaybackProgressEntity) {
        val mediaType = progress.mediaType.toMediaType() ?: return
        val rowId = resolveRowId(providerId, progress.mediaType, progress.mediaStableKey) ?: return
        val corrected = progress.copy(
            id = UserDataIds.playbackProgressId(providerId, mediaType, rowId),
            mediaId = rowId,
            isPending = false,
        )
        if (corrected.id != progress.id) playbackDao.deleteProgressById(progress.id)
        playbackDao.upsertProgress(corrected)
    }

    private suspend fun bindHistory(providerId: String, history: ChannelHistoryEntity) {
        val rowId = catalogDao.findChannelIdByStableKey(providerId, history.channelStableKey) ?: return
        val corrected = history.copy(
            id = UserDataIds.channelHistoryId(providerId, rowId),
            channelId = rowId,
            isPending = false,
        )
        if (corrected.id != history.id) playbackDao.deleteChannelHistoryById(history.id)
        playbackDao.upsertChannelHistory(corrected)
    }

    private suspend fun resolveRowId(providerId: String, mediaType: String, stableKey: String): String? =
        when (mediaType) {
            MEDIA_TYPE_CHANNEL -> catalogDao.findChannelIdByStableKey(providerId, stableKey)
            MEDIA_TYPE_MOVIE -> catalogDao.findMovieIdByStableKey(providerId, stableKey)
            MEDIA_TYPE_SERIES -> catalogDao.findSeriesIdByStableKey(providerId, stableKey)
            MEDIA_TYPE_EPISODE -> catalogDao.findEpisodeIdByStableKey(providerId, stableKey)
            else -> null
        }

    private fun String.toMediaType(): MediaType? =
        when (this) {
            MEDIA_TYPE_CHANNEL -> MediaType.Channel
            MEDIA_TYPE_MOVIE -> MediaType.Movie
            MEDIA_TYPE_SERIES -> MediaType.Series
            MEDIA_TYPE_EPISODE -> MediaType.Episode
            else -> null
        }

    private companion object {
        const val MEDIA_TYPE_CHANNEL = "CHANNEL"
        const val MEDIA_TYPE_MOVIE = "MOVIE"
        const val MEDIA_TYPE_SERIES = "SERIES"
        const val MEDIA_TYPE_EPISODE = "EPISODE"
    }
}
