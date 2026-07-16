package com.vivicast.tv.data.media

import androidx.room.withTransaction
import com.vivicast.tv.core.cache.M3uStreamReference
import com.vivicast.tv.core.cache.M3uStreamReferenceStore
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.forEachChunkedTransaction
import com.vivicast.tv.core.database.syncFingerprint
import com.vivicast.tv.core.database.model.CategoryEntity
import com.vivicast.tv.core.database.model.ChannelEntity
import com.vivicast.tv.core.database.model.ChannelStageEntity
import com.vivicast.tv.core.database.model.EpisodeEntity
import com.vivicast.tv.core.database.model.EpisodeStageEntity
import com.vivicast.tv.core.database.model.MovieEntity
import com.vivicast.tv.core.database.model.MovieStageEntity
import com.vivicast.tv.core.database.model.SeasonEntity
import com.vivicast.tv.core.database.model.SeriesEntity
import com.vivicast.tv.core.database.model.SeriesStageEntity
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
    private val providerCategorySettingsDao = database.providerCategorySettingsDao()
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
        val seriesRemoteIds = classified.seriesItems.mapTo(mutableSetOf()) { it.remoteId }

        // Build the staged rows (deterministic ids come out final; the fingerprint drives the merge, so no
        // per-row existing read is needed). M3U carries its whole catalog, so seasons/episodes are always
        // reconciled from the parse.
        val channels = classified.liveChannels.map { channel ->
            channel.toEntity(
                providerId = providerId,
                categoryId = categoryId(providerId, CATEGORY_TYPE_LIVE, channel.categoryRemoteId()),
                existing = null,
                now = now,
            ).toStage()
        }
        val movies = classified.vodItems.associateBy { it.remoteId }.values
            .map { it.toEntity(providerId, existing = null, now).toStage() }
        val series = classified.seriesItems.associateBy { it.remoteId }.values
            .map { it.toEntity(providerId, existing = null, now).toStage() }
        val seasons = buildSeasons(providerId, classified.seriesInfos, seriesRemoteIds, now)
        val episodes = buildEpisodes(providerId, classified.seriesInfos, seriesRemoteIds, now).map { it.toStage() }

        // Stage chunked, outside the merge transaction — the single writer is released between chunks.
        stageChannels(providerId, channels)
        stageMovies(providerId, movies)
        stageSeries(providerId, series)
        stageEpisodes(providerId, episodes)

        // Merge: one short transaction applies categories (with the D10 group user-state preserve), then
        // only the delta for channels/movies/series/episodes, then the FTS rebuild — atomic old->new flip.
        val result = database.withTransaction {
            val liveCategories = buildCategories(providerId, CATEGORY_TYPE_LIVE, m3uCategories(liveCategoryIds), liveCategoryIds, now)
            val movieCategories = buildCategories(providerId, CATEGORY_TYPE_MOVIE, m3uCategories(movieCategoryIds), movieCategoryIds, now)
            val seriesCategories = buildCategories(providerId, CATEGORY_TYPE_SERIES, m3uCategories(seriesCategoryIds), seriesCategoryIds, now)

            val channelCount = mergeChannels(providerId)
            mergeMovies(providerId)
            mergeSeries(providerId, cascadeSeasonsEpisodes = false)
            upsertSeasons(providerId, seasons)
            mergeEpisodes(providerId)

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
        clearAllStage(providerId)
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
        val seriesRemoteIds = catalog.seriesItems.mapTo(mutableSetOf()) { it.remoteId }
        // Season/episode detail is imported separately via importXtreamSeriesDetails (background job). When
        // seriesInfos is empty (the fast main refresh), leave existing seasons/episodes untouched — do not
        // stage/merge them, or the delta-merge would delete every existing episode against an empty stage.
        val hasSeriesDetails = catalog.seriesInfos.isNotEmpty()

        val channels = catalog.liveStreams.associateBy { it.remoteId }.values
            .map { it.toEntity(providerId, existing = null, now).toStage() }
        val movies = catalog.vodItems.associateBy { it.remoteId }.values
            .map { it.toEntity(providerId, existing = null, now).toStage() }
        val series = catalog.seriesItems.associateBy { it.remoteId }.values
            .map { it.toEntity(providerId, existing = null, now).toStage() }
        val seasons = if (hasSeriesDetails) buildSeasons(providerId, catalog.seriesInfos, seriesRemoteIds, now) else emptyList()
        val episodes = if (hasSeriesDetails) buildEpisodes(providerId, catalog.seriesInfos, seriesRemoteIds, now).map { it.toStage() } else emptyList()

        stageChannels(providerId, channels)
        stageMovies(providerId, movies)
        stageSeries(providerId, series)
        if (hasSeriesDetails) {
            stageEpisodes(providerId, episodes)
        }

        val result = database.withTransaction {
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

            val channelCount = mergeChannels(providerId)
            val movieCount = mergeMovies(providerId)
            val seriesCount = mergeSeries(providerId, cascadeSeasonsEpisodes = !hasSeriesDetails)
            val seasonCount: ImportCount
            val episodeCount: ImportCount
            if (hasSeriesDetails) {
                seasonCount = upsertSeasons(providerId, seasons)
                episodeCount = mergeEpisodes(providerId)
            } else {
                seasonCount = ImportCount(added = 0, updated = 0, removed = 0)
                episodeCount = ImportCount(added = 0, updated = 0, removed = 0)
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
        clearAllStage(providerId)
        return result
    }

    override suspend fun importXtreamSeriesDetails(
        providerId: String,
        seriesInfos: List<XtreamSeriesInfo>,
    ): XtreamSeriesDetailsImportResult {
        val now = clock()
        // Reconciles seasons/episodes for the provider's currently-known series (the caller passes ALL of
        // them per run). Deterministic series ids let seasons/episodes bind without reading the series back.
        val knownSeriesRemoteIds = catalogDao.getSeries(providerId).mapTo(mutableSetOf()) { it.remoteId }
        val seasons = buildSeasons(providerId, seriesInfos, knownSeriesRemoteIds, now)
        val episodes = buildEpisodes(providerId, seriesInfos, knownSeriesRemoteIds, now).map { it.toStage() }
        stageEpisodes(providerId, episodes)
        val result = database.withTransaction {
            val seasonCount = upsertSeasons(providerId, seasons)
            val episodeCount = mergeEpisodes(providerId)
            XtreamSeriesDetailsImportResult(seasons = seasonCount, episodes = episodeCount)
        }
        clearEpisodesStageFor(providerId)
        return result
    }

    private suspend fun buildCategories(
        providerId: String,
        type: String,
        categories: List<XtreamCategory>,
        referencedRemoteIds: Set<String>,
        now: Long,
    ): CategoryImport {
        val existing = catalogDao.getCategories(providerId, type).associateBy { it.remoteId }
        // New groups default to hidden per the provider's policy (NULL settings = show, the default).
        val hideNewGroups = providerCategorySettingsDao.getSettings(providerId, type)?.hideNewGroups ?: false
        val categoryNames = categories.associate { it.remoteId to it.name }
        // LinkedHashSet keeps first-appearance (source) order; sortOrder = that order = the PLAYLIST sort
        // mode. No alphabetical sort here (NAME mode is applied at read time). Uncategorized falls at its
        // natural source position — no longer force-pinned first (owner decision). See D10.
        val allRemoteIds = (categories.map { it.remoteId } + referencedRemoteIds)
            .map { it.ifBlank { UNCATEGORIZED_REMOTE_ID } }
            .toSet()
        val entities = allRemoteIds
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
                    // Preserved across import (keyed by remoteId), like isHidden: a hidden or manually
                    // ordered group survives a refresh. New groups take the hide-new policy; new groups
                    // have no manual position yet (null).
                    isHidden = existingCategory?.isHidden ?: hideNewGroups,
                    manualSortOrder = existingCategory?.manualSortOrder,
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

    // --- Staged delta-merge helpers (plans/nonblocking-db-imports.md) ---
    // Each merge runs inside the import transaction: cascade the removed rows' user-state, then apply the
    // delta trio (delete-changed -> insert-missing -> delete-stale) against the pre-staged rows. added =
    // rows inserted that weren't just re-inserted from a change; updated = the changed rows; removed = the
    // live ids with no stage match. IN(:ids) deletes are chunked under SQLite's 999-variable limit.

    private suspend fun mergeChannels(providerId: String): ImportCount {
        val removedIds = catalogDao.removedChannelsIds(providerId)
        removedIds.chunked(SQL_IN_MAX).forEach { ids ->
            favoritesDao.deleteFavoritesByMediaIds(providerId, MEDIA_TYPE_CHANNEL, ids)
            playbackDao.deleteProgressForMediaIds(providerId, MEDIA_TYPE_CHANNEL, ids)
            playbackDao.deleteHistoryForChannels(providerId, ids)
            epgDao.deleteMappingsForChannels(providerId, ids)
            epgDao.deleteProgramsForChannels(providerId, ids)
        }
        val added = catalogDao.countNewChannelsFromStage(providerId)
        val updated = catalogDao.deleteChangedChannelsFromStage(providerId)
        catalogDao.insertMissingChannelsFromStage(providerId)
        catalogDao.deleteStaleChannelsFromStage(providerId)
        return ImportCount(added = added, updated = updated, removed = removedIds.size)
    }

    private suspend fun mergeMovies(providerId: String): ImportCount {
        val removedIds = catalogDao.removedMoviesIds(providerId)
        removedIds.chunked(SQL_IN_MAX).forEach { ids ->
            favoritesDao.deleteFavoritesByMediaIds(providerId, MEDIA_TYPE_MOVIE, ids)
            playbackDao.deleteProgressForMediaIds(providerId, MEDIA_TYPE_MOVIE, ids)
        }
        val added = catalogDao.countNewMoviesFromStage(providerId)
        val updated = catalogDao.deleteChangedMoviesFromStage(providerId)
        catalogDao.insertMissingMoviesFromStage(providerId)
        catalogDao.deleteStaleMoviesFromStage(providerId)
        return ImportCount(added = added, updated = updated, removed = removedIds.size)
    }

    private suspend fun mergeSeries(providerId: String, cascadeSeasonsEpisodes: Boolean): ImportCount {
        val removedIds = catalogDao.removedSeriesIds(providerId)
        removedIds.chunked(SQL_IN_MAX).forEach { ids ->
            favoritesDao.deleteFavoritesByMediaIds(providerId, MEDIA_TYPE_SERIES, ids)
            playbackDao.deleteProgressForMediaIds(providerId, MEDIA_TYPE_SERIES, ids)
            // Cascade the removed series' seasons/episodes (+ episode progress) ONLY when this import does
            // not merge episodes/seasons itself (the fast Xtream refresh, seriesInfos empty). When it does,
            // the episode delta-merge + season reconcile already remove and COUNT them, so cascading here
            // would double-delete and under-count episodes.removed.
            if (cascadeSeasonsEpisodes) {
                val orphanEpisodeIds = catalogDao.getEpisodeIdsForSeries(providerId, ids)
                orphanEpisodeIds.chunked(SQL_IN_MAX).forEach { episodeIds ->
                    playbackDao.deleteProgressForMediaIds(providerId, MEDIA_TYPE_EPISODE, episodeIds)
                }
                catalogDao.deleteEpisodesForSeries(providerId, ids)
                catalogDao.deleteSeasonsForSeries(providerId, ids)
            }
        }
        val added = catalogDao.countNewSeriesFromStage(providerId)
        val updated = catalogDao.deleteChangedSeriesFromStage(providerId)
        catalogDao.insertMissingSeriesFromStage(providerId)
        catalogDao.deleteStaleSeriesFromStage(providerId)
        return ImportCount(added = added, updated = updated, removed = removedIds.size)
    }

    private suspend fun mergeEpisodes(providerId: String): ImportCount {
        val removedIds = catalogDao.removedEpisodesIds(providerId)
        removedIds.chunked(SQL_IN_MAX).forEach { ids ->
            playbackDao.deleteProgressForMediaIds(providerId, MEDIA_TYPE_EPISODE, ids)
        }
        val added = catalogDao.countNewEpisodesFromStage(providerId)
        val updated = catalogDao.deleteChangedEpisodesFromStage(providerId)
        catalogDao.insertMissingEpisodesFromStage(providerId)
        catalogDao.deleteStaleEpisodesFromStage(providerId)
        return ImportCount(added = added, updated = updated, removed = removedIds.size)
    }

    private suspend fun stageChannels(providerId: String, rows: List<ChannelStageEntity>) {
        catalogDao.clearChannelsStage(providerId)
        rows.forEachChunkedTransaction(database, STAGE_CHUNK_SIZE) { catalogDao.insertChannelsStage(it) }
    }

    private suspend fun stageMovies(providerId: String, rows: List<MovieStageEntity>) {
        catalogDao.clearMoviesStage(providerId)
        rows.forEachChunkedTransaction(database, STAGE_CHUNK_SIZE) { catalogDao.insertMoviesStage(it) }
    }

    private suspend fun stageSeries(providerId: String, rows: List<SeriesStageEntity>) {
        catalogDao.clearSeriesStage(providerId)
        rows.forEachChunkedTransaction(database, STAGE_CHUNK_SIZE) { catalogDao.insertSeriesStage(it) }
    }

    private suspend fun stageEpisodes(providerId: String, rows: List<EpisodeStageEntity>) {
        catalogDao.clearEpisodesStage(providerId)
        rows.forEachChunkedTransaction(database, STAGE_CHUNK_SIZE) { catalogDao.insertEpisodesStage(it) }
    }

    // Drop the transient staged rows after the merge consumes them (a crash before this is cleaned at
    // startup). clearEpisodesStageFor is the series-details job's narrower cleanup.
    private suspend fun clearAllStage(providerId: String) {
        catalogDao.clearChannelsStage(providerId)
        catalogDao.clearMoviesStage(providerId)
        catalogDao.clearSeriesStage(providerId)
        catalogDao.clearEpisodesStage(providerId)
    }

    private suspend fun clearEpisodesStageFor(providerId: String) {
        catalogDao.clearEpisodesStage(providerId)
    }

    // Live entity -> staging row + a content-only fingerprint (excludes id/providerId/stableKey/createdAt/
    // updatedAt — the derived/bookkeeping columns) that the delta-merge compares against the live row.
    private fun ChannelEntity.toStage(): ChannelStageEntity = ChannelStageEntity(
        id = id, providerId = providerId, categoryId = categoryId, stableKey = stableKey, remoteId = remoteId,
        channelNumber = channelNumber, name = name, logoUrl = logoUrl, epgChannelId = epgChannelId,
        isCatchupAvailable = isCatchupAvailable, catchupDays = catchupDays, createdAt = createdAt, updatedAt = updatedAt,
        syncFingerprint = syncFingerprint(
            categoryId, remoteId, channelNumber, name, logoUrl, epgChannelId, isCatchupAvailable, catchupDays,
        ),
    )

    private fun MovieEntity.toStage(): MovieStageEntity = MovieStageEntity(
        id = id, providerId = providerId, categoryId = categoryId, stableKey = stableKey, remoteId = remoteId,
        name = name, originalName = originalName, containerExtension = containerExtension, posterUrl = posterUrl,
        backdropUrl = backdropUrl, rating = rating, year = year, genre = genre, duration = duration, director = director,
        cast = cast, plot = plot, trailerUrl = trailerUrl, addedAt = addedAt, ageRating = ageRating, isAdult = isAdult,
        createdAt = createdAt, updatedAt = updatedAt,
        syncFingerprint = syncFingerprint(
            categoryId, remoteId, name, originalName, containerExtension, posterUrl, backdropUrl, rating, year, genre,
            duration, director, cast, plot, trailerUrl, addedAt, ageRating, isAdult,
        ),
    )

    private fun SeriesEntity.toStage(): SeriesStageEntity = SeriesStageEntity(
        id = id, providerId = providerId, categoryId = categoryId, stableKey = stableKey, remoteId = remoteId,
        name = name, originalName = originalName, posterUrl = posterUrl, backdropUrl = backdropUrl, rating = rating,
        year = year, genre = genre, director = director, cast = cast, plot = plot, addedAt = addedAt, ageRating = ageRating,
        isAdult = isAdult, createdAt = createdAt, updatedAt = updatedAt,
        syncFingerprint = syncFingerprint(
            categoryId, remoteId, name, originalName, posterUrl, backdropUrl, rating, year, genre, director, cast, plot,
            addedAt, ageRating, isAdult,
        ),
    )

    private fun EpisodeEntity.toStage(): EpisodeStageEntity = EpisodeStageEntity(
        id = id, providerId = providerId, seriesId = seriesId, seasonId = seasonId, stableKey = stableKey, remoteId = remoteId,
        episodeNumber = episodeNumber, seasonNumber = seasonNumber, name = name, plot = plot, thumbnailUrl = thumbnailUrl,
        containerExtension = containerExtension, duration = duration, airDate = airDate, ageRating = ageRating, isAdult = isAdult,
        createdAt = createdAt, updatedAt = updatedAt,
        syncFingerprint = syncFingerprint(
            seriesId, seasonId, remoteId, episodeNumber, seasonNumber, name, plot, thumbnailUrl, containerExtension,
            duration, airDate, ageRating, isAdult,
        ),
    )

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

    private suspend fun deleteRemovedCategories(providerId: String, type: String, categoryIds: List<String>) {
        if (categoryIds.isNotEmpty()) {
            catalogDao.deleteCategories(providerId, type, categoryIds)
        }
    }

    // Series ids are deterministic (seriesId(providerId, remoteId)), so seasons/episodes bind to their
    // series without reading it back. knownSeriesRemoteIds gates out infos for series not in the catalog
    // (preserving the old "skip series we don't have" behaviour) so no orphan seasons/episodes are built.
    private fun buildSeasons(
        providerId: String,
        seriesInfos: List<XtreamSeriesInfo>,
        knownSeriesRemoteIds: Set<String>,
        now: Long,
    ): List<SeasonEntity> =
        seriesInfos.flatMap { info ->
            if (info.seriesRemoteId !in knownSeriesRemoteIds) return@flatMap emptyList()
            val seriesRowId = seriesId(providerId, info.seriesRemoteId)
            val explicitSeasons = info.seasons.associateBy { it.seasonNumber }
            val episodeSeasonNumbers = info.episodes.mapTo(mutableSetOf()) { it.seasonNumber }
            (explicitSeasons.keys + episodeSeasonNumbers)
                .sorted()
                .map { seasonNumber ->
                    val season = explicitSeasons[seasonNumber]
                    season.toEntity(providerId, seriesRowId, info.seriesRemoteId, seasonNumber, now)
                }
        }

    private fun buildEpisodes(
        providerId: String,
        seriesInfos: List<XtreamSeriesInfo>,
        knownSeriesRemoteIds: Set<String>,
        now: Long,
    ): List<EpisodeEntity> =
        seriesInfos.flatMap { info ->
            if (info.seriesRemoteId !in knownSeriesRemoteIds) return@flatMap emptyList()
            val seriesRowId = seriesId(providerId, info.seriesRemoteId)
            info.episodes.map { episode ->
                episode.toEntity(providerId, seriesRowId, seasonId(providerId, info.seriesRemoteId, episode.seasonNumber), now)
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

        // Per-transaction stage chunk (writer released between chunks) + SQLite's IN(:ids) variable cap.
        const val STAGE_CHUNK_SIZE = 1_000
        const val SQL_IN_MAX = 900

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
