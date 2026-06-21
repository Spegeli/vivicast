package com.vivicast.tv.data.media

import androidx.room.withTransaction
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.model.CategoryEntity
import com.vivicast.tv.core.database.model.ChannelEntity
import com.vivicast.tv.iptv.m3u.M3uChannel
import com.vivicast.tv.iptv.m3u.M3uPlaylist
import java.security.MessageDigest

class RoomCatalogImportRepository(
    private val database: VivicastDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : CatalogImportRepository {
    private val catalogDao = database.catalogDao()
    private val favoritesDao = database.favoritesDao()
    private val playbackDao = database.playbackDao()
    private val epgDao = database.epgDao()

    override suspend fun importM3uLiveChannels(providerId: String, playlist: M3uPlaylist): CatalogImportResult {
        val now = clock()
        val uniqueChannels = playlist.channels.associateBy { it.remoteId }

        return database.withTransaction {
            val existingCategories = catalogDao.getCategories(providerId = providerId, type = CATEGORY_TYPE_LIVE)
                .associateBy { it.remoteId }
            val existingChannels = catalogDao.getChannels(providerId)
                .associateBy { it.remoteId }

            val importedCategoryRemoteIds = uniqueChannels.values
                .map { it.categoryRemoteId() }
                .toSet()
            val categoryEntities = importedCategoryRemoteIds
                .sortedBy { if (it == UNCATEGORIZED_REMOTE_ID) "" else it.lowercase() }
                .mapIndexed { index, remoteId ->
                    val existing = existingCategories[remoteId]
                    CategoryEntity(
                        id = categoryId(providerId, remoteId),
                        providerId = providerId,
                        type = CATEGORY_TYPE_LIVE,
                        remoteId = remoteId,
                        name = if (remoteId == UNCATEGORIZED_REMOTE_ID) UNCATEGORIZED_DISPLAY_NAME else remoteId,
                        sortOrder = index,
                        isHidden = existing?.isHidden ?: false,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                    )
                }

            val channels = uniqueChannels.values.map { channel ->
                val existing = existingChannels[channel.remoteId]
                channel.toEntity(
                    providerId = providerId,
                    categoryId = categoryId(providerId, channel.categoryRemoteId()),
                    existing = existing,
                    now = now,
                )
            }
            val importedChannelIds = channels.mapTo(mutableSetOf()) { it.id }
            val importedCategoryIds = categoryEntities.mapTo(mutableSetOf()) { it.id }
            val removedChannelIds = existingChannels.values
                .filter { it.id !in importedChannelIds }
                .map { it.id }
            val removedCategoryIds = existingCategories.values
                .filter { it.id !in importedCategoryIds }
                .map { it.id }

            val categoriesAdded = categoryEntities.count { it.remoteId !in existingCategories }
            val categoriesUpdated = categoryEntities.size - categoriesAdded
            val channelsAdded = channels.count { it.remoteId !in existingChannels }
            val channelsUpdated = channels.count { channel ->
                val existing = existingChannels[channel.remoteId]
                existing != null && existing.copy(updatedAt = channel.updatedAt) != channel
            }

            if (categoryEntities.isNotEmpty()) {
                catalogDao.upsertCategories(categoryEntities)
            }
            if (channels.isNotEmpty()) {
                catalogDao.upsertChannels(channels)
            }
            if (removedChannelIds.isNotEmpty()) {
                favoritesDao.deleteFavoritesByMediaIds(providerId, MEDIA_TYPE_CHANNEL, removedChannelIds)
                playbackDao.deleteProgressForMediaIds(providerId, MEDIA_TYPE_CHANNEL, removedChannelIds)
                playbackDao.deleteHistoryForChannels(providerId, removedChannelIds)
                epgDao.deleteMappingsForChannels(providerId, removedChannelIds)
                epgDao.deleteProgramsForChannels(providerId, removedChannelIds)
                catalogDao.deleteChannels(providerId, removedChannelIds)
            }
            if (removedCategoryIds.isNotEmpty()) {
                catalogDao.deleteCategories(providerId, CATEGORY_TYPE_LIVE, removedCategoryIds)
            }

            CatalogImportResult(
                categoriesAdded = categoriesAdded,
                categoriesUpdated = categoriesUpdated,
                categoriesRemoved = removedCategoryIds.size,
                channelsAdded = channelsAdded,
                channelsUpdated = channelsUpdated,
                channelsRemoved = removedChannelIds.size,
                skippedEntries = playlist.skippedEntries,
            )
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
            remoteId = remoteId,
            channelNumber = channelNumber,
            name = name,
            logoUrl = logoUrl,
            isCatchupAvailable = isCatchupAvailable,
            catchupDays = catchupDays,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )

    private fun M3uChannel.categoryRemoteId(): String =
        categoryName?.takeIf { it.isNotBlank() } ?: UNCATEGORIZED_REMOTE_ID

    private companion object {
        const val CATEGORY_TYPE_LIVE = "LIVE"
        const val MEDIA_TYPE_CHANNEL = "CHANNEL"
        const val UNCATEGORIZED_REMOTE_ID = "__UNCATEGORIZED__"
        const val UNCATEGORIZED_DISPLAY_NAME = "Nicht kategorisiert"

        fun categoryId(providerId: String, remoteId: String): String =
            "$providerId:category:live:${stableHash(remoteId)}"

        fun channelId(providerId: String, remoteId: String): String =
            "$providerId:channel:${stableHash(remoteId)}"

        fun stableHash(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.take(16).joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
    }
}
