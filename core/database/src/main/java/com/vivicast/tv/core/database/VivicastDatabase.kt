package com.vivicast.tv.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vivicast.tv.core.database.dao.CatalogDao
import com.vivicast.tv.core.database.dao.EpgDao
import com.vivicast.tv.core.database.dao.FavoritesDao
import com.vivicast.tv.core.database.dao.PlaybackDao
import com.vivicast.tv.core.database.dao.ProviderDao
import com.vivicast.tv.core.database.dao.SearchDao
import com.vivicast.tv.core.database.model.CategoryEntity
import com.vivicast.tv.core.database.model.ChannelEntity
import com.vivicast.tv.core.database.model.ChannelHistoryEntity
import com.vivicast.tv.core.database.model.EpgChannelMappingEntity
import com.vivicast.tv.core.database.model.EpgProgramEntity
import com.vivicast.tv.core.database.model.EpgSourceEntity
import com.vivicast.tv.core.database.model.EpisodeEntity
import com.vivicast.tv.core.database.model.FavoriteEntity
import com.vivicast.tv.core.database.model.MovieEntity
import com.vivicast.tv.core.database.model.PlaybackProgressEntity
import com.vivicast.tv.core.database.model.ProviderEntity
import com.vivicast.tv.core.database.model.ProviderEpgSourceEntity
import com.vivicast.tv.core.database.model.SeasonEntity
import com.vivicast.tv.core.database.model.SeriesEntity

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
        ProviderEpgSourceEntity::class,
        EpgProgramEntity::class,
        EpgChannelMappingEntity::class,
        FavoriteEntity::class,
        PlaybackProgressEntity::class,
        ChannelHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class VivicastDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao
    abstract fun catalogDao(): CatalogDao
    abstract fun epgDao(): EpgDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun playbackDao(): PlaybackDao
    abstract fun searchDao(): SearchDao
}

