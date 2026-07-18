package com.vivicast.tv.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vivicast.tv.core.database.dao.AndroidTvSearchDao
import com.vivicast.tv.core.database.dao.CatalogDao
import com.vivicast.tv.core.database.dao.EpgDao
import com.vivicast.tv.core.database.dao.FavoritesDao
import com.vivicast.tv.core.database.dao.PlaybackDao
import com.vivicast.tv.core.database.dao.ProviderCategorySettingsDao
import com.vivicast.tv.core.database.dao.ProviderDao
import com.vivicast.tv.core.database.dao.SearchDao
import com.vivicast.tv.core.database.model.AndroidTvSearchEntryEntity
import com.vivicast.tv.core.database.model.CategoryEntity
import com.vivicast.tv.core.database.model.ChannelEntity
import com.vivicast.tv.core.database.model.ChannelHistoryEntity
import com.vivicast.tv.core.database.model.EpgChannelMappingEntity
import com.vivicast.tv.core.database.model.EpgChannelEntity
import com.vivicast.tv.core.database.model.EpgProgramEntity
import com.vivicast.tv.core.database.model.EpgSourceEntity
import com.vivicast.tv.core.database.model.EpisodeEntity
import com.vivicast.tv.core.database.model.FavoriteEntity
import com.vivicast.tv.core.database.model.MovieEntity
import com.vivicast.tv.core.database.model.PlaybackProgressEntity
import com.vivicast.tv.core.database.model.ProviderCategorySettingsEntity
import com.vivicast.tv.core.database.model.ProviderEntity
import com.vivicast.tv.core.database.model.ProviderEpgSourceEntity
import com.vivicast.tv.core.database.model.SearchHistoryEntity
import com.vivicast.tv.core.database.model.SearchChannelFtsEntity
import com.vivicast.tv.core.database.model.SearchEpgFtsEntity
import com.vivicast.tv.core.database.model.SearchMovieFtsEntity
import com.vivicast.tv.core.database.model.SearchSeriesFtsEntity
import com.vivicast.tv.core.database.model.SeasonEntity
import com.vivicast.tv.core.database.model.SeriesEntity
import com.vivicast.tv.core.database.model.ChannelStageEntity
import com.vivicast.tv.core.database.model.MovieStageEntity
import com.vivicast.tv.core.database.model.SeriesStageEntity
import com.vivicast.tv.core.database.model.EpisodeStageEntity
import com.vivicast.tv.core.database.model.EpgProgramStageEntity

@Database(
    entities = [
        ProviderEntity::class,
        CategoryEntity::class,
        ChannelEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        SeasonEntity::class,
        EpisodeEntity::class,
        EpgSourceEntity::class,
        EpgChannelEntity::class,
        ProviderEpgSourceEntity::class,
        ProviderCategorySettingsEntity::class,
        EpgProgramEntity::class,
        EpgChannelMappingEntity::class,
        FavoriteEntity::class,
        PlaybackProgressEntity::class,
        ChannelHistoryEntity::class,
        SearchHistoryEntity::class,
        SearchChannelFtsEntity::class,
        SearchMovieFtsEntity::class,
        SearchSeriesFtsEntity::class,
        SearchEpgFtsEntity::class,
        AndroidTvSearchEntryEntity::class,
        // Staging tables for the chunked delta-merge import (plans/nonblocking-db-imports.md).
        ChannelStageEntity::class,
        MovieStageEntity::class,
        SeriesStageEntity::class,
        EpisodeStageEntity::class,
        EpgProgramStageEntity::class,
    ],
    version = VIVICAST_DATABASE_VERSION,
    exportSchema = true,
)
abstract class VivicastDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao
    abstract fun catalogDao(): CatalogDao
    abstract fun providerCategorySettingsDao(): ProviderCategorySettingsDao
    abstract fun epgDao(): EpgDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun playbackDao(): PlaybackDao
    abstract fun searchDao(): SearchDao
    abstract fun androidTvSearchDao(): AndroidTvSearchDao
}

const val VIVICAST_DATABASE_VERSION = 21

/**
 * Installs the InvalidationTracker triggers for the UI-observed tables eagerly at startup. Room installs
 * these lazily on the first Flow observer via a WRITE (`syncTriggers`); if that first observer subscribes
 * while a long import/refresh transaction holds the writer, its initial emission blocks until the import
 * commits — so the UI shows empty lists (e.g. "No playlists configured") until the refresh finishes.
 * Warming here, before any import runs, means later Flow subscriptions reuse the installed triggers and
 * emit the current WAL snapshot immediately. The no-op observer stays for the app's lifetime to keep the
 * triggers installed.
 */
fun VivicastDatabase.warmInvalidationTracker() {
    invalidationTracker.addObserver(
        object : androidx.room.InvalidationTracker.Observer(
            UI_OBSERVED_TABLES.first(),
            *UI_OBSERVED_TABLES.drop(1).toTypedArray(),
        ) {
            override fun onInvalidated(tables: Set<String>) = Unit
        },
    )
}

private val UI_OBSERVED_TABLES = arrayOf(
    "providers", "categories", "channels", "movies", "series", "seasons", "episodes",
    "epg_sources", "epg_channels", "provider_epg_sources", "provider_category_settings",
    "epg_channel_mappings", "epg_programs",
    "playback_progress", "channel_history", "favorites", "search_history",
)
