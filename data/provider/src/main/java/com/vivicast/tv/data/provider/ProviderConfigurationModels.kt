package com.vivicast.tv.data.provider

import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderType
import java.util.concurrent.ConcurrentHashMap

data class ProviderCreateRequest(
    val name: String,
    val type: ProviderType,
    val m3uSourceMode: M3uSourceMode = M3uSourceMode.Url,
    val m3uUrl: String? = null,
    val m3uContent: String? = null,
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
    val m3uSourceMode: M3uSourceMode? = null,
    val m3uUrl: String? = null,
    val m3uContent: String? = null,
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
    data class M3u(
        val url: String? = null,
        val sourceMode: M3uSourceMode = M3uSourceMode.Url,
        val inlineContent: String? = null,
    ) : ProviderCredentials
    data class Xtream(
        val serverUrl: String,
        val username: String,
        val password: String,
    ) : ProviderCredentials
}

enum class M3uSourceMode {
    Url,
    File,
    Clipboard,
}

val M3uSourceMode.isAutomaticallyRefreshable: Boolean
    get() = this == M3uSourceMode.Url

const val MAX_M3U_INLINE_SOURCE_CHARS = 5_000_000
const val DEFAULT_REFRESH_INTERVAL_HOURS = 12

object TransientM3uSourceStore {
    private val sources = ConcurrentHashMap<String, String>()

    fun put(providerId: String, content: String) {
        sources[providerId] = content
    }

    fun read(providerId: String): String? =
        sources[providerId]

    fun clear(providerId: String) {
        sources.remove(providerId)
    }
}
