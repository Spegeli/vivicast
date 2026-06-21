package com.vivicast.tv.data.epg

import com.vivicast.tv.domain.model.EpgChannelMapping
import com.vivicast.tv.domain.model.EpgProgram
import com.vivicast.tv.domain.model.EpgSource
import com.vivicast.tv.domain.model.ProviderEpgSource
import kotlinx.coroutines.flow.Flow

interface EpgRepository {
    fun observeEpgSources(): Flow<List<EpgSource>>

    fun observeProviderEpgSources(providerId: String): Flow<List<ProviderEpgSource>>

    fun observeProgramsForChannel(
        providerId: String,
        channelId: String,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<EpgProgram>>

    fun observeMappingsForChannel(providerId: String, channelId: String): Flow<List<EpgChannelMapping>>
}
