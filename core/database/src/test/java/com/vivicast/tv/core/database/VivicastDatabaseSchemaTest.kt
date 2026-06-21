package com.vivicast.tv.core.database

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
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VivicastDatabaseSchemaTest {
    @Test
    fun exportedSchemaContainsPrdPersistenceTables() {
        val schema = File("schemas/com.vivicast.tv.core.database.VivicastDatabase/1.json").readText()
        val expectedEntities = setOf(
            "providers",
            "categories",
            "channels",
            "movies",
            "series",
            "seasons",
            "episodes",
            "epg_sources",
            "provider_epg_sources",
            "epg_programs",
            "epg_channel_mappings",
            "favorites",
            "playback_progress",
            "channel_history",
        )

        expectedEntities.forEach { tableName ->
            assertTrue("Missing table $tableName", schema.contains("\"tableName\": \"$tableName\""))
        }
    }

    @Test
    fun providerEntityStoresCredentialKeyInsteadOfSecrets() {
        val fieldNames = ProviderEntity::class.java.declaredFields.map { it.name }.toSet()

        assertTrue(fieldNames.contains("credentialsKey"))
        assertFalse(fieldNames.contains("serverUrl"))
        assertFalse(fieldNames.contains("m3uUrl"))
        assertFalse(fieldNames.contains("username"))
        assertFalse(fieldNames.contains("password"))
    }

    @Test
    fun epgSourceEntityStoresUrlKeyInsteadOfRawUrl() {
        val fieldNames = EpgSourceEntity::class.java.declaredFields.map { it.name }.toSet()

        assertTrue(fieldNames.contains("urlKey"))
        assertFalse(fieldNames.contains("url"))
    }

    @Test
    fun exportedSchemaContainsProviderIsolationIndexes() {
        val schema = File("schemas/com.vivicast.tv.core.database.VivicastDatabase/1.json").readText()

        val requiredIndexes = setOf(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_channels_providerId_remoteId`",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_movies_providerId_remoteId`",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_series_providerId_remoteId`",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_favorites_providerId_mediaType_mediaId`",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_playback_progress_providerId_mediaType_mediaId`",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_channel_history_providerId_channelId`",
        )

        requiredIndexes.forEach { indexSql ->
            assertTrue("Missing index $indexSql", schema.contains(indexSql))
        }
    }
}
