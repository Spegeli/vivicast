package com.vivicast.core.database

import android.content.Context
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.Database
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vivicast.core.database.dao.ChannelDao
import com.vivicast.core.database.dao.EpgDao
import com.vivicast.core.database.dao.PlaylistDao
import com.vivicast.core.database.dao.VodDao
import com.vivicast.core.database.entity.EpisodeEntity
import com.vivicast.core.database.entity.EpisodePlaybackProgressEntity
import com.vivicast.core.database.entity.CategoryEntity
import com.vivicast.core.database.entity.ChannelEntity
import com.vivicast.core.database.entity.EpgProgramEntity
import com.vivicast.core.database.entity.EpgSourceEntity
import com.vivicast.core.database.entity.FavoriteChannelEntity
import com.vivicast.core.database.entity.MovieCategoryEntity
import com.vivicast.core.database.entity.MovieEntity
import com.vivicast.core.database.entity.MoviePlaybackProgressEntity
import com.vivicast.core.database.entity.PlaylistEntity
import com.vivicast.core.database.entity.RecentChannelEntity
import com.vivicast.core.database.entity.SeasonEntity
import com.vivicast.core.database.entity.SeriesCategoryEntity
import com.vivicast.core.database.entity.SeriesEntity

@Database(
    entities = [
        PlaylistEntity::class,
        CategoryEntity::class,
        ChannelEntity::class,
        EpgSourceEntity::class,
        EpgProgramEntity::class,
        FavoriteChannelEntity::class,
        RecentChannelEntity::class,
        MovieCategoryEntity::class,
        MovieEntity::class,
        SeriesCategoryEntity::class,
        SeriesEntity::class,
        SeasonEntity::class,
        EpisodeEntity::class,
        MoviePlaybackProgressEntity::class,
        EpisodePlaybackProgressEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class ViviCastDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun channelDao(): ChannelDao
    abstract fun epgDao(): EpgDao
    abstract fun vodDao(): VodDao
}

object ViviCastDatabaseFactory {
    fun create(context: Context): ViviCastDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            ViviCastDatabase::class.java,
            "vivicast.db"
        )
            .addMigrations(Migration1To2, Migration2To3)
            .build()
    }
}

private val Migration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playlists ADD COLUMN sourceUsername TEXT")
        db.execSQL("ALTER TABLE playlists ADD COLUMN sourcePassword TEXT")
    }
}

private val Migration2To3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `movie_categories` (
                `id` TEXT NOT NULL,
                `playlistId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `sortIndex` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_movie_categories_playlistId` ON `movie_categories` (`playlistId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_movie_categories_playlistId_name` ON `movie_categories` (`playlistId`, `name`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `movies` (
                `id` TEXT NOT NULL,
                `playlistId` TEXT NOT NULL,
                `categoryId` TEXT,
                `title` TEXT NOT NULL,
                `streamUrl` TEXT NOT NULL,
                `coverUrl` TEXT,
                `plot` TEXT,
                `durationMinutes` INTEGER,
                `releaseDate` TEXT,
                `addedAtEpochSeconds` INTEGER,
                `sortIndex` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_movies_playlistId` ON `movies` (`playlistId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_movies_categoryId` ON `movies` (`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_movies_playlistId_title` ON `movies` (`playlistId`, `title`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `series_categories` (
                `id` TEXT NOT NULL,
                `playlistId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `sortIndex` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_categories_playlistId` ON `series_categories` (`playlistId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_series_categories_playlistId_name` ON `series_categories` (`playlistId`, `name`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `series` (
                `id` TEXT NOT NULL,
                `playlistId` TEXT NOT NULL,
                `categoryId` TEXT,
                `title` TEXT NOT NULL,
                `coverUrl` TEXT,
                `plot` TEXT,
                `episodeRunTimeMinutes` INTEGER,
                `releaseDate` TEXT,
                `addedAtEpochSeconds` INTEGER,
                `sortIndex` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_playlistId` ON `series` (`playlistId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_categoryId` ON `series` (`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_playlistId_title` ON `series` (`playlistId`, `title`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `seasons` (
                `id` TEXT NOT NULL,
                `playlistId` TEXT NOT NULL,
                `seriesId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `seasonNumber` INTEGER NOT NULL,
                `coverUrl` TEXT,
                `plot` TEXT,
                `sortIndex` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`seriesId`) REFERENCES `series`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_seasons_playlistId` ON `seasons` (`playlistId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_seasons_seriesId` ON `seasons` (`seriesId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_seasons_seriesId_seasonNumber` ON `seasons` (`seriesId`, `seasonNumber`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `episodes` (
                `id` TEXT NOT NULL,
                `playlistId` TEXT NOT NULL,
                `seriesId` TEXT NOT NULL,
                `seasonId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `streamUrl` TEXT NOT NULL,
                `episodeNumber` INTEGER NOT NULL,
                `plot` TEXT,
                `durationMinutes` INTEGER,
                `coverUrl` TEXT,
                `addedAtEpochSeconds` INTEGER,
                `sortIndex` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`seriesId`) REFERENCES `series`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`seasonId`) REFERENCES `seasons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_playlistId` ON `episodes` (`playlistId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_seriesId` ON `episodes` (`seriesId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_seasonId` ON `episodes` (`seasonId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_episodes_seasonId_episodeNumber` ON `episodes` (`seasonId`, `episodeNumber`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `movie_playback_progress` (
                `movieId` TEXT NOT NULL,
                `positionMs` INTEGER NOT NULL,
                `durationMs` INTEGER,
                `completed` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`movieId`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_movie_playback_progress_updatedAtEpochMillis` ON `movie_playback_progress` (`updatedAtEpochMillis`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `episode_playback_progress` (
                `episodeId` TEXT NOT NULL,
                `positionMs` INTEGER NOT NULL,
                `durationMs` INTEGER,
                `completed` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`episodeId`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_episode_playback_progress_updatedAtEpochMillis` ON `episode_playback_progress` (`updatedAtEpochMillis`)")
    }
}
