package com.vivicast.tv.core.database

import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

object VivicastDatabaseCallbacks {
    val SearchFtsCallback: RoomDatabase.Callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.createSearchFtsTriggers()
        }
    }
}

object VivicastMigrations {
    val Migration1To2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addTextColumn("providers", "stableKey")
            db.execSQL("UPDATE providers SET stableKey = id WHERE stableKey = ''")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_providers_stableKey` ON `providers` (`stableKey`)")

            db.addTextColumn("categories", "stableKey")
            db.execSQL("UPDATE categories SET stableKey = id WHERE stableKey = ''")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_providerId_stableKey` ON `categories` (`providerId`, `stableKey`)")

            db.addTextColumn("channels", "stableKey")
            db.execSQL("UPDATE channels SET stableKey = id WHERE stableKey = ''")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_channels_providerId_stableKey` ON `channels` (`providerId`, `stableKey`)")

            db.addTextColumn("movies", "stableKey")
            db.execSQL("ALTER TABLE movies ADD COLUMN ageRating TEXT")
            db.addBooleanColumn("movies", "isAdult")
            db.execSQL("UPDATE movies SET stableKey = id WHERE stableKey = ''")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_movies_providerId_stableKey` ON `movies` (`providerId`, `stableKey`)")

            db.addTextColumn("series", "stableKey")
            db.execSQL("ALTER TABLE series ADD COLUMN ageRating TEXT")
            db.addBooleanColumn("series", "isAdult")
            db.execSQL("UPDATE series SET stableKey = id WHERE stableKey = ''")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_series_providerId_stableKey` ON `series` (`providerId`, `stableKey`)")

            db.addTextColumn("seasons", "stableKey")
            db.execSQL("UPDATE seasons SET stableKey = id WHERE stableKey = ''")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_seasons_providerId_stableKey` ON `seasons` (`providerId`, `stableKey`)")

            db.addTextColumn("episodes", "stableKey")
            db.execSQL("ALTER TABLE episodes ADD COLUMN ageRating TEXT")
            db.addBooleanColumn("episodes", "isAdult")
            db.execSQL("UPDATE episodes SET stableKey = id WHERE stableKey = ''")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_episodes_providerId_stableKey` ON `episodes` (`providerId`, `stableKey`)")

            db.addTextColumn("epg_sources", "stableKey")
            db.execSQL("ALTER TABLE epg_sources ADD COLUMN lastRefreshAt INTEGER")
            db.execSQL("ALTER TABLE epg_sources ADD COLUMN lastProgramCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE epg_sources SET stableKey = id WHERE stableKey = ''")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_epg_sources_stableKey` ON `epg_sources` (`stableKey`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `epg_channels` (
                    `id` TEXT NOT NULL,
                    `epgSourceId` TEXT NOT NULL,
                    `stableKey` TEXT NOT NULL,
                    `remoteId` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `iconUrl` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_epg_channels_epgSourceId` ON `epg_channels` (`epgSourceId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_epg_channels_epgSourceId_stableKey` ON `epg_channels` (`epgSourceId`, `stableKey`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_epg_channels_epgSourceId_remoteId` ON `epg_channels` (`epgSourceId`, `remoteId`)")

            db.addTextColumn("epg_programs", "stableKey")
            db.addTextColumn("epg_programs", "normalizedTitle")
            db.execSQL("UPDATE epg_programs SET stableKey = id WHERE stableKey = ''")
            db.execSQL("UPDATE epg_programs SET normalizedTitle = title WHERE normalizedTitle = ''")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_epg_programs_epgSourceId_stableKey` ON `epg_programs` (`epgSourceId`, `stableKey`)")

            db.addTextColumn("epg_channel_mappings", "channelStableKey")
            db.addTextColumn("epg_channel_mappings", "epgSourceStableKey")
            db.addTextColumn("epg_channel_mappings", "epgChannelStableKey")
            db.execSQL("ALTER TABLE epg_channel_mappings ADD COLUMN confidence REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE epg_channel_mappings ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE epg_channel_mappings SET channelStableKey = channelId WHERE channelStableKey = ''")
            db.execSQL("UPDATE epg_channel_mappings SET epgSourceStableKey = epgSourceId WHERE epgSourceStableKey = ''")
            db.execSQL("UPDATE epg_channel_mappings SET epgChannelStableKey = epgChannelId WHERE epgChannelStableKey = ''")
            db.execSQL("UPDATE epg_channel_mappings SET updatedAt = createdAt WHERE updatedAt = 0")

            db.addTextColumn("favorites", "mediaStableKey")
            db.addBooleanColumn("favorites", "isPending")
            db.execSQL("UPDATE favorites SET mediaStableKey = mediaId WHERE mediaStableKey = ''")

            db.addTextColumn("playback_progress", "mediaStableKey")
            db.addBooleanColumn("playback_progress", "isPending")
            db.execSQL("UPDATE playback_progress SET mediaStableKey = mediaId WHERE mediaStableKey = ''")

            db.addTextColumn("channel_history", "channelStableKey")
            db.addBooleanColumn("channel_history", "isPending")
            db.execSQL("UPDATE channel_history SET channelStableKey = channelId WHERE channelStableKey = ''")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `search_history` (
                    `id` TEXT NOT NULL,
                    `query` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_search_history_query` ON `search_history` (`query`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_search_history_updatedAt` ON `search_history` (`updatedAt`)")
        }
    }

    val Migration2To3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addTextColumn("search_history", "normalizedQuery")
            db.execSQL("ALTER TABLE search_history ADD COLUMN lastUsedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE search_history SET normalizedQuery = lower(trim(query)) WHERE normalizedQuery = ''")
            db.execSQL("UPDATE search_history SET lastUsedAt = updatedAt WHERE lastUsedAt = 0")
            db.execSQL("DELETE FROM search_history WHERE rowid NOT IN (SELECT max(rowid) FROM search_history GROUP BY normalizedQuery)")
            db.execSQL("DROP INDEX IF EXISTS `index_search_history_query`")
            db.execSQL("DROP INDEX IF EXISTS `index_search_history_updatedAt`")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_search_history_normalizedQuery` ON `search_history` (`normalizedQuery`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_search_history_lastUsedAt` ON `search_history` (`lastUsedAt`)")
        }
    }

    val Migration3To4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.createSearchFtsTables()
            db.createSearchFtsTriggers()
            db.rebuildSearchFts()
        }
    }

    val Migration4To5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.createAndroidTvSearchEntriesTable()
            db.rebuildAndroidTvSearchEntries()
        }
    }

    val Migration5To6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.migrateSourceConfigNames()
            db.migrateEpgProgramChannelNames()
            db.createSearchFtsTriggers()
            db.rebuildSearchEpgFts()
        }
    }

    // Android-TV system-search index becomes a pure content mirror (adds isAdult); protection is now
    // applied at read time, so the index no longer bakes in PIN state. Rebuild it as a full mirror.
    val Migration6To7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addBooleanColumn("android_tv_search_entries", "isAdult")
            db.rebuildAndroidTvSearchEntriesAsMirror()
        }
    }

    // Xtream account info persisted on the provider (expiry + max connections), refreshed on import.
    val Migration7To8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addNullableIntegerColumn("providers", "xtreamExpiresAtMillis")
            db.addNullableIntegerColumn("providers", "xtreamMaxConnections")
        }
    }

    // EPG source now stores the feed's channel count, written on refresh alongside lastRefreshAt.
    val Migration8To9: Migration = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE epg_sources ADD COLUMN lastChannelCount INTEGER NOT NULL DEFAULT 0")
        }
    }

    // In-progress flag on the EPG source so the overview can show a "Refreshing" badge.
    val Migration9To10: Migration = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE epg_sources ADD COLUMN isRefreshing INTEGER NOT NULL DEFAULT 0")
        }
    }

    // Per-provider User-Agent + refresh-on-app-start flag. Auto-refresh becomes per-provider, so the
    // hourly interval now means "off" when 0 — reset every existing playlist to off (opt-in per playlist).
    val Migration10To11: Migration = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE providers ADD COLUMN userAgent TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE providers ADD COLUMN refreshOnAppStartEnabled INTEGER NOT NULL DEFAULT 1")
            db.execSQL("UPDATE providers SET refreshIntervalHours = 0")
        }
    }

    // Per-EPG-source refresh interval (replaces the single global EPG interval). Existing sources start
    // "off" (0) — opt-in per source, mirroring the per-playlist interval reset in Migration10To11.
    val Migration11To12: Migration = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE epg_sources ADD COLUMN refreshIntervalHours INTEGER NOT NULL DEFAULT 0")
        }
    }

    // Per-provider last-refresh timestamp — one persisted clock that both the foreground interval loop
    // and the background periodic phase read from. NULL = never refreshed.
    val Migration12To13: Migration = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // DEFAULT NULL to match the entity's @ColumnInfo(defaultValue = "NULL"), else Room's schema
            // validation rejects the migration (as the other nullable provider columns do).
            db.execSQL("ALTER TABLE providers ADD COLUMN lastRefreshAt INTEGER DEFAULT NULL")
        }
    }
}

private fun SupportSQLiteDatabase.addTextColumn(tableName: String, columnName: String) {
    execSQL("ALTER TABLE $tableName ADD COLUMN $columnName TEXT NOT NULL DEFAULT ''")
}

private fun SupportSQLiteDatabase.addBooleanColumn(tableName: String, columnName: String) {
    execSQL("ALTER TABLE $tableName ADD COLUMN $columnName INTEGER NOT NULL DEFAULT 0")
}

private fun SupportSQLiteDatabase.addNullableIntegerColumn(tableName: String, columnName: String) {
    execSQL("ALTER TABLE $tableName ADD COLUMN $columnName INTEGER DEFAULT NULL")
}

private fun SupportSQLiteDatabase.migrateSourceConfigNames() {
    execSQL("DROP INDEX IF EXISTS `index_providers_stableKey`")
    execSQL("DROP INDEX IF EXISTS `index_providers_type`")
    execSQL("DROP INDEX IF EXISTS `index_providers_name`")
    execSQL("DROP INDEX IF EXISTS `index_providers_status`")
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS `providers_new` (
            `id` TEXT NOT NULL,
            `stableKey` TEXT NOT NULL DEFAULT '',
            `name` TEXT NOT NULL,
            `type` TEXT NOT NULL,
            `sourceConfigKey` TEXT NOT NULL,
            `isActive` INTEGER NOT NULL,
            `status` TEXT NOT NULL,
            `includeLiveTv` INTEGER NOT NULL,
            `includeMovies` INTEGER NOT NULL,
            `includeSeries` INTEGER NOT NULL,
            `refreshIntervalHours` INTEGER NOT NULL,
            `logoPriority` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
        """.trimIndent(),
    )
    execSQL(
        """
        INSERT INTO `providers_new` (
            `id`, `stableKey`, `name`, `type`, `sourceConfigKey`, `isActive`, `status`,
            `includeLiveTv`, `includeMovies`, `includeSeries`, `refreshIntervalHours`,
            `logoPriority`, `createdAt`, `updatedAt`
        )
        SELECT
            `id`, `stableKey`, `name`, `type`, `credentialsKey`, `isActive`, `status`,
            `includeLiveTv`, `includeMovies`, `includeSeries`, `refreshIntervalHours`,
            `logoPriority`, `createdAt`, `updatedAt`
        FROM `providers`
        """.trimIndent(),
    )
    execSQL("DROP TABLE `providers`")
    execSQL("ALTER TABLE `providers_new` RENAME TO `providers`")
    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_providers_stableKey` ON `providers` (`stableKey`)")
    execSQL("CREATE INDEX IF NOT EXISTS `index_providers_type` ON `providers` (`type`)")
    execSQL("CREATE INDEX IF NOT EXISTS `index_providers_name` ON `providers` (`name`)")
    execSQL("CREATE INDEX IF NOT EXISTS `index_providers_status` ON `providers` (`status`)")

    execSQL("DROP INDEX IF EXISTS `index_epg_sources_stableKey`")
    execSQL("DROP INDEX IF EXISTS `index_epg_sources_name`")
    execSQL("DROP INDEX IF EXISTS `index_epg_sources_urlKey`")
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS `epg_sources_new` (
            `id` TEXT NOT NULL,
            `stableKey` TEXT NOT NULL DEFAULT '',
            `name` TEXT NOT NULL,
            `sourceConfigKey` TEXT NOT NULL,
            `timeShiftMinutes` INTEGER NOT NULL,
            `isActive` INTEGER NOT NULL,
            `lastRefreshAt` INTEGER,
            `lastProgramCount` INTEGER NOT NULL DEFAULT 0,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
        """.trimIndent(),
    )
    execSQL(
        """
        INSERT INTO `epg_sources_new` (
            `id`, `stableKey`, `name`, `sourceConfigKey`, `timeShiftMinutes`, `isActive`,
            `lastRefreshAt`, `lastProgramCount`, `createdAt`, `updatedAt`
        )
        SELECT
            `id`, `stableKey`, `name`, `urlKey`, `timeShiftMinutes`, `isActive`,
            `lastRefreshAt`, `lastProgramCount`, `createdAt`, `updatedAt`
        FROM `epg_sources`
        """.trimIndent(),
    )
    execSQL("DROP TABLE `epg_sources`")
    execSQL("ALTER TABLE `epg_sources_new` RENAME TO `epg_sources`")
    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_epg_sources_stableKey` ON `epg_sources` (`stableKey`)")
    execSQL("CREATE INDEX IF NOT EXISTS `index_epg_sources_name` ON `epg_sources` (`name`)")
    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_epg_sources_sourceConfigKey` ON `epg_sources` (`sourceConfigKey`)")
}

private fun SupportSQLiteDatabase.migrateEpgProgramChannelNames() {
    execSQL("DROP INDEX IF EXISTS `index_epg_programs_providerId`")
    execSQL("DROP INDEX IF EXISTS `index_epg_programs_epgSourceId_stableKey`")
    execSQL("DROP INDEX IF EXISTS `index_epg_programs_channelId`")
    execSQL("DROP INDEX IF EXISTS `index_epg_programs_epgSourceId`")
    execSQL("DROP INDEX IF EXISTS `index_epg_programs_channelId_startTime_endTime`")
    execSQL("DROP INDEX IF EXISTS `index_epg_programs_providerId_title`")
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS `epg_programs_new` (
            `id` TEXT NOT NULL,
            `providerId` TEXT NOT NULL,
            `channelId` TEXT NOT NULL,
            `epgSourceId` TEXT NOT NULL,
            `stableKey` TEXT NOT NULL DEFAULT '',
            `epgChannelId` TEXT NOT NULL,
            `title` TEXT NOT NULL,
            `normalizedTitle` TEXT NOT NULL DEFAULT '',
            `subtitle` TEXT,
            `description` TEXT,
            `startTime` INTEGER NOT NULL,
            `endTime` INTEGER NOT NULL,
            `category` TEXT,
            `iconUrl` TEXT,
            `isCatchupAvailable` INTEGER NOT NULL,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
        """.trimIndent(),
    )
    execSQL(
        """
        INSERT INTO `epg_programs_new` (
            `id`, `providerId`, `channelId`, `epgSourceId`, `stableKey`, `epgChannelId`,
            `title`, `normalizedTitle`, `subtitle`, `description`, `startTime`, `endTime`,
            `category`, `iconUrl`, `isCatchupAvailable`, `createdAt`, `updatedAt`
        )
        SELECT
            `id`, `providerId`, `channelId`, `epgSourceId`, `stableKey`, `externalChannelId`,
            `title`, `normalizedTitle`, `subtitle`, `description`, `startTime`, `endTime`,
            `category`, `iconUrl`, `isCatchupAvailable`, `createdAt`, `updatedAt`
        FROM `epg_programs`
        """.trimIndent(),
    )
    execSQL("DROP TABLE `epg_programs`")
    execSQL("ALTER TABLE `epg_programs_new` RENAME TO `epg_programs`")
    execSQL("CREATE INDEX IF NOT EXISTS `index_epg_programs_providerId` ON `epg_programs` (`providerId`)")
    execSQL(
        """
        CREATE UNIQUE INDEX IF NOT EXISTS `index_epg_programs_epgSourceId_epgChannelId_stableKey`
        ON `epg_programs` (`epgSourceId`, `epgChannelId`, `stableKey`)
        """.trimIndent(),
    )
    execSQL("CREATE INDEX IF NOT EXISTS `index_epg_programs_channelId` ON `epg_programs` (`channelId`)")
    execSQL("CREATE INDEX IF NOT EXISTS `index_epg_programs_epgSourceId` ON `epg_programs` (`epgSourceId`)")
    execSQL(
        """
        CREATE INDEX IF NOT EXISTS `index_epg_programs_epgSourceId_epgChannelId_startTime_endTime`
        ON `epg_programs` (`epgSourceId`, `epgChannelId`, `startTime`, `endTime`)
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE INDEX IF NOT EXISTS `index_epg_programs_channelId_startTime_endTime`
        ON `epg_programs` (`channelId`, `startTime`, `endTime`)
        """.trimIndent(),
    )
    execSQL("CREATE INDEX IF NOT EXISTS `index_epg_programs_providerId_title` ON `epg_programs` (`providerId`, `title`)")
}

private fun SupportSQLiteDatabase.createSearchFtsTables() {
    execSQL(
        """
        CREATE VIRTUAL TABLE IF NOT EXISTS `search_channels_fts` USING FTS4(
            `mediaId` TEXT NOT NULL,
            `providerId` TEXT NOT NULL,
            `stableKey` TEXT NOT NULL,
            `categoryId` TEXT,
            `name` TEXT NOT NULL,
            `channelNumber` TEXT,
            tokenize=unicode61,
            notindexed=`mediaId`,
            notindexed=`providerId`,
            notindexed=`stableKey`,
            notindexed=`categoryId`
        )
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE VIRTUAL TABLE IF NOT EXISTS `search_movies_fts` USING FTS4(
            `mediaId` TEXT NOT NULL,
            `providerId` TEXT NOT NULL,
            `stableKey` TEXT NOT NULL,
            `categoryId` TEXT,
            `name` TEXT NOT NULL,
            `originalName` TEXT,
            `genre` TEXT,
            tokenize=unicode61,
            notindexed=`mediaId`,
            notindexed=`providerId`,
            notindexed=`stableKey`,
            notindexed=`categoryId`
        )
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE VIRTUAL TABLE IF NOT EXISTS `search_series_fts` USING FTS4(
            `mediaId` TEXT NOT NULL,
            `providerId` TEXT NOT NULL,
            `stableKey` TEXT NOT NULL,
            `categoryId` TEXT,
            `name` TEXT NOT NULL,
            `originalName` TEXT,
            `genre` TEXT,
            tokenize=unicode61,
            notindexed=`mediaId`,
            notindexed=`providerId`,
            notindexed=`stableKey`,
            notindexed=`categoryId`
        )
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE VIRTUAL TABLE IF NOT EXISTS `search_epg_fts` USING FTS4(
            `programId` TEXT NOT NULL,
            `providerId` TEXT NOT NULL,
            `channelId` TEXT NOT NULL,
            `epgSourceId` TEXT NOT NULL,
            `title` TEXT NOT NULL,
            `subtitle` TEXT,
            `description` TEXT,
            tokenize=unicode61,
            notindexed=`programId`,
            notindexed=`providerId`,
            notindexed=`channelId`,
            notindexed=`epgSourceId`
        )
        """.trimIndent(),
    )
}

internal fun SupportSQLiteDatabase.createSearchFtsTriggers() {
    execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `search_channels_fts_insert`
        AFTER INSERT ON `channels`
        BEGIN
            INSERT INTO `search_channels_fts` (`mediaId`, `providerId`, `stableKey`, `categoryId`, `name`, `channelNumber`)
            VALUES (new.`id`, new.`providerId`, new.`stableKey`, new.`categoryId`, new.`name`, new.`channelNumber`);
        END
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `search_channels_fts_update`
        AFTER UPDATE ON `channels`
        BEGIN
            DELETE FROM `search_channels_fts` WHERE `mediaId` = old.`id`;
            INSERT INTO `search_channels_fts` (`mediaId`, `providerId`, `stableKey`, `categoryId`, `name`, `channelNumber`)
            VALUES (new.`id`, new.`providerId`, new.`stableKey`, new.`categoryId`, new.`name`, new.`channelNumber`);
        END
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `search_channels_fts_delete`
        AFTER DELETE ON `channels`
        BEGIN
            DELETE FROM `search_channels_fts` WHERE `mediaId` = old.`id`;
        END
        """.trimIndent(),
    )

    execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `search_movies_fts_insert`
        AFTER INSERT ON `movies`
        BEGIN
            INSERT INTO `search_movies_fts` (`mediaId`, `providerId`, `stableKey`, `categoryId`, `name`, `originalName`, `genre`)
            VALUES (new.`id`, new.`providerId`, new.`stableKey`, new.`categoryId`, new.`name`, new.`originalName`, new.`genre`);
        END
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `search_movies_fts_update`
        AFTER UPDATE ON `movies`
        BEGIN
            DELETE FROM `search_movies_fts` WHERE `mediaId` = old.`id`;
            INSERT INTO `search_movies_fts` (`mediaId`, `providerId`, `stableKey`, `categoryId`, `name`, `originalName`, `genre`)
            VALUES (new.`id`, new.`providerId`, new.`stableKey`, new.`categoryId`, new.`name`, new.`originalName`, new.`genre`);
        END
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `search_movies_fts_delete`
        AFTER DELETE ON `movies`
        BEGIN
            DELETE FROM `search_movies_fts` WHERE `mediaId` = old.`id`;
        END
        """.trimIndent(),
    )

    execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `search_series_fts_insert`
        AFTER INSERT ON `series`
        BEGIN
            INSERT INTO `search_series_fts` (`mediaId`, `providerId`, `stableKey`, `categoryId`, `name`, `originalName`, `genre`)
            VALUES (new.`id`, new.`providerId`, new.`stableKey`, new.`categoryId`, new.`name`, new.`originalName`, new.`genre`);
        END
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `search_series_fts_update`
        AFTER UPDATE ON `series`
        BEGIN
            DELETE FROM `search_series_fts` WHERE `mediaId` = old.`id`;
            INSERT INTO `search_series_fts` (`mediaId`, `providerId`, `stableKey`, `categoryId`, `name`, `originalName`, `genre`)
            VALUES (new.`id`, new.`providerId`, new.`stableKey`, new.`categoryId`, new.`name`, new.`originalName`, new.`genre`);
        END
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `search_series_fts_delete`
        AFTER DELETE ON `series`
        BEGIN
            DELETE FROM `search_series_fts` WHERE `mediaId` = old.`id`;
        END
        """.trimIndent(),
    )

    execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `search_epg_fts_insert`
        AFTER INSERT ON `epg_programs`
        BEGIN
            INSERT INTO `search_epg_fts` (`programId`, `providerId`, `channelId`, `epgSourceId`, `title`, `subtitle`, `description`)
            VALUES (new.`id`, new.`providerId`, new.`channelId`, new.`epgSourceId`, new.`title`, new.`subtitle`, new.`description`);
        END
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `search_epg_fts_update`
        AFTER UPDATE ON `epg_programs`
        BEGIN
            DELETE FROM `search_epg_fts` WHERE `programId` = old.`id`;
            INSERT INTO `search_epg_fts` (`programId`, `providerId`, `channelId`, `epgSourceId`, `title`, `subtitle`, `description`)
            VALUES (new.`id`, new.`providerId`, new.`channelId`, new.`epgSourceId`, new.`title`, new.`subtitle`, new.`description`);
        END
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `search_epg_fts_delete`
        AFTER DELETE ON `epg_programs`
        BEGIN
            DELETE FROM `search_epg_fts` WHERE `programId` = old.`id`;
        END
        """.trimIndent(),
    )
}

private fun SupportSQLiteDatabase.rebuildSearchFts() {
    execSQL(
        """
        INSERT INTO `search_channels_fts` (`mediaId`, `providerId`, `stableKey`, `categoryId`, `name`, `channelNumber`)
        SELECT `id`, `providerId`, `stableKey`, `categoryId`, `name`, `channelNumber`
        FROM `channels`
        """.trimIndent(),
    )
    execSQL(
        """
        INSERT INTO `search_movies_fts` (`mediaId`, `providerId`, `stableKey`, `categoryId`, `name`, `originalName`, `genre`)
        SELECT `id`, `providerId`, `stableKey`, `categoryId`, `name`, `originalName`, `genre`
        FROM `movies`
        """.trimIndent(),
    )
    execSQL(
        """
        INSERT INTO `search_series_fts` (`mediaId`, `providerId`, `stableKey`, `categoryId`, `name`, `originalName`, `genre`)
        SELECT `id`, `providerId`, `stableKey`, `categoryId`, `name`, `originalName`, `genre`
        FROM `series`
        """.trimIndent(),
    )
    execSQL(
        """
        INSERT INTO `search_epg_fts` (`programId`, `providerId`, `channelId`, `epgSourceId`, `title`, `subtitle`, `description`)
        SELECT `id`, `providerId`, `channelId`, `epgSourceId`, `title`, `subtitle`, `description`
        FROM `epg_programs`
        """.trimIndent(),
    )
}

private fun SupportSQLiteDatabase.rebuildSearchEpgFts() {
    execSQL("DELETE FROM `search_epg_fts`")
    execSQL(
        """
        INSERT INTO `search_epg_fts` (`programId`, `providerId`, `channelId`, `epgSourceId`, `title`, `subtitle`, `description`)
        SELECT `id`, `providerId`, `channelId`, `epgSourceId`, `title`, `subtitle`, `description`
        FROM `epg_programs`
        """.trimIndent(),
    )
}

private fun SupportSQLiteDatabase.createAndroidTvSearchEntriesTable() {
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS `android_tv_search_entries` (
            `id` TEXT NOT NULL,
            `mediaType` TEXT NOT NULL,
            `providerStableKey` TEXT NOT NULL,
            `mediaStableKey` TEXT NOT NULL,
            `title` TEXT NOT NULL,
            `subtitle` TEXT,
            `imageUrl` TEXT,
            `deepLink` TEXT NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
        """.trimIndent(),
    )
    execSQL("CREATE INDEX IF NOT EXISTS `index_android_tv_search_entries_mediaType` ON `android_tv_search_entries` (`mediaType`)")
    execSQL(
        """
        CREATE UNIQUE INDEX IF NOT EXISTS `index_android_tv_search_entries_providerStableKey_mediaType_mediaStableKey`
        ON `android_tv_search_entries` (`providerStableKey`, `mediaType`, `mediaStableKey`)
        """.trimIndent(),
    )
}

// Pure-mirror rebuild (v7+): includes an isAdult column and does NOT exclude adult content — read-time
// filtering in AndroidTvSearchDao.searchEntries applies protection. Kept separate from the historical
// v4->5 rebuild (which has no isAdult column and must stay reproducible).
private fun SupportSQLiteDatabase.rebuildAndroidTvSearchEntriesAsMirror() {
    execSQL("DELETE FROM `android_tv_search_entries`")
    execSQL(
        """
        INSERT INTO `android_tv_search_entries` (
            `id`, `mediaType`, `providerStableKey`, `mediaStableKey`, `title`, `subtitle`,
            `imageUrl`, `deepLink`, `isAdult`, `updatedAt`
        )
        SELECT
            'CHANNEL:' || providers.`stableKey` || ':' || channels.`stableKey`,
            'CHANNEL',
            providers.`stableKey`,
            channels.`stableKey`,
            channels.`name`,
            'Live-TV',
            channels.`logoUrl`,
            'vivicast://channel/' || providers.`stableKey` || '/' || channels.`stableKey`,
            0,
            MAX(providers.`updatedAt`, channels.`updatedAt`)
        FROM `channels`
        INNER JOIN `providers` ON providers.`id` = channels.`providerId`
        WHERE providers.`isActive` = 1
            AND providers.`status` != 'DISABLED'
            AND providers.`includeLiveTv` = 1
        """.trimIndent(),
    )
    execSQL(
        """
        INSERT INTO `android_tv_search_entries` (
            `id`, `mediaType`, `providerStableKey`, `mediaStableKey`, `title`, `subtitle`,
            `imageUrl`, `deepLink`, `isAdult`, `updatedAt`
        )
        SELECT
            'MOVIE:' || providers.`stableKey` || ':' || movies.`stableKey`,
            'MOVIE',
            providers.`stableKey`,
            movies.`stableKey`,
            movies.`name`,
            COALESCE(movies.`year`, 'Film'),
            COALESCE(movies.`posterUrl`, movies.`backdropUrl`),
            'vivicast://movie/' || providers.`stableKey` || '/' || movies.`stableKey`,
            movies.`isAdult`,
            MAX(providers.`updatedAt`, movies.`updatedAt`)
        FROM `movies`
        INNER JOIN `providers` ON providers.`id` = movies.`providerId`
        WHERE providers.`isActive` = 1
            AND providers.`status` != 'DISABLED'
            AND providers.`includeMovies` = 1
        """.trimIndent(),
    )
    execSQL(
        """
        INSERT INTO `android_tv_search_entries` (
            `id`, `mediaType`, `providerStableKey`, `mediaStableKey`, `title`, `subtitle`,
            `imageUrl`, `deepLink`, `isAdult`, `updatedAt`
        )
        SELECT
            'SERIES:' || providers.`stableKey` || ':' || series.`stableKey`,
            'SERIES',
            providers.`stableKey`,
            series.`stableKey`,
            series.`name`,
            COALESCE(series.`year`, 'Serie'),
            COALESCE(series.`posterUrl`, series.`backdropUrl`),
            'vivicast://series/' || providers.`stableKey` || '/' || series.`stableKey`,
            series.`isAdult`,
            MAX(providers.`updatedAt`, series.`updatedAt`)
        FROM `series`
        INNER JOIN `providers` ON providers.`id` = series.`providerId`
        WHERE providers.`isActive` = 1
            AND providers.`status` != 'DISABLED'
            AND providers.`includeSeries` = 1
        """.trimIndent(),
    )
}

private fun SupportSQLiteDatabase.rebuildAndroidTvSearchEntries() {
    execSQL("DELETE FROM `android_tv_search_entries`")
    execSQL(
        """
        INSERT INTO `android_tv_search_entries` (
            `id`,
            `mediaType`,
            `providerStableKey`,
            `mediaStableKey`,
            `title`,
            `subtitle`,
            `imageUrl`,
            `deepLink`,
            `updatedAt`
        )
        SELECT
            'CHANNEL:' || providers.`stableKey` || ':' || channels.`stableKey`,
            'CHANNEL',
            providers.`stableKey`,
            channels.`stableKey`,
            channels.`name`,
            'Live-TV',
            channels.`logoUrl`,
            'vivicast://channel/' || providers.`stableKey` || '/' || channels.`stableKey`,
            MAX(providers.`updatedAt`, channels.`updatedAt`)
        FROM `channels`
        INNER JOIN `providers` ON providers.`id` = channels.`providerId`
        WHERE providers.`isActive` = 1
            AND providers.`status` != 'DISABLED'
            AND providers.`includeLiveTv` = 1
        """.trimIndent(),
    )
    execSQL(
        """
        INSERT INTO `android_tv_search_entries` (
            `id`,
            `mediaType`,
            `providerStableKey`,
            `mediaStableKey`,
            `title`,
            `subtitle`,
            `imageUrl`,
            `deepLink`,
            `updatedAt`
        )
        SELECT
            'MOVIE:' || providers.`stableKey` || ':' || movies.`stableKey`,
            'MOVIE',
            providers.`stableKey`,
            movies.`stableKey`,
            movies.`name`,
            COALESCE(movies.`year`, 'Film'),
            COALESCE(movies.`posterUrl`, movies.`backdropUrl`),
            'vivicast://movie/' || providers.`stableKey` || '/' || movies.`stableKey`,
            MAX(providers.`updatedAt`, movies.`updatedAt`)
        FROM `movies`
        INNER JOIN `providers` ON providers.`id` = movies.`providerId`
        WHERE providers.`isActive` = 1
            AND providers.`status` != 'DISABLED'
            AND providers.`includeMovies` = 1
            AND movies.`isAdult` = 0
        """.trimIndent(),
    )
    execSQL(
        """
        INSERT INTO `android_tv_search_entries` (
            `id`,
            `mediaType`,
            `providerStableKey`,
            `mediaStableKey`,
            `title`,
            `subtitle`,
            `imageUrl`,
            `deepLink`,
            `updatedAt`
        )
        SELECT
            'SERIES:' || providers.`stableKey` || ':' || series.`stableKey`,
            'SERIES',
            providers.`stableKey`,
            series.`stableKey`,
            series.`name`,
            COALESCE(series.`year`, 'Serie'),
            COALESCE(series.`posterUrl`, series.`backdropUrl`),
            'vivicast://series/' || providers.`stableKey` || '/' || series.`stableKey`,
            MAX(providers.`updatedAt`, series.`updatedAt`)
        FROM `series`
        INNER JOIN `providers` ON providers.`id` = series.`providerId`
        WHERE providers.`isActive` = 1
            AND providers.`status` != 'DISABLED'
            AND providers.`includeSeries` = 1
            AND series.`isAdult` = 0
        """.trimIndent(),
    )
}
