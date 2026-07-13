package com.vivicast.tv.backup

import androidx.room.withTransaction
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.model.CategoryEntity
import com.vivicast.tv.core.database.model.ChannelHistoryEntity
import com.vivicast.tv.core.database.model.EpgChannelMappingEntity
import com.vivicast.tv.core.database.model.EpgSourceEntity
import com.vivicast.tv.core.database.model.FavoriteEntity
import com.vivicast.tv.core.database.model.PlaybackProgressEntity
import com.vivicast.tv.core.database.model.ProviderEntity
import com.vivicast.tv.core.database.model.ProviderEpgSourceEntity
import com.vivicast.tv.core.database.model.SearchHistoryEntity
import com.vivicast.tv.core.datastore.UserPreferencesStore
import com.vivicast.tv.core.security.PinSecurityStateStore
import com.vivicast.tv.core.security.SecureKey
import com.vivicast.tv.core.security.SecureValueStore
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class StandardBackupRestorer(
    private val database: VivicastDatabase,
    private val userPreferencesStore: UserPreferencesStore,
    private val secureValueStore: SecureValueStore,
    private val pinSecurityStateStore: PinSecurityStateStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun restore(jsonText: String): StandardBackupRestoreValidation {
        val validation = validateStandardBackupForRestore(jsonText)
        return restoreValidated(jsonText, validation)
    }

    suspend fun restoreBackup(
        container: ByteArray,
        passphrase: CharArray,
    ): StandardBackupRestoreValidation {
        val payload = decryptBackupPayload(container, passphrase)
            ?: return StandardBackupRestoreValidation.Invalid("Passphrase falsch oder Backup beschaedigt.")
        return restoreFullPayload(payload)
    }

    suspend fun restoreFullPayload(jsonText: String): StandardBackupRestoreValidation {
        val validation = validateFullBackupPayloadForRestore(jsonText)
        return restoreValidated(jsonText, validation)
    }

    private suspend fun restoreValidated(
        jsonText: String,
        validation: StandardBackupRestoreValidation,
    ): StandardBackupRestoreValidation {
        if (validation !is StandardBackupRestoreValidation.Valid) return validation

        val json = JSONObject(jsonText)
        val providers = json.optJSONArray("providers") ?: JSONArray()
        val epgSources = json.optJSONArray("epgSources") ?: JSONArray()
        val oldProviders = database.providerDao().getProviders()
        val oldEpgSources = database.epgDao().getEpgSources()
        val now = clock()

        database.withTransaction {
            oldProviders.forEach { provider ->
                database.catalogDao().deleteCatalogForProvider(provider.id)
                database.epgDao().deleteProgramsForProvider(provider.id)
                database.epgDao().deleteMappingsForProvider(provider.id)
                database.epgDao().deleteProviderEpgSources(provider.id)
                database.favoritesDao().deleteFavoritesForProvider(provider.id)
                database.playbackDao().deleteProgressForProvider(provider.id)
                database.playbackDao().deleteHistoryForProvider(provider.id)
                database.providerDao().deleteProvider(provider.id)
            }
            oldEpgSources.forEach { source ->
                database.epgDao().deleteProviderEpgSourcesForSource(source.id)
                database.epgDao().deleteMappingsForSource(source.id)
                database.epgDao().deleteProgramsForSource(source.id)
                database.epgDao().deleteEpgSource(source.id)
            }
            database.searchDao().clearSearchHistory()

            database.providerDao().upsertProviders(providers.toProviderEntities(now))
            database.epgDao().upsertEpgSources(epgSources.toEpgSourceEntities(now))

            val providerIds = providers.stableKeyIdMap()
            val epgSourceIds = epgSources.stableKeyIdMap()
            database.epgDao().upsertProviderEpgSources(
                (json.optJSONArray("providerEpgSources") ?: JSONArray()).toProviderEpgSourceEntities(providerIds, epgSourceIds),
            )
            database.epgDao().upsertMappings(
                (json.optJSONArray("epgChannelMappings") ?: JSONArray()).toEpgChannelMappingEntities(providerIds, epgSourceIds, now),
            )
            database.catalogDao().upsertCategories(
                (json.optJSONArray("categories") ?: JSONArray()).toCategoryEntities(providerIds, now),
            )
            (json.optJSONArray("favorites") ?: JSONArray()).toFavoriteEntities(providerIds).forEach {
                database.favoritesDao().upsertFavorite(it)
            }
            (json.optJSONArray("playbackProgress") ?: JSONArray()).toPlaybackProgressEntities(providerIds).forEach {
                database.playbackDao().upsertProgress(it)
            }
            (json.optJSONArray("channelHistory") ?: JSONArray()).toChannelHistoryEntities(providerIds).forEach {
                database.playbackDao().upsertChannelHistory(it)
            }
            (json.optJSONArray("searchHistory") ?: JSONArray()).toSearchHistoryEntities(now).forEach {
                database.searchDao().upsertSearchHistory(it)
            }
            database.androidTvSearchDao().rebuildEntries()
        }

        deleteOldSecrets(oldProviders, oldEpgSources)
        writeRestoredSecrets(providers, epgSources)
        pinSecurityStateStore.clear()
        // Re-apply the backed-up app settings (audit #3): general/appearance/playback/history/epg/backup
        // + expanded Live-TV groups. selectedProviderId + parental control stay reset below — providers
        // are re-selected and the PIN is re-armed by the user (reactivation hint), whatever the backup held.
        json.optJSONObject("preferences")?.let { prefs ->
            val restored = userPreferencesFromStandardBackupJson(prefs)
            userPreferencesStore.updateGeneral(restored.general)
            userPreferencesStore.updateAppearance(restored.appearance)
            userPreferencesStore.updatePlayback(restored.playback)
            userPreferencesStore.updateHistory(restored.history)
            userPreferencesStore.updateEpg(restored.epg)
            userPreferencesStore.updateBackup(restored.backup)
            userPreferencesStore.updateExpandedLiveTvProviderIds(restored.expandedLiveTvProviderIds)
        }
        userPreferencesStore.updateSelectedProviderId(null)
        return validation
    }

    private suspend fun deleteOldSecrets(
        providers: List<ProviderEntity>,
        epgSources: List<EpgSourceEntity>,
    ) {
        providers.forEach { provider ->
            secureValueStore.delete(SecureKey("${provider.sourceConfigKey}:$FIELD_M3U_URL"))
            secureValueStore.delete(SecureKey("${provider.sourceConfigKey}:$FIELD_XTREAM_SERVER_URL"))
            secureValueStore.delete(SecureKey("${provider.sourceConfigKey}:$FIELD_XTREAM_USERNAME"))
            secureValueStore.delete(SecureKey("${provider.sourceConfigKey}:$FIELD_XTREAM_PASSWORD"))
        }
        epgSources.forEach { source -> secureValueStore.delete(SecureKey(source.sourceConfigKey)) }
    }

    private suspend fun writeRestoredSecrets(providers: JSONArray, epgSources: JSONArray) {
        providers.forEachObject { provider ->
            val stableKey = provider.requiredString("stableKey") ?: return@forEachObject
            val source = provider.optJSONObject("source") ?: return@forEachObject
            val sourceConfigKey = sourceConfigKeyFor(stableKey)
            source.nullableString("m3uUrl")?.let { secureValueStore.write(SecureKey("$sourceConfigKey:$FIELD_M3U_URL"), it) }
            source.nullableString("xtreamServerUrl")?.let {
                secureValueStore.write(SecureKey("$sourceConfigKey:$FIELD_XTREAM_SERVER_URL"), it)
            }
            source.nullableString("xtreamUsername")?.let {
                secureValueStore.write(SecureKey("$sourceConfigKey:$FIELD_XTREAM_USERNAME"), it)
            }
            source.nullableString("xtreamPassword")?.let {
                secureValueStore.write(SecureKey("$sourceConfigKey:$FIELD_XTREAM_PASSWORD"), it)
            }
        }
        epgSources.forEachObject { source ->
            val stableKey = source.requiredString("stableKey") ?: return@forEachObject
            source.nullableString("url")?.let { secureValueStore.write(SecureKey(epgSourceConfigKeyFor(stableKey)), it) }
        }
    }
}

private fun JSONArray.toProviderEntities(now: Long): List<ProviderEntity> =
    mapObjects { provider ->
        val stableKey = provider.requiredString("stableKey") ?: return@mapObjects null
        val type = provider.optString("type").toStorageProviderType()
        val source = provider.optJSONObject("source")
        val hasRestorableSource = when (type) {
            PROVIDER_TYPE_M3U -> !source?.nullableString("m3uUrl").isNullOrBlank()
            PROVIDER_TYPE_XTREAM -> source != null &&
                !source.nullableString("xtreamServerUrl").isNullOrBlank() &&
                !source.nullableString("xtreamUsername").isNullOrBlank() &&
                !source.nullableString("xtreamPassword").isNullOrBlank()
            else -> false
        }
        ProviderEntity(
            id = stableKey,
            stableKey = stableKey,
            name = provider.optString("name", "Provider").ifBlank { "Provider" },
            type = type,
            sourceConfigKey = sourceConfigKeyFor(stableKey),
            isActive = provider.optBoolean("isActive", true),
            status = if (hasRestorableSource) {
                provider.optString("status", PROVIDER_STATUS_ACTIVE).ifBlank { PROVIDER_STATUS_ACTIVE }
            } else {
                PROVIDER_STATUS_CREDENTIALS_REQUIRED
            },
            includeLiveTv = provider.optBoolean("includeLiveTv", true),
            includeMovies = provider.optBoolean("includeMovies", true),
            includeSeries = provider.optBoolean("includeSeries", true),
            refreshIntervalHours = provider.optInt("refreshIntervalHours", 12).coerceIn(1, 168),
            logoPriority = provider.optString("logoPriority", "provider").ifBlank { "provider" },
            xtreamOutputFormat = provider.optString("xtreamOutputFormat", "hls").ifBlank { "hls" },
            userAgent = provider.nullableString("userAgent"),
            refreshOnAppStartEnabled = provider.optBoolean("refreshOnAppStartEnabled", true),
            createdAt = now,
            updatedAt = now,
        )
    }

private fun JSONArray.toEpgSourceEntities(now: Long): List<EpgSourceEntity> =
    mapObjects { source ->
        val stableKey = source.requiredString("stableKey") ?: return@mapObjects null
        EpgSourceEntity(
            id = stableKey,
            stableKey = stableKey,
            name = source.optString("name", "EPG").ifBlank { "EPG" },
            sourceConfigKey = epgSourceConfigKeyFor(stableKey),
            timeShiftMinutes = source.optInt("timeShiftMinutes", 0),
            isActive = source.optBoolean("isActive", true),
            createdAt = now,
            updatedAt = now,
        )
    }

private fun JSONArray.toProviderEpgSourceEntities(
    providerIds: Map<String, String>,
    epgSourceIds: Map<String, String>,
): List<ProviderEpgSourceEntity> =
    mapObjects { link ->
        val providerId = providerIds[link.requiredString("providerStableKey")] ?: return@mapObjects null
        val epgSourceId = epgSourceIds[link.requiredString("epgSourceStableKey")] ?: return@mapObjects null
        ProviderEpgSourceEntity(
            id = "$providerId:epg-source:$epgSourceId",
            providerId = providerId,
            epgSourceId = epgSourceId,
            priority = link.optInt("priority", 1).coerceAtLeast(1),
            createdAt = 0L,
        )
    }

private fun JSONArray.toEpgChannelMappingEntities(
    providerIds: Map<String, String>,
    epgSourceIds: Map<String, String>,
    now: Long,
): List<EpgChannelMappingEntity> =
    mapObjects { mapping ->
        val providerId = providerIds[mapping.requiredString("providerStableKey")] ?: return@mapObjects null
        val epgSourceId = epgSourceIds[mapping.requiredString("epgSourceStableKey")] ?: return@mapObjects null
        val channelStableKey = mapping.requiredString("channelStableKey") ?: return@mapObjects null
        val epgChannelStableKey = mapping.requiredString("epgChannelStableKey") ?: return@mapObjects null
        EpgChannelMappingEntity(
            id = "$providerId:mapping:$channelStableKey:$epgSourceId",
            providerId = providerId,
            channelId = channelStableKey,
            channelStableKey = channelStableKey,
            epgSourceId = epgSourceId,
            epgSourceStableKey = mapping.requiredString("epgSourceStableKey") ?: epgSourceId,
            epgChannelId = epgChannelStableKey,
            epgChannelStableKey = epgChannelStableKey,
            isManual = mapping.optBoolean("isManual", true),
            confidence = mapping.optDouble("confidence", 0.0).toFloat(),
            createdAt = now,
            updatedAt = now,
        )
    }

private fun JSONArray.toCategoryEntities(providerIds: Map<String, String>, now: Long): List<CategoryEntity> =
    mapObjects { category ->
        val providerId = providerIds[category.requiredString("providerStableKey")] ?: return@mapObjects null
        val stableKey = category.requiredString("stableKey") ?: return@mapObjects null
        val type = category.optString("type", CATEGORY_TYPE_LIVE).ifBlank { CATEGORY_TYPE_LIVE }
        CategoryEntity(
            id = "$providerId:category:${type.lowercase(Locale.US)}:$stableKey",
            providerId = providerId,
            stableKey = stableKey,
            type = type,
            remoteId = stableKey,
            name = category.optString("name", stableKey).ifBlank { stableKey },
            sortOrder = category.optInt("sortOrder", 0),
            isHidden = category.optBoolean("isHidden", false),
            createdAt = now,
            updatedAt = now,
        )
    }

private fun JSONArray.toFavoriteEntities(providerIds: Map<String, String>): List<FavoriteEntity> =
    mapObjects { favorite ->
        val providerId = providerIds[favorite.requiredString("providerStableKey")] ?: return@mapObjects null
        val mediaStableKey = favorite.requiredString("mediaStableKey") ?: return@mapObjects null
        val mediaType = favorite.optString("mediaType", "MOVIE").ifBlank { "MOVIE" }
        FavoriteEntity(
            id = "$providerId:favorite:${mediaType.lowercase(Locale.US)}:$mediaStableKey",
            providerId = providerId,
            mediaType = mediaType,
            mediaId = mediaStableKey,
            mediaStableKey = mediaStableKey,
            isPending = true,
            sortOrder = favorite.optInt("sortOrder", 0),
            createdAt = favorite.optLong("createdAt", 0L),
            updatedAt = favorite.optLong("updatedAt", 0L),
        )
    }

private fun JSONArray.toPlaybackProgressEntities(providerIds: Map<String, String>): List<PlaybackProgressEntity> =
    mapObjects { progress ->
        val providerId = providerIds[progress.requiredString("providerStableKey")] ?: return@mapObjects null
        val mediaStableKey = progress.requiredString("mediaStableKey") ?: return@mapObjects null
        val mediaType = progress.optString("mediaType", "MOVIE").ifBlank { "MOVIE" }
        PlaybackProgressEntity(
            id = "$providerId:progress:${mediaType.lowercase(Locale.US)}:$mediaStableKey",
            providerId = providerId,
            mediaType = mediaType,
            mediaId = mediaStableKey,
            mediaStableKey = mediaStableKey,
            isPending = true,
            positionMillis = progress.optLong("positionMillis", 0L),
            durationMillis = progress.optLong("durationMillis", 0L),
            progressPercent = progress.optInt("progressPercent", 0).coerceIn(0, 100),
            isCompleted = progress.optBoolean("isCompleted", false),
            lastWatchedAt = progress.optLong("lastWatchedAt", 0L),
            createdAt = progress.optLong("updatedAt", 0L),
            updatedAt = progress.optLong("updatedAt", 0L),
        )
    }

private fun JSONArray.toChannelHistoryEntities(providerIds: Map<String, String>): List<ChannelHistoryEntity> =
    mapObjects { history ->
        val providerId = providerIds[history.requiredString("providerStableKey")] ?: return@mapObjects null
        val channelStableKey = history.requiredString("channelStableKey") ?: return@mapObjects null
        ChannelHistoryEntity(
            id = "$providerId:history:$channelStableKey",
            providerId = providerId,
            channelId = channelStableKey,
            channelStableKey = channelStableKey,
            isPending = true,
            watchedAt = history.optLong("watchedAt", 0L),
            durationWatchedMillis = history.optLong("durationWatchedMillis", 0L),
            updatedAt = history.optLong("updatedAt", 0L),
        )
    }

private fun JSONArray.toSearchHistoryEntities(now: Long): List<SearchHistoryEntity> =
    (0 until length()).mapNotNull { index ->
        val query = optString(index).trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val normalized = query.lowercase(Locale.GERMAN)
        SearchHistoryEntity(
            id = "search:$normalized",
            query = query,
            normalizedQuery = normalized,
            lastUsedAt = now - index,
            createdAt = now - index,
            updatedAt = now - index,
        )
    }

private fun JSONArray.stableKeyIdMap(): Map<String, String> =
    mapObjects { item ->
        val stableKey = item.requiredString("stableKey") ?: return@mapObjects null
        stableKey to stableKey
    }.toMap()

private inline fun <T> JSONArray.mapObjects(block: (JSONObject) -> T?): List<T> {
    val result = mutableListOf<T>()
    for (index in 0 until length()) {
        val item = optJSONObject(index) ?: continue
        block(item)?.let(result::add)
    }
    return result
}

private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
    for (index in 0 until length()) {
        optJSONObject(index)?.let(block)
    }
}

private fun JSONObject.requiredString(key: String): String? =
    nullableString(key)?.takeIf { it.isNotBlank() }

private fun JSONObject.nullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).trim()

private fun String.toStorageProviderType(): String =
    when (this) {
        "M3u", PROVIDER_TYPE_M3U -> PROVIDER_TYPE_M3U
        "Xtream", PROVIDER_TYPE_XTREAM -> PROVIDER_TYPE_XTREAM
        else -> PROVIDER_TYPE_M3U
    }

private fun sourceConfigKeyFor(providerStableKey: String): String =
    "provider:$providerStableKey:credentials"

private fun epgSourceConfigKeyFor(epgSourceStableKey: String): String =
    "epg-source:$epgSourceStableKey:url"

private const val PROVIDER_TYPE_M3U = "M3U"
private const val PROVIDER_TYPE_XTREAM = "XTREAM"
private const val PROVIDER_STATUS_ACTIVE = "ACTIVE"
private const val PROVIDER_STATUS_CREDENTIALS_REQUIRED = "CREDENTIALS_REQUIRED"
private const val CATEGORY_TYPE_LIVE = "LIVE"
private const val FIELD_M3U_URL = "m3u_url"
private const val FIELD_XTREAM_SERVER_URL = "xtream_server_url"
private const val FIELD_XTREAM_USERNAME = "xtream_username"
private const val FIELD_XTREAM_PASSWORD = "xtream_password"
