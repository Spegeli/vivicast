package com.vivicast.tv.data.playback

import com.vivicast.tv.domain.model.ChannelHistory
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.PlaybackProgress
import kotlinx.coroutines.flow.Flow

interface PlaybackRepository {
    fun observeContinueWatching(providerId: String): Flow<List<PlaybackProgress>>

    fun observeRecentChannels(providerId: String, limit: Int): Flow<List<ChannelHistory>>

    suspend fun getProgress(providerId: String, mediaType: MediaType, mediaId: String): PlaybackProgress?

    suspend fun saveProgress(progress: PlaybackProgress)

    suspend fun saveChannelHistory(history: ChannelHistory)

    suspend fun clearProviderPlayback(providerId: String)
}
