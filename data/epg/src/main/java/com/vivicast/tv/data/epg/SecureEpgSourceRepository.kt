package com.vivicast.tv.data.epg

import androidx.room.withTransaction
import com.vivicast.tv.core.database.VivicastDatabase
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

    suspend fun deleteSource(sourceId: String)

    suspend fun linkSourceToProvider(providerId: String, epgSourceId: String, priority: Int)
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
        val urlKey = existing?.urlKey ?: urlKeyFor(sourceId)
        val url = request.url?.trim().orEmpty()

        if (existing == null || url.isNotBlank()) {
            require(url.isNotBlank()) { "EPG URL must not be blank." }
            secureValueStore.write(SecureKey(urlKey), url)
        }

        return delegate.saveEpgSource(
            EpgSourceSaveRequest(
                sourceId = sourceId,
                name = request.name,
                urlKey = urlKey,
                timeShiftMinutes = request.timeShiftMinutes,
                isActive = request.isActive,
            ),
        )
    }

    override suspend fun deleteSource(sourceId: String) {
        val source = database.epgDao().getEpgSource(sourceId)
        database.withTransaction {
            database.epgDao().deleteProviderEpgSourcesForSource(sourceId)
            database.epgDao().deleteMappingsForSource(sourceId)
            database.epgDao().deleteProgramsForSource(sourceId)
            database.epgDao().deleteEpgSource(sourceId)
        }
        source?.urlKey?.let { secureValueStore.delete(SecureKey(it)) }
    }

    override suspend fun linkSourceToProvider(providerId: String, epgSourceId: String, priority: Int) {
        delegate.linkEpgSourceToProvider(providerId, epgSourceId, priority)
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

    private fun urlKeyFor(sourceId: String): String =
        "$EPG_SOURCE_KEY_PREFIX$sourceId:url"
}

private const val EPG_SOURCE_KEY_PREFIX = "epg-source:"
