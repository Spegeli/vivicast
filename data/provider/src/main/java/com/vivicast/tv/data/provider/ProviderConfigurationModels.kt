package com.vivicast.tv.data.provider

import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderType

data class ProviderCreateRequest(
    val name: String,
    val type: ProviderType,
    val m3uUrl: String? = null,
    val xtreamServerUrl: String? = null,
    val xtreamUsername: String? = null,
    val xtreamPassword: String? = null,
    val includeLiveTv: Boolean = true,
    val includeMovies: Boolean = true,
    val includeSeries: Boolean = true,
    val refreshIntervalHours: Int = DEFAULT_REFRESH_INTERVAL_HOURS,
)

data class ProviderUpdateRequest(
    val providerId: String,
    val name: String,
    val m3uUrl: String? = null,
    val xtreamServerUrl: String? = null,
    val xtreamUsername: String? = null,
    val xtreamPassword: String? = null,
    val includeLiveTv: Boolean = true,
    val includeMovies: Boolean = true,
    val includeSeries: Boolean = true,
    val refreshIntervalHours: Int = DEFAULT_REFRESH_INTERVAL_HOURS,
)

data class ProviderSaveResult(
    val provider: Provider,
    val hasDuplicateName: Boolean,
)

sealed interface ProviderCredentials {
    data class M3u(val url: String) : ProviderCredentials
    data class Xtream(
        val serverUrl: String,
        val username: String,
        val password: String,
    ) : ProviderCredentials
}

const val DEFAULT_REFRESH_INTERVAL_HOURS = 12
