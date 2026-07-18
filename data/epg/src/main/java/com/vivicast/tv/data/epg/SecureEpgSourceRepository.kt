package com.vivicast.tv.data.epg

import androidx.room.withTransaction
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.model.ProviderEpgSourceEntity
import com.vivicast.tv.core.security.SecureKey
import com.vivicast.tv.core.security.SecureValueStore
import com.vivicast.tv.domain.model.EpgChannelMapping
import com.vivicast.tv.domain.model.EpgProgram
import com.vivicast.tv.domain.model.EpgSource
import com.vivicast.tv.domain.model.ProviderEpgSource
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface EpgSourceRepository : EpgRepository {
    suspend fun saveSource(request: EpgSourceEditRequest): EpgSource

    /** The (securely stored) URL of an existing source, for duplicate detection in the editor. */
    suspend fun getSourceUrl(sourceId: String): String?

    suspend fun deleteSource(sourceId: String)

    suspend fun linkSourceToProvider(providerId: String, epgSourceId: String, priority: Int)

    suspend fun unlinkSourceFromProvider(providerId: String, epgSourceId: String)

    /** Renumber [providerId]'s linked EPG sources to match [orderedSourceIds] (index 0 = priority 1). */
    suspend fun reorderProviderEpgSources(providerId: String, orderedSourceIds: List<String>)
}

data class EpgSourceEditRequest(
    val sourceId: String? = null,
    val name: String,
    val url: String? = null,
    val timeShiftMinutes: Int = 0,
    val isActive: Boolean = true,
)

class SecureEpgSourceRepository(
    private val database: VivicastDatabase,
    private val secureValueStore: SecureValueStore,
    private val delegate: RoomEpgRepository = RoomEpgRepository(database),
) : EpgSourceRepository, EpgRepository by delegate {
    override suspend fun saveSource(request: EpgSourceEditRequest): EpgSource {
        val sourceId = request.sourceId ?: UUID.randomUUID().toString()
        val existing = request.sourceId?.let { database.epgDao().getEpgSource(it) }
        val sourceConfigKey = existing?.sourceConfigKey ?: sourceConfigKeyFor(sourceId)
        val url = request.url?.trim().orEmpty()

        if (existing == null || url.isNotBlank()) {
            require(url.isNotBlank()) { "EPG URL must not be blank." }
            secureValueStore.write(SecureKey(sourceConfigKey), url)
        }

        return delegate.saveEpgSource(
            EpgSourceSaveRequest(
                sourceId = sourceId,
                name = request.name,
                sourceConfigKey = sourceConfigKey,
                timeShiftMinutes = request.timeShiftMinutes,
                isActive = request.isActive,
            ),
        )
    }

    override suspend fun getSourceUrl(sourceId: String): String? {
        val source = database.epgDao().getEpgSource(sourceId) ?: return null
        return secureValueStore.read(SecureKey(source.sourceConfigKey))
    }

    override suspend fun deleteSource(sourceId: String) {
        val source = database.epgDao().getEpgSource(sourceId)
        database.withTransaction {
            database.epgDao().deleteProviderEpgSourcesForSource(sourceId)
            database.epgDao().deleteMappingsForSource(sourceId)
            database.epgDao().deleteProgramsForSource(sourceId)
            // Stage hygiene: drop any half-staged rows for this source (delete racing a mid-stage refresh).
            database.epgDao().clearProgramsStageForSource(sourceId)
            database.epgDao().deleteEpgSource(sourceId)
        }
        source?.sourceConfigKey?.let { secureValueStore.delete(SecureKey(it)) }
    }

    override suspend fun linkSourceToProvider(providerId: String, epgSourceId: String, priority: Int) {
        delegate.linkEpgSourceToProvider(providerId, epgSourceId, priority)
    }

    override suspend fun unlinkSourceFromProvider(providerId: String, epgSourceId: String) {
        require(providerId.isNotBlank()) { "Provider ID must not be blank." }
        require(epgSourceId.isNotBlank()) { "EPG source ID must not be blank." }

        database.withTransaction {
            val links = database.epgDao().getProviderEpgSources(providerId)
            val remaining = links.filterNot { it.epgSourceId == epgSourceId }
            if (remaining.size == links.size) return@withTransaction

            database.epgDao().deleteProviderEpgSource(providerId, epgSourceId)
            // F1: drop this (provider, source) pair's mappings + programmes so nothing stale lingers or
            // resurfaces on re-link. Scoped to the pair — other providers sharing the source keep theirs.
            database.epgDao().deleteMappingsForProviderAndSource(providerId, epgSourceId)
            database.epgDao().deleteProgramsForProviderAndSource(providerId, epgSourceId)
            rewritePriorities(remaining)
        }
    }

    override suspend fun reorderProviderEpgSources(providerId: String, orderedSourceIds: List<String>) {
        require(providerId.isNotBlank()) { "Provider ID must not be blank." }

        database.withTransaction {
            val links = database.epgDao().getProviderEpgSources(providerId)
            if (links.size < 2) return@withTransaction

            // Apply the requested order; any linked source not named in the request keeps its current
            // relative order, appended after the named ones (defensive against a stale/partial UI list).
            val byId = links.associateBy { it.epgSourceId }
            val reordered = orderedSourceIds.mapNotNull { byId[it] } +
                links.filterNot { it.epgSourceId in orderedSourceIds }
            rewritePriorities(reordered)
        }
    }

    override fun observeProviderEpgSources(providerId: String): Flow<List<ProviderEpgSource>> =
        delegate.observeProviderEpgSources(providerId)

    override fun observeProgramsForChannel(
        providerId: String,
        channelId: String,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<EpgProgram>> =
        delegate.observeProgramsForChannel(providerId, channelId, fromMillis, toMillis)

    override fun observeMappingsForChannel(providerId: String, channelId: String): Flow<List<EpgChannelMapping>> =
        delegate.observeMappingsForChannel(providerId, channelId)

    private fun sourceConfigKeyFor(sourceId: String): String =
        "$EPG_SOURCE_KEY_PREFIX$sourceId:url"

    private suspend fun rewritePriorities(links: List<ProviderEpgSourceEntity>) {
        if (links.isEmpty()) return

        val temporaryLinks = links.mapIndexed { index, link ->
            link.copy(priority = TEMPORARY_PRIORITY_OFFSET + index)
        }
        database.epgDao().upsertProviderEpgSources(temporaryLinks)

        val normalizedLinks = links.mapIndexed { index, link ->
            link.copy(priority = index + 1)
        }
        database.epgDao().upsertProviderEpgSources(normalizedLinks)
    }
}

private const val EPG_SOURCE_KEY_PREFIX = "epg-source:"
private const val TEMPORARY_PRIORITY_OFFSET = 10_000
