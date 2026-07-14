package com.vivicast.tv.system

import com.vivicast.tv.data.playback.PlaybackRepository
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.ProviderSaveResult
import com.vivicast.tv.data.provider.ProviderUpdateRequest
import com.vivicast.tv.domain.model.ChannelHistory
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.PlaybackProgress
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderStatus
import kotlinx.coroutines.flow.Flow

class SystemIntegrationPlaybackRepository(
    private val delegate: PlaybackRepository,
    private val syncWatchNext: suspend () -> Unit,
) : PlaybackRepository {
    override fun observeContinueWatching(providerId: String): Flow<List<PlaybackProgress>> =
        delegate.observeContinueWatching(providerId)

    override fun observeAllContinueWatching(): Flow<List<PlaybackProgress>> =
        delegate.observeAllContinueWatching()

    override suspend fun getWatchNextProgress(): List<PlaybackProgress> =
        delegate.getWatchNextProgress()

    override fun observeRecentChannels(providerId: String, limit: Int): Flow<List<ChannelHistory>> =
        delegate.observeRecentChannels(providerId, limit)

    override fun observeAllRecentChannels(limit: Int): Flow<List<ChannelHistory>> =
        delegate.observeAllRecentChannels(limit)

    override suspend fun getProgress(providerId: String, mediaType: MediaType, mediaId: String): PlaybackProgress? =
        delegate.getProgress(providerId, mediaType, mediaId)

    override suspend fun saveProgress(progress: PlaybackProgress) {
        delegate.saveProgress(progress)
        if (progress.mediaType == MediaType.Movie || progress.mediaType == MediaType.Episode) {
            syncWatchNextSafely()
        }
    }

    override suspend fun deleteProgress(providerId: String, mediaType: MediaType, mediaId: String) {
        delegate.deleteProgress(providerId, mediaType, mediaId)
        if (mediaType == MediaType.Movie || mediaType == MediaType.Episode) {
            syncWatchNextSafely()
        }
    }

    override suspend fun saveChannelHistory(history: ChannelHistory) {
        delegate.saveChannelHistory(history)
    }

    override suspend fun clearProviderPlayback(providerId: String) {
        delegate.clearProviderPlayback(providerId)
        syncWatchNextSafely()
    }

    override suspend fun clearLiveTvHistory() {
        delegate.clearLiveTvHistory()
    }

    override suspend fun clearMovieProgress() {
        delegate.clearMovieProgress()
        syncWatchNextSafely()
    }

    override suspend fun clearSeriesProgress() {
        delegate.clearSeriesProgress()
        syncWatchNextSafely()
    }

    private suspend fun syncWatchNextSafely() {
        runCatching { syncWatchNext() }
    }
}

class SystemIntegrationProviderRepository(
    private val delegate: ProviderRepository,
    private val syncWatchNext: suspend () -> Unit,
) : ProviderRepository {
    override fun observeProviders(): Flow<List<Provider>> =
        delegate.observeProviders()

    override suspend fun getProvider(providerId: String): Provider? =
        delegate.getProvider(providerId)

    override suspend fun getCredentials(providerId: String): ProviderCredentials? =
        delegate.getCredentials(providerId)

    override suspend fun getProviderM3uInlineContent(providerId: String): String? =
        delegate.getProviderM3uInlineContent(providerId)

    override suspend fun createProvider(request: ProviderCreateRequest): ProviderSaveResult =
        delegate.createProvider(request).also { syncWatchNextSafely() }

    override suspend fun updateProvider(request: ProviderUpdateRequest): ProviderSaveResult =
        delegate.updateProvider(request).also { syncWatchNextSafely() }

    override suspend fun saveProvider(provider: Provider) {
        delegate.saveProvider(provider)
        syncWatchNextSafely()
    }

    override suspend fun setProviderStatus(providerId: String, status: ProviderStatus) {
        delegate.setProviderStatus(providerId, status)
        syncWatchNextSafely()
    }

    override suspend fun setProviderActive(providerId: String, isActive: Boolean) {
        delegate.setProviderActive(providerId, isActive)
        syncWatchNextSafely()
    }

    override suspend fun setProviderEnabled(providerId: String, isEnabled: Boolean) {
        delegate.setProviderEnabled(providerId, isEnabled)
        syncWatchNextSafely()
    }

    override suspend fun updateXtreamAccountInfo(providerId: String, expiresAtMillis: Long?, maxConnections: Int?) {
        delegate.updateXtreamAccountInfo(providerId, expiresAtMillis, maxConnections)
    }

    override suspend fun deleteProvider(providerId: String) {
        delegate.deleteProvider(providerId)
        syncWatchNextSafely()
    }

    private suspend fun syncWatchNextSafely() {
        runCatching { syncWatchNext() }
    }
}
