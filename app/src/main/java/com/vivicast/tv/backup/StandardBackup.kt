package com.vivicast.tv.backup

import com.vivicast.tv.core.datastore.UserPreferences
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderType
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

const val STANDARD_BACKUP_SCHEMA_VERSION = 1

data class StandardBackupDocument(
    val exportedAtMillis: Long,
    val preferences: UserPreferences = UserPreferences(),
    val providers: List<StandardBackupProvider> = emptyList(),
    val epgSources: List<StandardBackupEpgSource> = emptyList(),
    val providerEpgSources: List<StandardBackupProviderEpgSource> = emptyList(),
    val epgChannelMappings: List<StandardBackupEpgChannelMapping> = emptyList(),
    val categories: List<StandardBackupCategory> = emptyList(),
    val favorites: List<StandardBackupFavorite> = emptyList(),
    val playbackProgress: List<StandardBackupPlaybackProgress> = emptyList(),
    val channelHistory: List<StandardBackupChannelHistory> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val security: StandardBackupSecuritySummary = StandardBackupSecuritySummary(),
)

data class StandardBackupProvider(
    val stableKey: String,
    val name: String,
    val type: String,
    val isActive: Boolean,
    val status: String,
    val includeLiveTv: Boolean,
    val includeMovies: Boolean,
    val includeSeries: Boolean,
    val refreshIntervalHours: Int,
    val logoPriority: String,
    val source: StandardBackupProviderSource? = null,
)

data class StandardBackupProviderSource(
    val m3uUrl: String? = null,
    val xtreamServerUrl: String? = null,
)

data class StandardBackupEpgSource(
    val stableKey: String,
    val name: String,
    val timeShiftMinutes: Int,
    val isActive: Boolean,
    val url: String? = null,
)

data class StandardBackupProviderEpgSource(
    val providerStableKey: String,
    val epgSourceStableKey: String,
    val priority: Int,
)

data class StandardBackupEpgChannelMapping(
    val providerStableKey: String,
    val channelStableKey: String,
    val epgSourceStableKey: String,
    val epgChannelStableKey: String,
    val isManual: Boolean,
    val confidence: Float,
)

data class StandardBackupCategory(
    val providerStableKey: String,
    val stableKey: String,
    val type: String,
    val name: String,
    val sortOrder: Int,
    val isHidden: Boolean,
)

data class StandardBackupFavorite(
    val providerStableKey: String,
    val mediaType: String,
    val mediaStableKey: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class StandardBackupPlaybackProgress(
    val providerStableKey: String,
    val mediaType: String,
    val mediaStableKey: String,
    val positionMillis: Long,
    val durationMillis: Long,
    val progressPercent: Int,
    val isCompleted: Boolean,
    val lastWatchedAt: Long,
    val updatedAt: Long,
)

data class StandardBackupChannelHistory(
    val providerStableKey: String,
    val channelStableKey: String,
    val watchedAt: Long,
    val durationWatchedMillis: Long,
    val updatedAt: Long,
)

data class StandardBackupSecuritySummary(
    val parentalProtectionWasActive: Boolean = false,
)

fun Provider.toStandardBackupProvider(credentials: ProviderCredentials?): StandardBackupProvider =
    StandardBackupProvider(
        stableKey = stableKey,
        name = name,
        type = type.name,
        isActive = isActive,
        status = status.name,
        includeLiveTv = includeLiveTv,
        includeMovies = includeMovies,
        includeSeries = includeSeries,
        refreshIntervalHours = refreshIntervalHours,
        logoPriority = logoPriority,
        source = when {
            type == ProviderType.M3u && credentials is ProviderCredentials.M3u ->
                standardBackupM3uUrlOrNull(credentials.url)?.let { StandardBackupProviderSource(m3uUrl = it) }

            type == ProviderType.Xtream && credentials is ProviderCredentials.Xtream ->
                standardBackupXtreamServerUrlOrNull(credentials.serverUrl)
                    ?.let { StandardBackupProviderSource(xtreamServerUrl = it) }

            else -> null
        },
    )

fun standardBackupM3uUrlOrNull(url: String?): String? =
    standardBackupSafeHttpUrlOrNull(url, keepPath = true)

fun standardBackupEpgUrlOrNull(url: String?): String? =
    standardBackupSafeHttpUrlOrNull(url, keepPath = true)

fun standardBackupXtreamServerUrlOrNull(url: String?): String? {
    val parsed = parseStandardBackupHttpUri(url) ?: return null
    val port = if (parsed.port == -1) "" else ":${parsed.port}"
    return "${parsed.scheme.lowercase()}://${parsed.host}$port"
}

fun StandardBackupDocument.toJsonString(indentSpaces: Int = 2): String =
    toJson().toString(indentSpaces)

fun StandardBackupDocument.toJson(): JSONObject =
    JSONObject()
        .put("schemaVersion", STANDARD_BACKUP_SCHEMA_VERSION)
        .put("exportMode", "STANDARD")
        .put("exportedAtMillis", exportedAtMillis)
        .put("preferences", preferences.toStandardBackupJson())
        .put("providers", providers.toJsonArray { it.toJson() })
        .put("epgSources", epgSources.toJsonArray { it.toJson() })
        .put("providerEpgSources", providerEpgSources.toJsonArray { it.toJson() })
        .put("epgChannelMappings", epgChannelMappings.toJsonArray { it.toJson() })
        .put("categories", categories.toJsonArray { it.toJson() })
        .put("favorites", favorites.toJsonArray { it.toJson() })
        .put("playbackProgress", playbackProgress.toJsonArray { it.toJson() })
        .put("channelHistory", channelHistory.toJsonArray { it.toJson() })
        .put("searchHistory", JSONArray(searchHistory))
        .put("security", security.toJson())

private fun UserPreferences.toStandardBackupJson(): JSONObject =
    JSONObject()
        .put("selectedProviderId", JSONObject.NULL)
        .put("general", JSONObject()
            .put("launchOnBoot", general.launchOnBoot)
            .put("doubleBackToExit", general.doubleBackToExit)
            .put("backgroundRefreshEnabled", general.backgroundRefreshEnabled)
            .put("globalUserAgent", general.globalUserAgent))
        .put("appearance", JSONObject()
            .put("backgroundColor", appearance.backgroundColor.name)
            .put("accentColor", appearance.accentColor.name)
            .put("transparency", appearance.transparency.name)
            .put("fontScale", appearance.fontScale.name)
            .put("language", appearance.language.name)
            .put("animationSpeed", appearance.animationSpeed.name))
        .put("playback", JSONObject()
            .put("bufferSize", playback.bufferSize.name)
            .put("audioDecoder", playback.audioDecoder.name)
            .put("videoDecoder", playback.videoDecoder.name)
            .put("afrEnabled", playback.afrEnabled)
            .put("timeshiftEnabled", playback.timeshiftEnabled)
            .put("timeshiftStorage", playback.timeshiftStorage.name)
            .put("timeshiftMinutes", playback.timeshiftMinutes)
            .put("preferredAudioLanguage", playback.preferredAudioLanguage)
            .put("preferredSubtitleLanguage", playback.preferredSubtitleLanguage)
            .put("externalPlayer", playback.externalPlayer.name)
            .put("autoNextEnabled", playback.autoNextEnabled)
            .put("autoNextCountdownSeconds", playback.autoNextCountdownSeconds))
        .put("history", JSONObject()
            .put("enabled", history.enabled)
            .put("maxRecentChannels", history.maxRecentChannels)
            .put("watchedThresholdPercent", history.watchedThresholdPercent))
        .put("expandedLiveTvProviderIds", JSONArray(expandedLiveTvProviderIds.toList()))
        .put("epg", JSONObject()
            .put("pastRetentionDays", epg.pastRetentionDays)
            .put("futureRetentionDays", epg.futureRetentionDays)
            .put("refreshIntervalHours", epg.refreshIntervalHours)
            .put("refreshOnAppStartEnabled", epg.refreshOnAppStartEnabled)
            .put("refreshOnPlaylistChangeEnabled", epg.refreshOnPlaylistChangeEnabled))
        .put("backup", JSONObject()
            .put("targetType", backup.target.name))

private fun StandardBackupProvider.toJson(): JSONObject =
    JSONObject()
        .put("stableKey", stableKey)
        .put("name", name)
        .put("type", type)
        .put("isActive", isActive)
        .put("status", status)
        .put("includeLiveTv", includeLiveTv)
        .put("includeMovies", includeMovies)
        .put("includeSeries", includeSeries)
        .put("refreshIntervalHours", refreshIntervalHours)
        .put("logoPriority", logoPriority)
        .put("source", source?.toJson())

private fun StandardBackupProviderSource.toJson(): JSONObject =
    JSONObject()
        .put("m3uUrl", m3uUrl)
        .put("xtreamServerUrl", xtreamServerUrl)

private fun StandardBackupEpgSource.toJson(): JSONObject =
    JSONObject()
        .put("stableKey", stableKey)
        .put("name", name)
        .put("timeShiftMinutes", timeShiftMinutes)
        .put("isActive", isActive)
        .put("url", url)

private fun StandardBackupProviderEpgSource.toJson(): JSONObject =
    JSONObject()
        .put("providerStableKey", providerStableKey)
        .put("epgSourceStableKey", epgSourceStableKey)
        .put("priority", priority)

private fun StandardBackupEpgChannelMapping.toJson(): JSONObject =
    JSONObject()
        .put("providerStableKey", providerStableKey)
        .put("channelStableKey", channelStableKey)
        .put("epgSourceStableKey", epgSourceStableKey)
        .put("epgChannelStableKey", epgChannelStableKey)
        .put("isManual", isManual)
        .put("confidence", confidence.toDouble())

private fun StandardBackupCategory.toJson(): JSONObject =
    JSONObject()
        .put("providerStableKey", providerStableKey)
        .put("stableKey", stableKey)
        .put("type", type)
        .put("name", name)
        .put("sortOrder", sortOrder)
        .put("isHidden", isHidden)

private fun StandardBackupFavorite.toJson(): JSONObject =
    JSONObject()
        .put("providerStableKey", providerStableKey)
        .put("mediaType", mediaType)
        .put("mediaStableKey", mediaStableKey)
        .put("sortOrder", sortOrder)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

private fun StandardBackupPlaybackProgress.toJson(): JSONObject =
    JSONObject()
        .put("providerStableKey", providerStableKey)
        .put("mediaType", mediaType)
        .put("mediaStableKey", mediaStableKey)
        .put("positionMillis", positionMillis)
        .put("durationMillis", durationMillis)
        .put("progressPercent", progressPercent)
        .put("isCompleted", isCompleted)
        .put("lastWatchedAt", lastWatchedAt)
        .put("updatedAt", updatedAt)

private fun StandardBackupChannelHistory.toJson(): JSONObject =
    JSONObject()
        .put("providerStableKey", providerStableKey)
        .put("channelStableKey", channelStableKey)
        .put("watchedAt", watchedAt)
        .put("durationWatchedMillis", durationWatchedMillis)
        .put("updatedAt", updatedAt)

private fun StandardBackupSecuritySummary.toJson(): JSONObject =
    JSONObject()
        .put("parentalProtectionWasActive", parentalProtectionWasActive)

private fun <T> List<T>.toJsonArray(mapper: (T) -> JSONObject): JSONArray =
    JSONArray().also { array -> forEach { array.put(mapper(it)) } }

private fun standardBackupSafeHttpUrlOrNull(url: String?, keepPath: Boolean): String? {
    val parsed = parseStandardBackupHttpUri(url) ?: return null
    if (parsed.rawPath.hasSecretLikePathSegment()) return null
    if (!keepPath) {
        val port = if (parsed.port == -1) "" else ":${parsed.port}"
        return "${parsed.scheme.lowercase()}://${parsed.host}$port"
    }
    return url?.trim()
}

private fun parseStandardBackupHttpUri(url: String?): URI? {
    val value = url?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val parsed = runCatching { URI(value) }.getOrNull() ?: return null
    if (parsed.scheme?.lowercase() !in setOf("http", "https")) return null
    if (parsed.host.isNullOrBlank()) return null
    if (!parsed.rawUserInfo.isNullOrBlank()) return null
    if (!parsed.rawQuery.isNullOrBlank()) return null
    if (!parsed.rawFragment.isNullOrBlank()) return null
    return parsed
}

private fun String?.hasSecretLikePathSegment(): Boolean {
    val path = this ?: return false
    val secretMarkers = listOf("token", "apikey", "api_key", "auth", "secret", "password", "passwd", "session")
    return path.split('/').any { segment ->
        val lower = segment.lowercase()
        secretMarkers.any { marker -> lower.contains(marker) }
    }
}
