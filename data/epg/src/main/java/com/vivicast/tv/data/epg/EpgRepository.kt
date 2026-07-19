package com.vivicast.tv.data.epg

import com.vivicast.tv.domain.model.EpgChannelMapping
import com.vivicast.tv.domain.model.EpgProgram
import com.vivicast.tv.domain.model.EpgSource
import com.vivicast.tv.domain.model.ProviderEpgSource
import com.vivicast.tv.domain.model.Channel
import kotlinx.coroutines.flow.Flow

interface EpgRepository {
    fun observeEpgSources(): Flow<List<EpgSource>>

    /** One-shot snapshot of all EPG sources, for dedup/uniqueness scans. Prefer [observeEpgSources] for UI. */
    suspend fun getEpgSources(): List<EpgSource>

    fun observeProviderEpgSources(providerId: String): Flow<List<ProviderEpgSource>>

    fun observeChannelsForProvider(providerId: String): Flow<List<Channel>>

    fun observeProgramsForChannel(
        providerId: String,
        channelId: String,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<EpgProgram>>

    /**
     * The winner-resolved CURRENT programme for each of [channelIds] under one [providerId] at [nowMillis]
     * (Live-TV list). Winner-aware per channel (manual > lowest priority among active linked sources);
     * channels with no current programme on their winning source are simply absent from the result. Pass a
     * non-empty [channelIds].
     */
    fun observeCurrentProgramsForChannels(
        providerId: String,
        channelIds: List<String>,
        nowMillis: Long,
    ): Flow<List<EpgProgram>>

    fun observeMappingsForChannel(providerId: String, channelId: String): Flow<List<EpgChannelMapping>>

    suspend fun setManualChannelMapping(request: ManualEpgChannelMappingRequest): EpgChannelMapping

    suspend fun clearManualChannelMapping(providerId: String, channelId: String, epgSourceId: String)
}

data class ManualEpgChannelMappingRequest(
    val providerId: String,
    val channelId: String,
    val epgSourceId: String,
    val epgChannelId: String,
)
