package com.vivicast.tv.backup

import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.model.CategoryEntity
import com.vivicast.tv.core.database.model.ProviderCategorySettingsEntity
import com.vivicast.tv.core.database.model.ChannelHistoryEntity
import com.vivicast.tv.core.database.model.EpgChannelMappingEntity
import com.vivicast.tv.core.database.model.EpgSourceEntity
import com.vivicast.tv.core.database.model.FavoriteEntity
import com.vivicast.tv.core.database.model.PlaybackProgressEntity
import com.vivicast.tv.core.database.model.ProviderEntity
import com.vivicast.tv.core.database.model.ProviderEpgSourceEntity
import com.vivicast.tv.core.datastore.UserPreferencesStore
import com.vivicast.tv.core.security.PinSecurityStateStore
import com.vivicast.tv.core.security.SecureKey
import com.vivicast.tv.core.security.SecureValueStore
import com.vivicast.tv.data.provider.DiskM3uFileSourceStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class StandardBackupExporter(
    private val database: VivicastDatabase,
    private val userPreferencesStore: UserPreferencesStore,
    private val secureValueStore: SecureValueStore,
    private val pinSecurityStateStore: PinSecurityStateStore,
    private val m3uFileSourceStore: DiskM3uFileSourceStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun buildDocument(): StandardBackupDocument =
        buildDocument(exportedAtMillis = clock())

    private suspend fun buildDocument(exportedAtMillis: Long): StandardBackupDocument {
        val providers = database.providerDao().getProviders()
        val providerStableKeys = providers.associate { it.id to it.stableKey }
        val epgSources = database.epgDao().getEpgSources()
        val epgSourceStableKeys = epgSources.associate { it.id to it.stableKey }
        val providerEpgSources = providers.flatMap { provider ->
            database.epgDao().getProviderEpgSources(provider.id)
        }
        return StandardBackupDocument(
            exportedAtMillis = exportedAtMillis,
            preferences = userPreferencesStore.values.first(),
            providers = providers.map { it.toBackupProvider() },
            epgSources = epgSources.map { it.toBackupEpgSource() },
            providerEpgSources = providerEpgSources.mapNotNull {
                it.toBackupProviderEpgSource(providerStableKeys, epgSourceStableKeys)
            },
            epgChannelMappings = providerEpgSources.flatMap { link ->
                database.epgDao().getMappingsForProviderAndSource(link.providerId, link.epgSourceId)
            }.mapNotNull {
                it.toBackupEpgChannelMapping(providerStableKeys, epgSourceStableKeys)
            },
            categories = providers.flatMap { provider ->
                BACKUP_CATEGORY_TYPES.flatMap { type -> database.catalogDao().getCategories(provider.id, type) }
            }.mapNotNull { it.toBackupCategory(providerStableKeys) },
            categorySettings = providers.flatMap { provider ->
                BACKUP_CATEGORY_TYPES.mapNotNull { type ->
                    database.providerCategorySettingsDao().getSettings(provider.id, type)
                        ?.toBackupCategorySettings(providerStableKeys)
                }
            },
            favorites = database.favoritesDao().getFavorites().mapNotNull { it.toBackupFavorite(providerStableKeys) },
            playbackProgress = database.playbackDao().getPlaybackProgress()
                .mapNotNull { it.toBackupPlaybackProgress(providerStableKeys) },
            channelHistory = database.playbackDao().getChannelHistory()
                .mapNotNull { it.toBackupChannelHistory(providerStableKeys) },
            searchHistory = database.searchDao().getSearchHistory().map { it.query },
            security = StandardBackupSecuritySummary(
                parentalProtectionWasActive = pinSecurityStateStore.read().hasPin,
            ),
        )
    }

    /** Internal, best-effort snapshot of the current state (sanitized, no secrets) for the pre-restore safety backup. */
    suspend fun exportInternalSnapshotJson(indentSpaces: Int = 2): String =
        buildDocument().toJsonString(indentSpaces)

    /** Support/settings export: readable JSON with settings + non-secret metadata, but no source URLs and
     *  no secrets. Not a restorable backup — for sending to support. */
    suspend fun exportSupportSettingsJson(indentSpaces: Int = 2): String {
        val document = buildDocument()
        val secretFree = document.copy(
            providers = document.providers.map { it.copy(source = null) },
            epgSources = document.epgSources.map { it.copy(url = null) },
        )
        return secretFree.toJsonString(indentSpaces)
    }

    suspend fun buildBackupPayloadJson(indentSpaces: Int = 2): String =
        buildBackupPayloadJsonObject(exportedAtMillis = clock()).toString(indentSpaces)

    /** Returns the whole encrypted .vcbak as opaque bytes (binary container, see [encryptBackupPayload]). */
    suspend fun exportBackup(passphrase: CharArray): ByteArray =
        encryptBackupPayload(
            payloadJson = buildBackupPayloadJsonObject(clock()).toString(0),
            passphrase = passphrase,
        )

    private suspend fun buildBackupPayloadJsonObject(exportedAtMillis: Long): JSONObject {
        val providers = database.providerDao().getProviders()
        val epgSources = database.epgDao().getEpgSources()
        val providersByStableKey = providers.associateBy { it.stableKey }
        val epgSourcesByStableKey = epgSources.associateBy { it.stableKey }
        val json = buildDocument(exportedAtMillis).toJson()

        json.put("exportMode", "FULL")
        json.getJSONArray("providers").forEachObject { provider ->
            val stableKey = provider.optString("stableKey")
            val fullSource = providersByStableKey[stableKey]?.toFullBackupSourceJson()
            provider.put("source", fullSource ?: JSONObject.NULL)
        }
        json.getJSONArray("epgSources").forEachObject { source ->
            val stableKey = source.optString("stableKey")
            source.put("url", epgSourcesByStableKey[stableKey]?.let { secureValueStore.read(SecureKey(it.sourceConfigKey)) } ?: JSONObject.NULL)
        }
        return json
    }

    private suspend fun ProviderEntity.toBackupProvider(): StandardBackupProvider =
        StandardBackupProvider(
            stableKey = stableKey,
            name = name,
            type = type,
            isActive = isActive,
            status = status,
            includeLiveTv = includeLiveTv,
            includeMovies = includeMovies,
            includeSeries = includeSeries,
            refreshIntervalHours = refreshIntervalHours,
            logoPriority = logoPriority,
            xtreamOutputFormat = xtreamOutputFormat,
            userAgent = userAgent,
            refreshOnAppStartEnabled = refreshOnAppStartEnabled,
            source = when (type) {
                PROVIDER_TYPE_M3U -> secureValueStore.read(SecureKey("$sourceConfigKey:$FIELD_M3U_URL"))
                    ?.let(::standardBackupM3uUrlOrNull)
                    ?.let { StandardBackupProviderSource(m3uUrl = it) }

                PROVIDER_TYPE_XTREAM -> secureValueStore.read(SecureKey("$sourceConfigKey:$FIELD_XTREAM_SERVER_URL"))
                    ?.let(::standardBackupXtreamServerUrlOrNull)
                    ?.let { StandardBackupProviderSource(xtreamServerUrl = it) }

                else -> null
            },
        )

    private suspend fun EpgSourceEntity.toBackupEpgSource(): StandardBackupEpgSource =
        StandardBackupEpgSource(
            stableKey = stableKey,
            name = name,
            timeShiftMinutes = timeShiftMinutes,
            isActive = isActive,
            url = secureValueStore.read(SecureKey(sourceConfigKey)).let(::standardBackupEpgUrlOrNull),
        )

    private suspend fun ProviderEntity.toFullBackupSourceJson(): JSONObject? =
        when (type) {
            // URL-mode M3U keeps its fetchable URL; File-mode M3U has no URL, so embed the raw playlist
            // content (persisted on disk) — the only way restore can rebuild the catalog.
            PROVIDER_TYPE_M3U -> secureValueStore.read(SecureKey("$sourceConfigKey:$FIELD_M3U_URL"))
                ?.takeIf { it.isNotBlank() }
                ?.let { JSONObject().put("m3uUrl", it) }
                ?: m3uFileSourceStore.read(id)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { JSONObject().put("m3uInlineContent", it) }

            PROVIDER_TYPE_XTREAM -> JSONObject()
                .putIfPresent("xtreamServerUrl", secureValueStore.read(SecureKey("$sourceConfigKey:$FIELD_XTREAM_SERVER_URL")))
                .putIfPresent("xtreamUsername", secureValueStore.read(SecureKey("$sourceConfigKey:$FIELD_XTREAM_USERNAME")))
                .putIfPresent("xtreamPassword", secureValueStore.read(SecureKey("$sourceConfigKey:$FIELD_XTREAM_PASSWORD")))
                .takeIf { it.length() > 0 }

            else -> null
        }
}

private fun ProviderEpgSourceEntity.toBackupProviderEpgSource(
    providerStableKeys: Map<String, String>,
    epgSourceStableKeys: Map<String, String>,
): StandardBackupProviderEpgSource? {
    val providerStableKey = providerStableKeys[providerId] ?: return null
    val epgSourceStableKey = epgSourceStableKeys[epgSourceId] ?: return null
    return StandardBackupProviderEpgSource(
        providerStableKey = providerStableKey,
        epgSourceStableKey = epgSourceStableKey,
        priority = priority,
    )
}

private fun EpgChannelMappingEntity.toBackupEpgChannelMapping(
    providerStableKeys: Map<String, String>,
    epgSourceStableKeys: Map<String, String>,
): StandardBackupEpgChannelMapping? {
    val providerStableKey = providerStableKeys[providerId] ?: return null
    val mappedEpgSourceStableKey = epgSourceStableKeys[epgSourceId] ?: epgSourceStableKey.takeIf { it.isNotBlank() }
        ?: return null
    return StandardBackupEpgChannelMapping(
        providerStableKey = providerStableKey,
        channelStableKey = channelStableKey,
        epgSourceStableKey = mappedEpgSourceStableKey,
        epgChannelStableKey = epgChannelStableKey,
        isManual = isManual,
        confidence = confidence,
    )
}

private fun CategoryEntity.toBackupCategory(
    providerStableKeys: Map<String, String>,
): StandardBackupCategory? {
    val providerStableKey = providerStableKeys[providerId] ?: return null
    return StandardBackupCategory(
        providerStableKey = providerStableKey,
        stableKey = stableKey,
        type = type,
        name = name,
        sortOrder = sortOrder,
        isHidden = isHidden,
        manualSortOrder = manualSortOrder,
    )
}

private fun ProviderCategorySettingsEntity.toBackupCategorySettings(
    providerStableKeys: Map<String, String>,
): StandardBackupCategorySettings? {
    val providerStableKey = providerStableKeys[providerId] ?: return null
    return StandardBackupCategorySettings(
        providerStableKey = providerStableKey,
        type = type,
        sortMode = sortMode,
        hideNewGroups = hideNewGroups,
    )
}

private fun FavoriteEntity.toBackupFavorite(
    providerStableKeys: Map<String, String>,
): StandardBackupFavorite? {
    val providerStableKey = providerStableKeys[providerId] ?: return null
    return StandardBackupFavorite(
        providerStableKey = providerStableKey,
        mediaType = mediaType,
        mediaStableKey = mediaStableKey,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun PlaybackProgressEntity.toBackupPlaybackProgress(
    providerStableKeys: Map<String, String>,
): StandardBackupPlaybackProgress? {
    val providerStableKey = providerStableKeys[providerId] ?: return null
    return StandardBackupPlaybackProgress(
        providerStableKey = providerStableKey,
        mediaType = mediaType,
        mediaStableKey = mediaStableKey,
        positionMillis = positionMillis,
        durationMillis = durationMillis,
        progressPercent = progressPercent,
        isCompleted = isCompleted,
        lastWatchedAt = lastWatchedAt,
        updatedAt = updatedAt,
    )
}

private fun ChannelHistoryEntity.toBackupChannelHistory(
    providerStableKeys: Map<String, String>,
): StandardBackupChannelHistory? {
    val providerStableKey = providerStableKeys[providerId] ?: return null
    return StandardBackupChannelHistory(
        providerStableKey = providerStableKey,
        channelStableKey = channelStableKey,
        watchedAt = watchedAt,
        durationWatchedMillis = durationWatchedMillis,
        updatedAt = updatedAt,
    )
}

private val BACKUP_CATEGORY_TYPES = listOf("LIVE", "MOVIE", "SERIES")
private const val PROVIDER_TYPE_M3U = "M3U"
private const val PROVIDER_TYPE_XTREAM = "XTREAM"
private const val FIELD_M3U_URL = "m3u_url"
private const val FIELD_XTREAM_SERVER_URL = "xtream_server_url"
private const val FIELD_XTREAM_USERNAME = "xtream_username"
private const val FIELD_XTREAM_PASSWORD = "xtream_password"

private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
    for (index in 0 until length()) {
        optJSONObject(index)?.let(block)
    }
}

private fun JSONObject.putIfPresent(key: String, value: String?): JSONObject {
    val trimmed = value?.trim()?.takeIf { it.isNotBlank() } ?: return this
    return put(key, trimmed)
}
