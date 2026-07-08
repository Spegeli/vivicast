package com.vivicast.tv.data.epg

import androidx.room.withTransaction
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.model.ChannelEntity
import com.vivicast.tv.core.database.model.EpgChannelMappingEntity
import com.vivicast.tv.core.database.model.EpgProgramEntity
import com.vivicast.tv.core.database.model.EpgSourceEntity
import com.vivicast.tv.core.database.model.ProviderEpgSourceEntity
import com.vivicast.tv.domain.model.Channel
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

    override fun observeChannelsForProvider(providerId: String): Flow<List<Channel>> =
        catalogDao.observeChannels(providerId, categoryId = null).map { channels -> channels.map { it.toDomain() } }

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
            val source = epgDao.getEpgSource(epgSourceId) ?: error("EPG source not found: $epgSourceId")
            val channel = catalogDao.getChannels(providerId).firstOrNull { it.id == channelId }
            require(channel != null) {
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
                channelStableKey = channel.stableKey,
                epgSourceId = epgSourceId,
                epgSourceStableKey = source.stableKey,
                epgChannelId = epgChannelId,
                epgChannelStableKey = epgChannelStableKey(epgChannelId),
                isManual = true,
                confidence = 1f,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
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
        val sourceConfigKey = request.sourceConfigKey.trim()
        require(name.isNotBlank()) { "EPG source name must not be blank." }
        require(sourceConfigKey.isNotBlank()) { "EPG source URL key must not be blank." }

        val now = clock()
        val existing = request.sourceId?.let { epgDao.getEpgSource(it) }
        val sourceId = existing?.id ?: request.sourceId ?: UUID.randomUUID().toString()
        // Repo-level uniqueness backstop (the editor already checks, but a race / non-editor caller must
        // not persist duplicate names — mirrors RoomProviderRepository).
        require(epgDao.getEpgSources().none { it.id != sourceId && it.name.trim().equals(name, ignoreCase = true) }) {
            "EPG source name must be unique."
        }
        val source = EpgSourceEntity(
            id = sourceId,
            stableKey = existing?.stableKey ?: sourceId,
            name = name,
            sourceConfigKey = sourceConfigKey,
            timeShiftMinutes = request.timeShiftMinutes,
            isActive = request.isActive,
            refreshIntervalHours = request.refreshIntervalHours,
            // Preserve refresh metadata across edits — otherwise editing (e.g. disabling) a source would
            // wipe its last-refresh timestamp and channel/programme counts back to defaults.
            lastRefreshAt = existing?.lastRefreshAt,
            lastProgramCount = existing?.lastProgramCount ?: 0,
            lastChannelCount = existing?.lastChannelCount ?: 0,
            isRefreshing = existing?.isRefreshing ?: false,
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
                epgSourceStableKey = source.stableKey,
                now = now,
            )

            if (autoMappings.isNotEmpty()) {
                epgDao.upsertMappings(autoMappings)
            }

            val channelById = channels.associateBy { it.id }
            val sourceStableKey = source.stableKey
            val externalChannelToLocal = (manualMappings + autoMappings)
                .associateBy { it.epgChannelId }
                .mapValues { (_, mapping) -> mapping.channelId }
            epgDao.deleteProgramsForProviderAndSource(providerId, epgSourceId)
            var importedPrograms = 0
            var skippedUnmappedPrograms = 0
            val programBuffer = ArrayList<EpgProgramEntity>(EPG_IMPORT_CHUNK_SIZE)
            suspend fun flushPrograms() {
                if (programBuffer.isEmpty()) return
                epgDao.insertPrograms(programBuffer)
                importedPrograms += programBuffer.size
                programBuffer.clear()
            }

            document.programs.forEach { program ->
                val channelId = externalChannelToLocal[program.channelId]
                val channel = channelId?.let(channelById::get)
                if (channel == null) {
                    skippedUnmappedPrograms += 1
                } else {
                    programBuffer += program.toEntity(
                        providerId = providerId,
                        epgSourceId = epgSourceId,
                        localChannel = channel,
                        sourceStableKey = sourceStableKey,
                        now = now,
                        timeShiftMillis = timeShiftMillis,
                    )
                }
                if (programBuffer.size >= EPG_IMPORT_CHUNK_SIZE) {
                    flushPrograms()
                }
            }
            flushPrograms()

            EpgImportResult(
                programsImported = importedPrograms,
                programsSkipped = document.skippedPrograms + skippedUnmappedPrograms,
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

    override suspend fun cleanupProgramsOutsideRetention(
        nowMillis: Long,
        pastDays: Int,
        futureDays: Int,
    ): Int {
        val fromMillis = nowMillis - pastDays.coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS).toLong() * MILLIS_PER_DAY
        val toMillis = nowMillis + futureDays.coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS).toLong() * MILLIS_PER_DAY
        return epgDao.deleteProgramsOutsideWindow(fromMillis, toMillis)
    }

    override suspend fun markEpgSourceRefreshed(
        sourceId: String,
        refreshedAt: Long,
        channelCount: Int,
        programCount: Int,
    ) {
        epgDao.markEpgSourceRefreshed(sourceId, refreshedAt, channelCount, programCount)
    }

    override suspend fun setEpgSourceRefreshing(sourceId: String, refreshing: Boolean) {
        epgDao.setEpgSourceRefreshing(sourceId, refreshing)
    }

    private fun buildAutomaticMappings(
        providerId: String,
        epgSourceId: String,
        channels: List<ChannelEntity>,
        xmltvChannels: List<XmltvChannel>,
        existingMappings: List<EpgChannelMappingEntity>,
        ignoredChannelIds: Set<String>,
        epgSourceStableKey: String,
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
                channelStableKey = channel.stableKey,
                epgSourceId = epgSourceId,
                epgSourceStableKey = epgSourceStableKey,
                epgChannelId = match.id,
                epgChannelStableKey = epgChannelStableKey(match.id),
                isManual = false,
                confidence = 0.8f,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        }
    }

    private fun XmltvProgram.toEntity(
        providerId: String,
        epgSourceId: String,
        localChannel: ChannelEntity,
        sourceStableKey: String,
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
            stableKey = epgProgramStableKey(sourceStableKey, channelId, shiftedStart, shiftedEnd, title),
            epgChannelId = channelId,
            title = title,
            normalizedTitle = title.normalize(),
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
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
        const val MIN_RETENTION_DAYS = 1
        const val MAX_RETENTION_DAYS = 14
        const val EPG_IMPORT_CHUNK_SIZE = 20_000
        val SHA256: ThreadLocal<MessageDigest> = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }

        fun providerEpgSourceId(providerId: String, epgSourceId: String): String =
            "$providerId:provider-epg-source:${stableHash(epgSourceId)}"

        fun epgMappingId(providerId: String, epgSourceId: String, channelId: String): String =
            "$providerId:epg-map:${stableHash("$epgSourceId:$channelId")}"

        fun epgProgramId(
            providerId: String,
            epgSourceId: String,
            epgChannelId: String,
            startTime: Long,
            endTime: Long,
            title: String,
        ): String =
            "$providerId:epg-program:${stableHash("$epgSourceId:$epgChannelId:$startTime:$endTime:$title")}"

        fun epgProgramStableKey(
            epgSourceStableKey: String,
            epgChannelId: String,
            startTime: Long,
            endTime: Long,
            title: String,
        ): String =
            stableHash("$epgSourceStableKey:$epgChannelId:$startTime:$endTime:$title")

        fun epgChannelStableKey(epgChannelId: String): String =
            stableHash(epgChannelId)

        fun String.normalize(): String =
            trim().lowercase(Locale.ROOT)

        fun stableHash(value: String): String {
            val messageDigest = requireNotNull(SHA256.get()).apply { reset() }
            val digest = messageDigest.digest(value.toByteArray(Charsets.UTF_8))
            return digest.take(16).joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
    }
}

private fun EpgSourceEntity.toDomain(): EpgSource =
    EpgSource(
        id = id,
        stableKey = stableKey,
        name = name,
        sourceConfigKey = sourceConfigKey,
        timeShiftMinutes = timeShiftMinutes,
        isActive = isActive,
        refreshIntervalHours = refreshIntervalHours,
        lastRefreshAt = lastRefreshAt,
        lastProgramCount = lastProgramCount,
        lastChannelCount = lastChannelCount,
        isRefreshing = isRefreshing,
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
        stableKey = stableKey,
        epgChannelId = epgChannelId,
        title = title,
        normalizedTitle = normalizedTitle,
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
        channelStableKey = channelStableKey,
        epgSourceId = epgSourceId,
        epgSourceStableKey = epgSourceStableKey,
        epgChannelId = epgChannelId,
        epgChannelStableKey = epgChannelStableKey,
        isManual = isManual,
        confidence = confidence,
    )

private fun ChannelEntity.toDomain(): Channel =
    Channel(
        id = id,
        providerId = providerId,
        categoryId = categoryId,
        stableKey = stableKey,
        remoteId = remoteId,
        channelNumber = channelNumber,
        name = name,
        logoUrl = logoUrl,
        isCatchupAvailable = isCatchupAvailable,
        catchupDays = catchupDays,
    )
