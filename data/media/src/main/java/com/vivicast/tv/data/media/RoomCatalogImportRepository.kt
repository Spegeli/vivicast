package com.vivicast.tv.data.media

import androidx.room.withTransaction
import com.vivicast.tv.core.cache.M3uStreamReference
import com.vivicast.tv.core.cache.M3uStreamReferenceStore
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.model.CategoryEntity
import com.vivicast.tv.core.database.model.ChannelEntity
import com.vivicast.tv.core.database.model.EpisodeEntity
import com.vivicast.tv.core.database.model.MovieEntity
import com.vivicast.tv.core.database.model.SeasonEntity
import com.vivicast.tv.core.database.model.SeriesEntity
import com.vivicast.tv.iptv.m3u.DefaultM3uContentClassifier
import com.vivicast.tv.iptv.m3u.M3uChannel
import com.vivicast.tv.iptv.m3u.M3uContentClassifier
import com.vivicast.tv.iptv.m3u.M3uPlaylist
import com.vivicast.tv.iptv.xtream.XtreamCategory
import com.vivicast.tv.iptv.xtream.XtreamEpisode
import com.vivicast.tv.iptv.xtream.XtreamLiveStream
import com.vivicast.tv.iptv.xtream.XtreamSeason
import com.vivicast.tv.iptv.xtream.XtreamSeriesInfo
import com.vivicast.tv.iptv.xtream.XtreamSeriesItem
import com.vivicast.tv.iptv.xtream.XtreamVodItem
import java.security.MessageDigest

class RoomCatalogImportRepository(
    private val database: VivicastDatabase,
    private val m3uStreamReferenceStore: M3uStreamReferenceStore,
    private val classifier: M3uContentClassifier = DefaultM3uContentClassifier(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : CatalogImportRepository {
    private val catalogDao = database.catalogDao()
    private val favoritesDao = database.favoritesDao()
    private val playbackDao = database.playbackDao()
    private val epgDao = database.epgDao()
    private val androidTvSearchDao = database.androidTvSearchDao()

    override suspend fun importM3uLiveChannels(providerId: String, playlist: M3uPlaylist): CatalogImportResult =
        importM3uCatalog(providerId, playlist)

    override suspend fun importM3uCatalog(providerId: String, playlist: M3uPlaylist): CatalogImportResult {
        val now = clock()
        val classified = classifyM3uPlaylist(playlist, classifier)
        val liveCategoryIds = classified.liveChannels.mapTo(mutableSetOf()) { it.categoryRemoteId() }
        val movieCategoryIds = classified.vodItems.mapTo(mutableSetOf()) { it.categoryRemoteId() }
        val seriesCategoryIds = classified.seriesItems.mapTo(mutableSetOf()) { it.categoryRemoteId() }

        val result = database.withTransaction {
            val liveCategories = buildCategories(providerId, CATEGORY_TYPE_LIVE, m3uCategories(liveCategoryIds), liveCategoryIds, now)
            val movieCategories = buildCategories(providerId, CATEGORY_TYPE_MOVIE, m3uCategories(movieCategoryIds), movieCategoryIds, now)
            val seriesCategories = buildCategories(providerId, CATEGORY_TYPE_SERIES, m3uCategories(seriesCategoryIds), seriesCategoryIds, now)

            val existingChannels = catalogDao.getChannels(providerId).associateBy { it.remoteId }
            val channels = classified.liveChannels.map { channel ->
                channel.toEntity(
                    providerId = providerId,
                    categoryId = categoryId(providerId, CATEGORY_TYPE_LIVE, channel.categoryRemoteId()),
                    existing = existingChannels[channel.remoteId],
                    now = now,
                )
            }
            val channelCount = upsertChannels(providerId, channels, existingChannels)
            upsertMovies(providerId, classified.vodItems, now)
            upsertSeries(providerId, classified.seriesItems, now)
            val seriesByRemoteId = catalogDao.getSeries(providerId).associateBy { it.remoteId }
            upsertSeasons(providerId, buildSeasons(providerId, classified.seriesInfos, seriesByRemoteId, now))
            upsertEpisodes(providerId, buildEpisodes(providerId, classified.seriesInfos, seriesByRemoteId, now))

            deleteRemovedCategories(providerId, CATEGORY_TYPE_LIVE, liveCategories.removedIds)
            deleteRemovedCategories(providerId, CATEGORY_TYPE_MOVIE, movieCategories.removedIds)
            deleteRemovedCategories(providerId, CATEGORY_TYPE_SERIES, seriesCategories.removedIds)
            androidTvSearchDao.rebuildEntries()

            CatalogImportResult(
                categoriesAdded = liveCategories.count.added,
                categoriesUpdated = liveCategories.count.updated,
                categoriesRemoved = liveCategories.count.removed,
                channelsAdded = channelCount.added,
                channelsUpdated = channelCount.updated,
                channelsRemoved = channelCount.removed,
                skippedEntries = playlist.skippedEntries,
            )
        }
        m3uStreamReferenceStore.replaceProviderReferences(providerId, classified.streamReferences)
        return result
    }

    private fun m3uCategories(remoteIds: Set<String>): List<XtreamCategory> =
        remoteIds.map { remoteId ->
            XtreamCategory(
                remoteId = remoteId,
                name = if (remoteId == UNCATEGORIZED_REMOTE_ID) UNCATEGORIZED_DISPLAY_NAME else remoteId,
            )
        }

    override suspend fun importXtreamCatalog(providerId: String, catalog: XtreamCatalog): XtreamCatalogImportResult {
        val now = clock()
        return database.withTransaction {
            val liveCategories = buildCategories(
                providerId = providerId,
                type = CATEGORY_TYPE_LIVE,
                categories = catalog.liveCategories,
                referencedRemoteIds = catalog.liveStreams.mapTo(mutableSetOf()) { it.categoryRemoteId() },
                now = now,
            )
            val movieCategories = buildCategories(
                providerId = providerId,
                type = CATEGORY_TYPE_MOVIE,
                categories = catalog.vodCategories,
                referencedRemoteIds = catalog.vodItems.mapTo(mutableSetOf()) { it.categoryRemoteId() },
                now = now,
            )
            val seriesCategories = buildCategories(
                providerId = providerId,
                type = CATEGORY_TYPE_SERIES,
                categories = catalog.seriesCategories,
                referencedRemoteIds = catalog.seriesItems.mapTo(mutableSetOf()) { it.categoryRemoteId() },
                now = now,
            )

            val existingChannels = catalogDao.getChannels(providerId).associateBy { it.remoteId }
            val channelCount = upsertChannels(
                providerId = providerId,
                channels = catalog.liveStreams
                    .associateBy { it.remoteId }
                    .values
                    .map { stream -> stream.toEntity(providerId, existingChannels[stream.remoteId], now) },
                existingChannels = existingChannels,
            )
            val movieCount = upsertMovies(providerId, catalog.vodItems.associateBy { it.remoteId }.values.toList(), now)
            val seriesCount = upsertSeries(providerId, catalog.seriesItems.associateBy { it.remoteId }.values.toList(), now)
            // Season/episode detail is imported separately via importXtreamSeriesDetails (background job).
            // When seriesInfos is empty (the fast main refresh), leave existing seasons/episodes untouched
            // instead of reconciling them away.
            val seriesByRemoteId = catalogDao.getSeries(providerId).associateBy { it.remoteId }
            val seasonCount: ImportCount
            val episodeCount: ImportCount
            if (catalog.seriesInfos.isEmpty()) {
                seasonCount = ImportCount(added = 0, updated = 0, removed = 0)
                episodeCount = ImportCount(added = 0, updated = 0, removed = 0)
            } else {
                seasonCount = upsertSeasons(providerId, buildSeasons(providerId, catalog.seriesInfos, seriesByRemoteId, now))
                episodeCount = upsertEpisodes(providerId, buildEpisodes(providerId, catalog.seriesInfos, seriesByRemoteId, now))
            }

            deleteRemovedCategories(providerId, CATEGORY_TYPE_LIVE, liveCategories.removedIds)
            deleteRemovedCategories(providerId, CATEGORY_TYPE_MOVIE, movieCategories.removedIds)
            deleteRemovedCategories(providerId, CATEGORY_TYPE_SERIES, seriesCategories.removedIds)
            androidTvSearchDao.rebuildEntries()

            XtreamCatalogImportResult(
                liveCategories = liveCategories.count,
                movieCategories = movieCategories.count,
                seriesCategories = seriesCategories.count,
                channels = channelCount,
                movies = movieCount,
                series = seriesCount,
                seasons = seasonCount,
                episodes = episodeCount,
            )
        }
    }

    override suspend fun importXtreamSeriesDetails(
        providerId: String,
        seriesInfos: List<XtreamSeriesInfo>,
    ): XtreamSeriesDetailsImportResult {
        val now = clock()
        return database.withTransaction {
            val seriesByRemoteId = catalogDao.getSeries(providerId).associateBy { it.remoteId }
            val seasonCount = upsertSeasons(providerId, buildSeasons(providerId, seriesInfos, seriesByRemoteId, now))
            val episodeCount = upsertEpisodes(providerId, buildEpisodes(providerId, seriesInfos, seriesByRemoteId, now))
            XtreamSeriesDetailsImportResult(seasons = seasonCount, episodes = episodeCount)
        }
    }

    private suspend fun buildCategories(
        providerId: String,
        type: String,
        categories: List<XtreamCategory>,
        referencedRemoteIds: Set<String>,
        now: Long,
    ): CategoryImport {
        val existing = catalogDao.getCategories(providerId, type).associateBy { it.remoteId }
        val categoryNames = categories.associate { it.remoteId to it.name }
        val allRemoteIds = (categories.map { it.remoteId } + referencedRemoteIds)
            .map { it.ifBlank { UNCATEGORIZED_REMOTE_ID } }
            .toSet()
        val entities = allRemoteIds
            .sortedBy { if (it == UNCATEGORIZED_REMOTE_ID) "" else categoryNames[it] ?: it }
            .mapIndexed { index, remoteId ->
                val existingCategory = existing[remoteId]
                CategoryEntity(
                    id = categoryId(providerId, type, remoteId),
                    providerId = providerId,
                    stableKey = categoryStableKey(type, remoteId),
                    type = type,
                    remoteId = remoteId,
                    name = if (remoteId == UNCATEGORIZED_REMOTE_ID) {
                        UNCATEGORIZED_DISPLAY_NAME
                    } else {
                        categoryNames[remoteId] ?: remoteId
                    },
                    sortOrder = index,
                    isHidden = existingCategory?.isHidden ?: false,
                    createdAt = existingCategory?.createdAt ?: now,
                    updatedAt = now,
                )
            }
        val importedIds = entities.mapTo(mutableSetOf()) { it.id }
        val removedIds = existing.values.filter { it.id !in importedIds }.map { it.id }
        val added = entities.count { it.remoteId !in existing }
        val updated = entities.count { entity ->
            val existingCategory = existing[entity.remoteId]
            existingCategory != null && existingCategory.copy(updatedAt = entity.updatedAt) != entity
        }
        if (entities.isNotEmpty()) {
            catalogDao.upsertCategories(entities)
        }
        return CategoryImport(ImportCount(added = added, updated = updated, removed = removedIds.size), removedIds)
    }

    private suspend fun upsertChannels(
        providerId: String,
        channels: List<ChannelEntity>,
        existingChannels: Map<String, ChannelEntity>,
    ): ImportCount {
        val importedIds = channels.mapTo(mutableSetOf()) { it.id }
        val removedIds = existingChannels.values.filter { it.id !in importedIds }.map { it.id }
        val count = countChanges(channels, existingChannels)
        if (channels.isNotEmpty()) {
            catalogDao.upsertChannels(channels)
        }
        if (removedIds.isNotEmpty()) {
            favoritesDao.deleteFavoritesByMediaIds(providerId, MEDIA_TYPE_CHANNEL, removedIds)
            playbackDao.deleteProgressForMediaIds(providerId, MEDIA_TYPE_CHANNEL, removedIds)
            playbackDao.deleteHistoryForChannels(providerId, removedIds)
            epgDao.deleteMappingsForChannels(providerId, removedIds)
            epgDao.deleteProgramsForChannels(providerId, removedIds)
            catalogDao.deleteChannels(providerId, removedIds)
        }
        return count.copy(removed = removedIds.size)
    }

    private suspend fun upsertMovies(providerId: String, items: List<XtreamVodItem>, now: Long): ImportCount {
        val existing = catalogDao.getMovies(providerId).associateBy { it.remoteId }
        val movies = items.map { item -> item.toEntity(providerId, existing[item.remoteId], now) }
        val importedIds = movies.mapTo(mutableSetOf()) { it.id }
        val removedIds = existing.values.filter { it.id !in importedIds }.map { it.id }
        val count = countChanges(movies, existing)
        if (movies.isNotEmpty()) {
            catalogDao.upsertMovies(movies)
        }
        if (removedIds.isNotEmpty()) {
            favoritesDao.deleteFavoritesByMediaIds(providerId, MEDIA_TYPE_MOVIE, removedIds)
            playbackDao.deleteProgressForMediaIds(providerId, MEDIA_TYPE_MOVIE, removedIds)
            catalogDao.deleteMovies(providerId, removedIds)
        }
        return count.copy(removed = removedIds.size)
    }

    private suspend fun upsertSeries(providerId: String, items: List<XtreamSeriesItem>, now: Long): ImportCount {
        val existing = catalogDao.getSeries(providerId).associateBy { it.remoteId }
        val series = items.map { item -> item.toEntity(providerId, existing[item.remoteId], now) }
        val importedIds = series.mapTo(mutableSetOf()) { it.id }
        val removedIds = existing.values.filter { it.id !in importedIds }.map { it.id }
        val count = countChanges(series, existing)
        if (series.isNotEmpty()) {
            catalogDao.upsertSeries(series)
        }
        if (removedIds.isNotEmpty()) {
            favoritesDao.deleteFavoritesByMediaIds(providerId, MEDIA_TYPE_SERIES, removedIds)
            playbackDao.deleteProgressForMediaIds(providerId, MEDIA_TYPE_SERIES, removedIds)
            // Cascade the removed series' seasons/episodes (+ episode progress). The fast Xtream refresh
            // leaves seasons/episodes untouched otherwise, so without this a removed series would orphan
            // its seasons/episodes until the separate series-details job reconciles them.
            val orphanEpisodeIds = catalogDao.getEpisodeIdsForSeries(providerId, removedIds)
            if (orphanEpisodeIds.isNotEmpty()) {
                playbackDao.deleteProgressForMediaIds(providerId, MEDIA_TYPE_EPISODE, orphanEpisodeIds)
            }
            catalogDao.deleteEpisodesForSeries(providerId, removedIds)
            catalogDao.deleteSeasonsForSeries(providerId, removedIds)
            catalogDao.deleteSeries(providerId, removedIds)
        }
        return count.copy(removed = removedIds.size)
    }

    private suspend fun upsertSeasons(providerId: String, seasons: List<SeasonEntity>): ImportCount {
        val existing = catalogDao.getSeasons(providerId).associateBy { it.id }
        val seasonsWithCreatedAt = seasons.map { season ->
            season.copy(createdAt = existing[season.id]?.createdAt ?: season.createdAt)
        }
        val importedIds = seasonsWithCreatedAt.mapTo(mutableSetOf()) { it.id }
        val removedIds = existing.values.filter { it.id !in importedIds }.map { it.id }
        val added = seasonsWithCreatedAt.count { it.id !in existing }
        val updated = seasonsWithCreatedAt.count { season ->
            val existingSeason = existing[season.id]
            existingSeason != null && existingSeason.copy(updatedAt = season.updatedAt) != season
        }
        if (seasonsWithCreatedAt.isNotEmpty()) {
            catalogDao.upsertSeasons(seasonsWithCreatedAt)
        }
        if (removedIds.isNotEmpty()) {
            catalogDao.deleteSeasons(providerId, removedIds)
        }
        return ImportCount(added = added, updated = updated, removed = removedIds.size)
    }

    private suspend fun upsertEpisodes(providerId: String, episodes: List<EpisodeEntity>): ImportCount {
        val existing = catalogDao.getEpisodes(providerId).associateBy { it.remoteId }
        val importedIds = episodes.mapTo(mutableSetOf()) { it.id }
        val removedIds = existing.values.filter { it.id !in importedIds }.map { it.id }
        val episodesWithCreatedAt = episodes.map { episode ->
            episode.copy(createdAt = existing[episode.remoteId]?.createdAt ?: episode.createdAt)
        }
        val count = countChanges(episodesWithCreatedAt, existing)
        if (episodesWithCreatedAt.isNotEmpty()) {
            catalogDao.upsertEpisodes(episodesWithCreatedAt)
        }
        if (removedIds.isNotEmpty()) {
            playbackDao.deleteProgressForMediaIds(providerId, MEDIA_TYPE_EPISODE, removedIds)
            catalogDao.deleteEpisodes(providerId, removedIds)
        }
        return count.copy(removed = removedIds.size)
    }

    private suspend fun deleteRemovedCategories(providerId: String, type: String, categoryIds: List<String>) {
        if (categoryIds.isNotEmpty()) {
            catalogDao.deleteCategories(providerId, type, categoryIds)
        }
    }

    private fun buildSeasons(
        providerId: String,
        seriesInfos: List<XtreamSeriesInfo>,
        seriesByRemoteId: Map<String, SeriesEntity>,
        now: Long,
    ): List<SeasonEntity> =
        seriesInfos.flatMap { info ->
            val series = seriesByRemoteId[info.seriesRemoteId] ?: return@flatMap emptyList()
            val explicitSeasons = info.seasons.associateBy { it.seasonNumber }
            val episodeSeasonNumbers = info.episodes.mapTo(mutableSetOf()) { it.seasonNumber }
            (explicitSeasons.keys + episodeSeasonNumbers)
                .sorted()
                .map { seasonNumber ->
                    val season = explicitSeasons[seasonNumber]
                    season.toEntity(providerId, series.id, info.seriesRemoteId, seasonNumber, now)
                }
        }

    private fun buildEpisodes(
        providerId: String,
        seriesInfos: List<XtreamSeriesInfo>,
        seriesByRemoteId: Map<String, SeriesEntity>,
        now: Long,
    ): List<EpisodeEntity> =
        seriesInfos.flatMap { info ->
            val series = seriesByRemoteId[info.seriesRemoteId] ?: return@flatMap emptyList()
            info.episodes.map { episode ->
                episode.toEntity(providerId, series.id, seasonId(providerId, info.seriesRemoteId, episode.seasonNumber), now)
            }
        }

    private fun M3uChannel.toEntity(
        providerId: String,
        categoryId: String,
        existing: ChannelEntity?,
        now: Long,
    ): ChannelEntity =
        ChannelEntity(
            id = channelId(providerId, remoteId),
            providerId = providerId,
            categoryId = categoryId,
            stableKey = mediaStableKey(remoteId),
            remoteId = remoteId,
            channelNumber = channelNumber,
            name = name,
            logoUrl = logoUrl,
            epgChannelId = tvgId?.trim()?.takeIf { it.isNotBlank() },
            isCatchupAvailable = isCatchupAvailable,
            catchupDays = catchupDays,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )

    private fun XtreamLiveStream.toEntity(providerId: String, existing: ChannelEntity?, now: Long): ChannelEntity =
        ChannelEntity(
            id = channelId(providerId, remoteId),
            providerId = providerId,
            categoryId = categoryId(providerId, CATEGORY_TYPE_LIVE, categoryRemoteId()),
            stableKey = mediaStableKey(remoteId),
            remoteId = remoteId,
            channelNumber = channelNumber,
            name = name,
            logoUrl = logoUrl,
            epgChannelId = epgChannelId?.trim()?.takeIf { it.isNotBlank() },
            isCatchupAvailable = isCatchupAvailable,
            catchupDays = catchupDays,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )

    private fun XtreamVodItem.toEntity(providerId: String, existing: MovieEntity?, now: Long): MovieEntity =
        MovieEntity(
            id = movieId(providerId, remoteId),
            providerId = providerId,
            categoryId = categoryId(providerId, CATEGORY_TYPE_MOVIE, categoryRemoteId()),
            stableKey = mediaStableKey(remoteId),
            remoteId = remoteId,
            name = name,
            originalName = null,
            containerExtension = containerExtension,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            rating = rating,
            year = year,
            genre = genre,
            duration = durationSeconds,
            director = director,
            cast = cast,
            plot = plot,
            trailerUrl = trailerUrl,
            addedAt = addedAtSeconds,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )

    private fun XtreamSeriesItem.toEntity(providerId: String, existing: SeriesEntity?, now: Long): SeriesEntity =
        SeriesEntity(
            id = seriesId(providerId, remoteId),
            providerId = providerId,
            categoryId = categoryId(providerId, CATEGORY_TYPE_SERIES, categoryRemoteId()),
            stableKey = mediaStableKey(remoteId),
            remoteId = remoteId,
            name = name,
            originalName = null,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            rating = rating,
            year = year,
            genre = genre,
            director = director,
            cast = cast,
            plot = plot,
            addedAt = addedAtSeconds,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )

    private fun XtreamSeason?.toEntity(
        providerId: String,
        seriesId: String,
        seriesRemoteId: String,
        seasonNumber: Int,
        now: Long,
    ): SeasonEntity {
        return SeasonEntity(
            id = seasonId(providerId, seriesRemoteId, seasonNumber),
            providerId = providerId,
            seriesId = seriesId,
            stableKey = seasonStableKey(seriesRemoteId, seasonNumber),
            seasonNumber = seasonNumber,
            name = this?.name ?: "Staffel $seasonNumber",
            posterUrl = this?.posterUrl,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun XtreamEpisode.toEntity(providerId: String, seriesId: String, seasonId: String, now: Long): EpisodeEntity =
        EpisodeEntity(
            id = episodeId(providerId, remoteId),
            providerId = providerId,
            seriesId = seriesId,
            seasonId = seasonId,
            stableKey = mediaStableKey(remoteId),
            remoteId = remoteId,
            episodeNumber = episodeNumber,
            seasonNumber = seasonNumber,
            name = name,
            plot = plot,
            thumbnailUrl = thumbnailUrl,
            containerExtension = containerExtension,
            duration = durationSeconds,
            airDate = airDate,
            createdAt = now,
            updatedAt = now,
        )

    private fun M3uChannel.categoryRemoteId(): String =
        categoryName?.takeIf { it.isNotBlank() } ?: UNCATEGORIZED_REMOTE_ID

    private fun XtreamLiveStream.categoryRemoteId(): String =
        categoryRemoteId?.takeIf { it.isNotBlank() } ?: UNCATEGORIZED_REMOTE_ID

    private fun XtreamVodItem.categoryRemoteId(): String =
        categoryRemoteId?.takeIf { it.isNotBlank() } ?: UNCATEGORIZED_REMOTE_ID

    private fun XtreamSeriesItem.categoryRemoteId(): String =
        categoryRemoteId?.takeIf { it.isNotBlank() } ?: UNCATEGORIZED_REMOTE_ID

    private fun <T : Any> countChanges(entities: List<T>, existingByRemoteId: Map<String, T>): ImportCount =
        ImportCount(
            added = entities.count { entity -> entity.remoteId() !in existingByRemoteId },
            updated = entities.count { entity ->
                val existing = existingByRemoteId[entity.remoteId()]
                existing != null && existing.withUpdatedAt(entity.updatedAt()) != entity
            },
            removed = 0,
        )

    private fun Any.remoteId(): String =
        when (this) {
            is ChannelEntity -> remoteId
            is MovieEntity -> remoteId
            is SeriesEntity -> remoteId
            is EpisodeEntity -> remoteId
            else -> error("Unsupported import entity: ${this::class.java.name}")
        }

    private fun Any.updatedAt(): Long =
        when (this) {
            is ChannelEntity -> updatedAt
            is MovieEntity -> updatedAt
            is SeriesEntity -> updatedAt
            is EpisodeEntity -> updatedAt
            else -> error("Unsupported import entity: ${this::class.java.name}")
        }

    private fun Any.withUpdatedAt(updatedAt: Long): Any =
        when (this) {
            is ChannelEntity -> copy(updatedAt = updatedAt)
            is MovieEntity -> copy(updatedAt = updatedAt)
            is SeriesEntity -> copy(updatedAt = updatedAt)
            is EpisodeEntity -> copy(updatedAt = updatedAt)
            else -> error("Unsupported import entity: ${this::class.java.name}")
        }

    private data class CategoryImport(
        val count: ImportCount,
        val removedIds: List<String>,
    )

    private companion object {
        const val CATEGORY_TYPE_LIVE = "LIVE"
        const val CATEGORY_TYPE_MOVIE = "MOVIE"
        const val CATEGORY_TYPE_SERIES = "SERIES"
        const val MEDIA_TYPE_CHANNEL = "CHANNEL"
        const val MEDIA_TYPE_MOVIE = "MOVIE"
        const val MEDIA_TYPE_SERIES = "SERIES"
        const val MEDIA_TYPE_EPISODE = "EPISODE"
        const val UNCATEGORIZED_REMOTE_ID = "__UNCATEGORIZED__"
        const val UNCATEGORIZED_DISPLAY_NAME = "Nicht kategorisiert"

        fun categoryId(providerId: String, type: String, remoteId: String): String =
            "$providerId:category:${type.lowercase()}:${stableHash(remoteId)}"

        fun channelId(providerId: String, remoteId: String): String =
            "$providerId:channel:${stableHash(remoteId)}"

        fun movieId(providerId: String, remoteId: String): String =
            "$providerId:movie:${stableHash(remoteId)}"

        fun seriesId(providerId: String, remoteId: String): String =
            "$providerId:series:${stableHash(remoteId)}"

        fun seasonId(providerId: String, seriesRemoteId: String, seasonNumber: Int): String =
            "$providerId:season:${stableHash("$seriesRemoteId:$seasonNumber")}"

        fun episodeId(providerId: String, remoteId: String): String =
            "$providerId:episode:${stableHash(remoteId)}"

        fun categoryStableKey(type: String, remoteId: String): String =
            stableHash("${type.lowercase()}:$remoteId")

        fun mediaStableKey(remoteId: String): String =
            stableHash(remoteId)

        fun seasonStableKey(seriesRemoteId: String, seasonNumber: Int): String =
            stableHash("$seriesRemoteId:$seasonNumber")

        fun stableHash(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.take(16).joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
    }
}
