package com.vivicast.tv.data.epg

import androidx.room.withTransaction
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.model.ChannelEntity
import com.vivicast.tv.core.database.model.EpgChannelMappingEntity
import com.vivicast.tv.core.database.model.EpgProgramEntity
import com.vivicast.tv.core.database.model.EpgSourceEntity
import com.vivicast.tv.core.database.model.ProviderEpgSourceEntity
import com.vivicast.tv.domain.model.EpgChannelMapping
import com.vivicast.tv.domain.model.EpgProgram
import com.vivicast.tv.domain.model.EpgSource
import com.vivicast.tv.domain.model.ProviderEpgSource
import com.vivicast.tv.iptv.xmltv.XmltvChannel
import com.vivicast.tv.iptv.xmltv.XmltvDocument
import com.vivicast.tv.iptv.xmltv.XmltvProgram
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

class RoomEpgRepository(
    private val database: VivicastDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : EpgRepository, EpgImportRepository {
    private val catalogDao = database.catalogDao()
    private val epgDao = database.epgDao()

    override fun observeEpgSources(): Flow<List<EpgSource>> =
        epgDao.observeEpgSources().map { sources -> sources.map { it.toDomain() } }

    override fun observeProviderEpgSources(providerId: String): Flow<List<ProviderEpgSource>> =
        epgDao.observeProviderEpgSources(providerId).map { sources -> sources.map { it.toDomain() } }

    override fun observeProgramsForChannel(
        providerId: String,
        channelId: String,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<EpgProgram>> =
        epgDao.observeProgramsForChannel(providerId, channelId, fromMillis, toMillis)
            .map { programs -> programs.map { it.toDomain() } }

    override fun observeMappingsForChannel(providerId: String, channelId: String): Flow<List<EpgChannelMapping>> =
        epgDao.observeMappingsForChannel(providerId, channelId).map { mappings -> mappings.map { it.toDomain() } }

    override suspend fun setManualChannelMapping(request: ManualEpgChannelMappingRequest): EpgChannelMapping {
        val providerId = request.providerId.trim()
        val channelId = request.channelId.trim()
        val epgSourceId = request.epgSourceId.trim()
        val epgChannelId = request.epgChannelId.trim()
        require(providerId.isNotBlank()) { "Provider ID must not be blank." }
        require(channelId.isNotBlank()) { "Channel ID must not be blank." }
        require(epgSourceId.isNotBlank()) { "EPG source ID must not be blank." }
        require(epgChannelId.isNotBlank()) { "EPG channel ID must not be blank." }

        val now = clock()
        val mapping = database.withTransaction {
            require(epgDao.getEpgSource(epgSourceId) != null) { "EPG source not found: $epgSourceId" }
            require(catalogDao.getChannels(providerId).any { it.id == channelId }) {
                "Channel not found for provider: $channelId"
            }

            val existing = epgDao.getMappingForChannelSource(
                providerId = providerId,
                channelId = channelId,
                epgSourceId = epgSourceId,
            )
            val manualMapping = EpgChannelMappingEntity(
                id = existing?.id ?: epgMappingId(providerId, epgSourceId, channelId),
                providerId = providerId,
                channelId = channelId,
                epgSourceId = epgSourceId,
                epgChannelId = epgChannelId,
                isManual = true,
                createdAt = existing?.createdAt ?: now,
            )
            epgDao.upsertMappings(listOf(manualMapping))
            epgDao.deleteProgramsForChannelAndSource(providerId, channelId, epgSourceId)
            manualMapping
        }
        return mapping.toDomain()
    }

    override suspend fun clearManualChannelMapping(providerId: String, channelId: String, epgSourceId: String) {
        val normalizedProviderId = providerId.trim()
        val normalizedChannelId = channelId.trim()
        val normalizedEpgSourceId = epgSourceId.trim()
        require(normalizedProviderId.isNotBlank()) { "Provider ID must not be blank." }
        require(normalizedChannelId.isNotBlank()) { "Channel ID must not be blank." }
        require(normalizedEpgSourceId.isNotBlank()) { "EPG source ID must not be blank." }

        database.withTransaction {
            val existing = epgDao.getMappingForChannelSource(
                providerId = normalizedProviderId,
                channelId = normalizedChannelId,
                epgSourceId = normalizedEpgSourceId,
            )
            if (existing?.isManual != true) return@withTransaction

            epgDao.deleteManualMappingForChannelSource(
                providerId = normalizedProviderId,
                channelId = normalizedChannelId,
                epgSourceId = normalizedEpgSourceId,
            )
            epgDao.deleteProgramsForChannelAndSource(
                providerId = normalizedProviderId,
                channelId = normalizedChannelId,
                epgSourceId = normalizedEpgSourceId,
            )
        }
    }

    override suspend fun saveEpgSource(request: EpgSourceSaveRequest): EpgSource {
        val name = request.name.trim()
        val urlKey = request.urlKey.trim()
        require(name.isNotBlank()) { "EPG source name must not be blank." }
        require(urlKey.isNotBlank()) { "EPG source URL key must not be blank." }

        val now = clock()
        val existing = request.sourceId?.let { epgDao.getEpgSource(it) }
        val source = EpgSourceEntity(
            id = existing?.id ?: request.sourceId ?: UUID.randomUUID().toString(),
            name = name,
            urlKey = urlKey,
            timeShiftMinutes = request.timeShiftMinutes,
            isActive = request.isActive,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        epgDao.upsertEpgSources(listOf(source))
        return source.toDomain()
    }

    override suspend fun linkEpgSourceToProvider(providerId: String, epgSourceId: String, priority: Int) {
        require(providerId.isNotBlank()) { "Provider ID must not be blank." }
        require(epgSourceId.isNotBlank()) { "EPG source ID must not be blank." }
        require(priority > 0) { "EPG source priority must be positive." }

        val existing = epgDao.getProviderEpgSources(providerId)
            .firstOrNull { it.epgSourceId == epgSourceId }
        val link = ProviderEpgSourceEntity(
            id = existing?.id ?: providerEpgSourceId(providerId, epgSourceId),
            providerId = providerId,
            epgSourceId = epgSourceId,
            priority = priority,
            createdAt = existing?.createdAt ?: clock(),
        )
        epgDao.upsertProviderEpgSources(listOf(link))
    }

    override suspend fun importXmltv(
        providerId: String,
        epgSourceId: String,
        document: XmltvDocument,
    ): EpgImportResult {
        val now = clock()
        return database.withTransaction {
            val source = epgDao.getEpgSource(epgSourceId)
                ?: error("EPG source not found: $epgSourceId")
            val timeShiftMillis = source.timeShiftMinutes * MILLIS_PER_MINUTE
            val channels = catalogDao.getChannels(providerId)
            val existingMappings = epgDao.getMappingsForProviderAndSource(providerId, epgSourceId)
            val manualMappings = existingMappings.filter { it.isManual }
            val manualMappedChannelIds = manualMappings.mapTo(mutableSetOf()) { it.channelId }

            val autoMappings = buildAutomaticMappings(
                providerId = providerId,
                epgSourceId = epgSourceId,
                channels = channels,
                xmltvChannels = document.channels,
                existingMappings = existingMappings,
                ignoredChannelIds = manualMappedChannelIds,
                now = now,
            )

            if (autoMappings.isNotEmpty()) {
                epgDao.upsertMappings(autoMappings)
            }

            val channelById = channels.associateBy { it.id }
            val externalChannelToLocal = (manualMappings + autoMappings)
                .associateBy { it.epgChannelId }
                .mapValues { (_, mapping) -> mapping.channelId }
            val programs = document.programs.mapNotNull { program ->
                val channelId = externalChannelToLocal[program.channelId] ?: return@mapNotNull null
                val channel = channelById[channelId] ?: return@mapNotNull null
                program.toEntity(
                    providerId = providerId,
                    epgSourceId = epgSourceId,
                    localChannel = channel,
                    now = now,
                    timeShiftMillis = timeShiftMillis,
                )
            }

            epgDao.deleteProgramsForProviderAndSource(providerId, epgSourceId)
            if (programs.isNotEmpty()) {
                epgDao.upsertPrograms(programs)
            }

            EpgImportResult(
                programsImported = programs.size,
                programsSkipped = document.skippedPrograms + (document.programs.size - programs.size),
                mappingsAdded = autoMappings.count { mapping ->
                    existingMappings.none { it.channelId == mapping.channelId && it.epgSourceId == mapping.epgSourceId }
                },
                mappingsUpdated = autoMappings.count { mapping ->
                    val existing = existingMappings.firstOrNull {
                        it.channelId == mapping.channelId && it.epgSourceId == mapping.epgSourceId
                    }
                    existing != null && existing.epgChannelId != mapping.epgChannelId
                },
            )
        }
    }

    private fun buildAutomaticMappings(
        providerId: String,
        epgSourceId: String,
        channels: List<ChannelEntity>,
        xmltvChannels: List<XmltvChannel>,
        existingMappings: List<EpgChannelMappingEntity>,
        ignoredChannelIds: Set<String>,
        now: Long,
    ): List<EpgChannelMappingEntity> {
        val xmlById = xmltvChannels.associateBy { it.id.normalize() }
        val xmlByName = xmltvChannels.flatMap { xmlChannel ->
            xmlChannel.displayNames.map { displayName -> displayName.normalize() to xmlChannel }
        }.toMap()

        return channels.mapNotNull { channel ->
            if (channel.id in ignoredChannelIds) return@mapNotNull null
            val match = xmlById[channel.remoteId.normalize()]
                ?: xmlByName[channel.name.normalize()]
                ?: return@mapNotNull null
            val existing = existingMappings.firstOrNull { it.channelId == channel.id && !it.isManual }
            EpgChannelMappingEntity(
                id = existing?.id ?: epgMappingId(providerId, epgSourceId, channel.id),
                providerId = providerId,
                channelId = channel.id,
                epgSourceId = epgSourceId,
                epgChannelId = match.id,
                isManual = false,
                createdAt = existing?.createdAt ?: now,
            )
        }
    }

    private fun XmltvProgram.toEntity(
        providerId: String,
        epgSourceId: String,
        localChannel: ChannelEntity,
        now: Long,
        timeShiftMillis: Long,
    ): EpgProgramEntity {
        val shiftedStart = startTimeMillis + timeShiftMillis
        val shiftedEnd = endTimeMillis + timeShiftMillis
        return EpgProgramEntity(
            id = epgProgramId(providerId, epgSourceId, channelId, shiftedStart, shiftedEnd, title),
            providerId = providerId,
            channelId = localChannel.id,
            epgSourceId = epgSourceId,
            externalChannelId = channelId,
            title = title,
            subtitle = subtitle,
            description = description,
            startTime = shiftedStart,
            endTime = shiftedEnd,
            category = category,
            iconUrl = iconUrl,
            isCatchupAvailable = localChannel.isCatchupAvailable,
            createdAt = now,
            updatedAt = now,
        )
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L

        fun providerEpgSourceId(providerId: String, epgSourceId: String): String =
            "$providerId:provider-epg-source:${stableHash(epgSourceId)}"

        fun epgMappingId(providerId: String, epgSourceId: String, channelId: String): String =
            "$providerId:epg-map:${stableHash("$epgSourceId:$channelId")}"

        fun epgProgramId(
            providerId: String,
            epgSourceId: String,
            externalChannelId: String,
            startTime: Long,
            endTime: Long,
            title: String,
        ): String =
            "$providerId:epg-program:${stableHash("$epgSourceId:$externalChannelId:$startTime:$endTime:$title")}"

        fun String.normalize(): String =
            trim().lowercase(Locale.ROOT)

        fun stableHash(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.take(16).joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
    }
}

private fun EpgSourceEntity.toDomain(): EpgSource =
    EpgSource(
        id = id,
        name = name,
        urlKey = urlKey,
        timeShiftMinutes = timeShiftMinutes,
        isActive = isActive,
    )

private fun ProviderEpgSourceEntity.toDomain(): ProviderEpgSource =
    ProviderEpgSource(
        id = id,
        providerId = providerId,
        epgSourceId = epgSourceId,
        priority = priority,
    )

private fun EpgProgramEntity.toDomain(): EpgProgram =
    EpgProgram(
        id = id,
        providerId = providerId,
        channelId = channelId,
        epgSourceId = epgSourceId,
        externalChannelId = externalChannelId,
        title = title,
        subtitle = subtitle,
        description = description,
        startTime = startTime,
        endTime = endTime,
        category = category,
        iconUrl = iconUrl,
        isCatchupAvailable = isCatchupAvailable,
    )

private fun EpgChannelMappingEntity.toDomain(): EpgChannelMapping =
    EpgChannelMapping(
        id = id,
        providerId = providerId,
        channelId = channelId,
        epgSourceId = epgSourceId,
        epgChannelId = epgChannelId,
        isManual = isManual,
    )
