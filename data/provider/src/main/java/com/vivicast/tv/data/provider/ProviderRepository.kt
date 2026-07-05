package com.vivicast.tv.data.provider

import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderStatus
import kotlinx.coroutines.flow.Flow

interface ProviderRepository {
    fun observeProviders(): Flow<List<Provider>>

    suspend fun getProvider(providerId: String): Provider?

    suspend fun getCredentials(providerId: String): ProviderCredentials?

    suspend fun createProvider(request: ProviderCreateRequest): ProviderSaveResult

    suspend fun updateProvider(request: ProviderUpdateRequest): ProviderSaveResult

    suspend fun saveProvider(provider: Provider)

    suspend fun setProviderStatus(providerId: String, status: ProviderStatus)

    suspend fun setProviderActive(providerId: String, isActive: Boolean)

    suspend fun setProviderEnabled(providerId: String, isEnabled: Boolean)

    /** Persists the latest Xtream account snapshot (expiry + max connections). No-op for M3U providers. */
    suspend fun updateXtreamAccountInfo(providerId: String, expiresAtMillis: Long?, maxConnections: Int?)

    suspend fun deleteProvider(providerId: String)
}
