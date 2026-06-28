package com.vivicast.tv.core.database

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
import com.vivicast.tv.core.database.model.ProviderEntity
import com.vivicast.tv.core.database.model.ProviderEpgSourceEntity
import com.vivicast.tv.core.database.model.AndroidTvSearchEntryEntity
import com.vivicast.tv.core.database.model.SearchHistoryEntity
import com.vivicast.tv.core.database.model.SeasonEntity
import com.vivicast.tv.core.database.model.SeriesEntity
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VivicastDatabaseSchemaTest {
    @Test
    fun exportedSchemaContainsPrdPersistenceTables() {
        val schema = exportedSchema().readText()
        val expectedEntities = setOf(
            "providers",
            "categories",
            "channels",
            "movies",
            "series",
            "seasons",
            "episodes",
            "epg_sources",
            "epg_channels",
            "provider_epg_sources",
            "epg_programs",
            "epg_channel_mappings",
            "favorites",
            "playback_progress",
            "channel_history",
            "search_history",
            "search_channels_fts",
            "search_movies_fts",
            "search_series_fts",
            "search_epg_fts",
            "android_tv_search_entries",
        )

        expectedEntities.forEach { tableName ->
            assertTrue("Missing table $tableName", schema.contains("\"tableName\": \"$tableName\""))
        }
    }

    @Test
    fun providerEntityStoresStableCredentialReferenceInsteadOfSecrets() {
        val fieldNames = ProviderEntity::class.java.declaredFields.map { it.name }.toSet()

        assertTrue(fieldNames.contains("stableKey"))
        assertTrue(fieldNames.contains("sourceConfigKey"))
        assertFalse(fieldNames.contains("credentialsKey"))
        assertFalse(fieldNames.contains("serverUrl"))
        assertFalse(fieldNames.contains("m3uUrl"))
        assertFalse(fieldNames.contains("username"))
        assertFalse(fieldNames.contains("password"))
    }

    @Test
    fun epgSourceEntityStoresSourceConfigKeyInsteadOfRawUrl() {
        val fieldNames = EpgSourceEntity::class.java.declaredFields.map { it.name }.toSet()

        assertTrue(fieldNames.contains("stableKey"))
        assertTrue(fieldNames.contains("sourceConfigKey"))
        assertFalse(fieldNames.contains("urlKey"))
        assertTrue(fieldNames.contains("lastRefreshAt"))
        assertTrue(fieldNames.contains("lastProgramCount"))
        assertFalse(fieldNames.contains("url"))
    }

    @Test
    fun stableIdentityAndPendingRestoreFieldsExist() {
        assertTrue(CategoryEntity::class.java.fieldNames().contains("stableKey"))
        assertTrue(ChannelEntity::class.java.fieldNames().contains("stableKey"))
        assertTrue(MovieEntity::class.java.fieldNames().contains("stableKey"))
        assertTrue(SeriesEntity::class.java.fieldNames().contains("stableKey"))
        assertTrue(SeasonEntity::class.java.fieldNames().contains("stableKey"))
        assertTrue(EpisodeEntity::class.java.fieldNames().contains("stableKey"))
        assertTrue(EpgProgramEntity::class.java.fieldNames().contains("stableKey"))
        assertTrue(EpgChannelEntity::class.java.fieldNames().contains("stableKey"))
        assertTrue(SearchHistoryEntity::class.java.fieldNames().contains("query"))
        assertTrue(SearchHistoryEntity::class.java.fieldNames().contains("normalizedQuery"))
        assertTrue(SearchHistoryEntity::class.java.fieldNames().contains("lastUsedAt"))
        assertTrue(AndroidTvSearchEntryEntity::class.java.fieldNames().contains("providerStableKey"))
        assertTrue(AndroidTvSearchEntryEntity::class.java.fieldNames().contains("mediaStableKey"))
        assertTrue(AndroidTvSearchEntryEntity::class.java.fieldNames().contains("deepLink"))

        assertTrue(FavoriteEntity::class.java.fieldNames().contains("mediaStableKey"))
        assertTrue(FavoriteEntity::class.java.fieldNames().contains("isPending"))
        assertTrue(PlaybackProgressEntity::class.java.fieldNames().contains("mediaStableKey"))
        assertTrue(PlaybackProgressEntity::class.java.fieldNames().contains("isPending"))
        assertTrue(ChannelHistoryEntity::class.java.fieldNames().contains("channelStableKey"))
        assertTrue(ChannelHistoryEntity::class.java.fieldNames().contains("isPending"))
        assertTrue(EpgChannelMappingEntity::class.java.fieldNames().contains("channelStableKey"))
        assertTrue(EpgChannelMappingEntity::class.java.fieldNames().contains("epgSourceStableKey"))
        assertTrue(EpgChannelMappingEntity::class.java.fieldNames().contains("epgChannelStableKey"))
    }

    @Test
    fun exportedSchemaContainsProviderIsolationIndexes() {
        val schema = exportedSchema().readText()

        val requiredIndexes = setOf(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_channels_providerId_remoteId`",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_channels_providerId_stableKey`",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_movies_providerId_remoteId`",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_movies_providerId_stableKey`",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_series_providerId_remoteId`",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_series_providerId_stableKey`",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_favorites_providerId_mediaType_mediaId`",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_playback_progress_providerId_mediaType_mediaId`",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_channel_history_providerId_channelId`",
        )

        requiredIndexes.forEach { indexSql ->
            assertTrue("Missing index $indexSql", schema.contains(indexSql))
        }
    }
}

private fun Class<*>.fieldNames(): Set<String> =
    declaredFields.map { it.name }.toSet()

private fun exportedSchema(): File =
    File("schemas/com.vivicast.tv.core.database.VivicastDatabase/$VIVICAST_DATABASE_VERSION.json")
