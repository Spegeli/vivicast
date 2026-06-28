package com.vivicast.tv.core.database

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VivicastDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VivicastDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migratesVersionOneFixtureToLatestSchema() {
        helper.createDatabase(TEST_DB, 1).apply {
            seedVersionOneFixture()
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            VIVICAST_DATABASE_VERSION,
            true,
            VivicastMigrations.Migration1To2,
            VivicastMigrations.Migration2To3,
            VivicastMigrations.Migration3To4,
            VivicastMigrations.Migration4To5,
            VivicastMigrations.Migration5To6,
        )

        migrated.use {
            assertEquals("provider-1", it.stringValue("SELECT stableKey FROM providers WHERE id = 'provider-1'"))
            assertEquals("secret-key-1", it.stringValue("SELECT sourceConfigKey FROM providers WHERE id = 'provider-1'"))
            assertEquals("channel-1", it.stringValue("SELECT stableKey FROM channels WHERE id = 'channel-1'"))
            assertEquals("movie-1", it.stringValue("SELECT stableKey FROM movies WHERE id = 'movie-1'"))
            assertEquals("series-1", it.stringValue("SELECT stableKey FROM series WHERE id = 'series-1'"))
            assertEquals("epg-url-key-1", it.stringValue("SELECT sourceConfigKey FROM epg_sources WHERE id = 'epg-source-1'"))
            assertEquals("external-channel-1", it.stringValue("SELECT epgChannelId FROM epg_programs WHERE id = 'program-1'"))
            assertEquals("channel-1", it.stringValue("SELECT channelStableKey FROM channel_history WHERE id = 'history-1'"))
            assertEquals("movie-1", it.stringValue("SELECT mediaStableKey FROM favorites WHERE id = 'favorite-1'"))
            assertEquals("episode-1", it.stringValue("SELECT mediaStableKey FROM playback_progress WHERE id = 'progress-1'"))
            assertEquals("fixture news", it.stringValue("SELECT name FROM search_channels_fts WHERE mediaId = 'channel-1'"))
            assertEquals("fixture movie", it.stringValue("SELECT name FROM search_movies_fts WHERE mediaId = 'movie-1'"))
            assertEquals("fixture series", it.stringValue("SELECT name FROM search_series_fts WHERE mediaId = 'series-1'"))
            assertEquals("fixture show", it.stringValue("SELECT title FROM search_epg_fts WHERE programId = 'program-1'"))
            assertEquals(3, it.longValue("SELECT COUNT(*) FROM android_tv_search_entries").toInt())
        }
    }

    @Test
    fun validatesEachKnownMigrationStep() {
        validateMigrationStep("migration-1-2.db", 1, 2, VivicastMigrations.Migration1To2)
        validateMigrationStep("migration-2-3.db", 2, 3, VivicastMigrations.Migration2To3)
        validateMigrationStep("migration-3-4.db", 3, 4, VivicastMigrations.Migration3To4)
        validateMigrationStep("migration-4-5.db", 4, 5, VivicastMigrations.Migration4To5)
        validateMigrationStep("migration-5-6.db", 5, 6, VivicastMigrations.Migration5To6)
    }

    private fun validateMigrationStep(
        databaseName: String,
        startVersion: Int,
        endVersion: Int,
        migration: androidx.room.migration.Migration,
    ) {
        helper.createDatabase(databaseName, startVersion).close()
        helper.runMigrationsAndValidate(databaseName, endVersion, true, migration).close()
    }

    private fun SupportSQLiteDatabase.seedVersionOneFixture() {
        execSQL(
            """
            INSERT INTO providers (
                id, name, type, credentialsKey, isActive, status, includeLiveTv, includeMovies,
                includeSeries, refreshIntervalHours, logoPriority, createdAt, updatedAt
            ) VALUES (
                'provider-1', 'Fixture Provider', 'M3U', 'secret-key-1', 1, 'READY', 1, 1,
                1, 24, 'PLAYLIST', 1, 2
            )
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO categories (
                id, providerId, type, remoteId, name, sortOrder, isHidden, createdAt, updatedAt
            ) VALUES (
                'category-live', 'provider-1', 'LIVE_TV', 'remote-live', 'Live', 0, 0, 1, 2
            )
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO channels (
                id, providerId, categoryId, remoteId, channelNumber, name, logoUrl,
                isCatchupAvailable, catchupDays, createdAt, updatedAt
            ) VALUES (
                'channel-1', 'provider-1', 'category-live', 'remote-channel-1', '1',
                'fixture news', NULL, 0, 0, 1, 2
            )
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO movies (
                id, providerId, categoryId, remoteId, name, originalName, containerExtension,
                posterUrl, backdropUrl, rating, year, genre, duration, director, cast, plot,
                trailerUrl, addedAt, createdAt, updatedAt
            ) VALUES (
                'movie-1', 'provider-1', NULL, 'remote-movie-1', 'fixture movie', NULL, 'mp4',
                NULL, NULL, NULL, '2026', NULL, 60000, NULL, NULL, NULL, NULL, 1, 1, 2
            )
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO series (
                id, providerId, categoryId, remoteId, name, originalName, posterUrl, backdropUrl,
                rating, year, genre, director, cast, plot, addedAt, createdAt, updatedAt
            ) VALUES (
                'series-1', 'provider-1', NULL, 'remote-series-1', 'fixture series', NULL,
                NULL, NULL, NULL, '2026', NULL, NULL, NULL, NULL, 1, 1, 2
            )
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO seasons (
                id, providerId, seriesId, seasonNumber, name, posterUrl, createdAt, updatedAt
            ) VALUES (
                'season-1', 'provider-1', 'series-1', 1, 'Staffel 1', NULL, 1, 2
            )
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO episodes (
                id, providerId, seriesId, seasonId, remoteId, episodeNumber, seasonNumber, name,
                plot, thumbnailUrl, containerExtension, duration, airDate, createdAt, updatedAt
            ) VALUES (
                'episode-1', 'provider-1', 'series-1', 'season-1', 'remote-episode-1', 1, 1,
                'fixture episode', NULL, NULL, 'mp4', 60000, NULL, 1, 2
            )
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO epg_sources (
                id, name, urlKey, timeShiftMinutes, isActive, createdAt, updatedAt
            ) VALUES (
                'epg-source-1', 'Fixture EPG', 'epg-url-key-1', 0, 1, 1, 2
            )
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO provider_epg_sources (
                id, providerId, epgSourceId, priority, createdAt
            ) VALUES (
                'provider-epg-1', 'provider-1', 'epg-source-1', 0, 1
            )
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO epg_programs (
                id, providerId, channelId, epgSourceId, externalChannelId, title, subtitle,
                description, startTime, endTime, category, iconUrl, isCatchupAvailable,
                createdAt, updatedAt
            ) VALUES (
                'program-1', 'provider-1', 'channel-1', 'epg-source-1', 'external-channel-1',
                'fixture show', NULL, NULL, 1000, 2000, NULL, NULL, 0, 1, 2
            )
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO favorites (
                id, providerId, mediaType, mediaId, sortOrder, createdAt, updatedAt
            ) VALUES (
                'favorite-1', 'provider-1', 'MOVIE', 'movie-1', 0, 1, 2
            )
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO playback_progress (
                id, providerId, mediaType, mediaId, positionMillis, durationMillis,
                progressPercent, isCompleted, lastWatchedAt, createdAt, updatedAt
            ) VALUES (
                'progress-1', 'provider-1', 'EPISODE', 'episode-1', 10000, 60000,
                16, 0, 3, 1, 2
            )
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO channel_history (
                id, providerId, channelId, watchedAt, durationWatchedMillis, updatedAt
            ) VALUES (
                'history-1', 'provider-1', 'channel-1', 3, 1000, 4
            )
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.stringValue(sql: String): String =
        query(sql).useValue { cursor ->
            cursor.getString(0)
        }

    private fun SupportSQLiteDatabase.longValue(sql: String): Long =
        query(sql).useValue { cursor ->
            cursor.getLong(0)
        }

    private fun <T> Cursor.useValue(block: (Cursor) -> T): T =
        use {
            assertTrue("Query returned no rows", it.moveToFirst())
            block(it)
        }

    private companion object {
        const val TEST_DB = "vivicast-migration-test.db"
    }
}
