package com.vivicast.tv.data.provider

import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderStatus
import kotlinx.coroutines.flow.Flow

interface ProviderRepository {
    fun observeProviders(): Flow<List<Provider>>

    suspend fun getProvider(providerId: String): Provider?

    suspend fun saveProvider(provider: Provider)

    suspend fun setProviderStatus(providerId: String, status: ProviderStatus)

    suspend fun setProviderActive(providerId: String, isActive: Boolean)

    suspend fun deleteProvider(providerId: String)
}
