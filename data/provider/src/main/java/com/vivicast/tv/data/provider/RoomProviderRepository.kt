package com.vivicast.tv.data.provider

import androidx.room.withTransaction
import com.vivicast.tv.core.cache.M3uStreamReferenceStore
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.model.ProviderEntity
import com.vivicast.tv.core.security.SecureKey
import com.vivicast.tv.core.security.SecureValueStore
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderStatus
import com.vivicast.tv.domain.model.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomProviderRepository(
    private val database: VivicastDatabase,
    private val secureValueStore: SecureValueStore,
    private val m3uFileSourceStore: DiskM3uFileSourceStore,
    private val m3uStreamReferenceStore: M3uStreamReferenceStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ProviderRepository {
    private val providerDao = database.providerDao()
    private val catalogDao = database.catalogDao()
    private val providerCategorySettingsDao = database.providerCategorySettingsDao()
    private val epgDao = database.epgDao()
    private val favoritesDao = database.favoritesDao()
    private val playbackDao = database.playbackDao()
    private val androidTvSearchDao = database.androidTvSearchDao()

    override fun observeProviders(): Flow<List<Provider>> =
        providerDao.observeProviders().map { providers -> providers.map { it.toDomain() } }

    override suspend fun getProvider(providerId: String): Provider? =
        providerDao.getProvider(providerId)?.toDomain()

    override suspend fun getCredentials(providerId: String): ProviderCredentials? {
        val provider = providerDao.getProvider(providerId) ?: return null
        return when (provider.type.toProviderType()) {
            ProviderType.M3u -> {
                val sourceMode = provider.sourceConfigKey.readM3uSourceMode()
                when (sourceMode) {
                    M3uSourceMode.Url -> {
                        val url = secureValueStore.read(provider.secureKey(FIELD_M3U_URL)) ?: return null
                        ProviderCredentials.M3u(url = url, sourceMode = sourceMode)
                    }
                    // Content is read on demand via getProviderM3uInlineContent, not here.
                    M3uSourceMode.File -> ProviderCredentials.M3u(sourceMode = sourceMode)
                }
            }

            ProviderType.Xtream -> {
                val serverUrl = secureValueStore.read(provider.secureKey(FIELD_XTREAM_SERVER_URL)) ?: return null
                val username = secureValueStore.read(provider.secureKey(FIELD_XTREAM_USERNAME)) ?: return null
                val password = secureValueStore.read(provider.secureKey(FIELD_XTREAM_PASSWORD)) ?: return null
                ProviderCredentials.Xtream(
                    serverUrl = serverUrl,
                    username = username,
                    password = password,
                )
            }
        }
    }

    override suspend fun getProviderM3uInlineContent(providerId: String): String? =
        m3uFileSourceStore.read(providerId)

    override suspend fun createProvider(request: ProviderCreateRequest): ProviderSaveResult {
        validateProviderOptions(request.includeLiveTv, request.includeMovies, request.includeSeries)
        val providerId = UUID.randomUUID().toString()
        val sourceConfigKey = sourceConfigKeyFor(providerId)
        val now = clock()
        val name = request.name.trim()
        require(name.isNotEmpty()) { "Provider name must not be blank." }
        require(!hasDuplicateName(name = name, excludeProviderId = null)) { "Provider name must be unique." }

        writeCredentialsForCreate(providerId, sourceConfigKey, request)

        // A File playlist has no fetchable source, so it must never be auto-refreshed: force the interval
        // OFF and app-start refresh off (the editor hides both for File, so they'd otherwise keep defaults
        // and the scheduler — which only checks interval>0 / refreshOnAppStartEnabled — would re-import the
        // unchanged file on every start/interval). Manual save/replace still re-imports directly.
        val fileModeM3u = isFileModeM3u(request.type, sourceConfigKey, request.m3uSourceMode)
        val provider = Provider(
            id = providerId,
            stableKey = providerId,
            name = name,
            type = request.type,
            sourceConfigKey = sourceConfigKey,
            isActive = true,
            status = ProviderStatus.Active,
            includeLiveTv = request.includeLiveTv,
            includeMovies = request.includeMovies,
            includeSeries = request.includeSeries,
            refreshIntervalHours = if (fileModeM3u) {
                REFRESH_INTERVAL_OFF
            } else {
                request.refreshIntervalHours.coerceIn(MIN_REFRESH_INTERVAL_HOURS, MAX_REFRESH_INTERVAL_HOURS)
            },
            logoPriority = normalizeLogoPriority(request.logoPriority),
            xtreamOutputFormat = normalizeXtreamOutputFormat(request.xtreamOutputFormat),
            createdAt = now,
            updatedAt = now,
            userAgent = request.userAgent?.trim()?.takeIf { it.isNotEmpty() },
            refreshOnAppStartEnabled = !fileModeM3u && request.refreshOnAppStartEnabled,
        )
        database.withTransaction {
            providerDao.upsertProvider(provider.toEntity())
            androidTvSearchDao.rebuildEntries()
        }
        return ProviderSaveResult(provider = provider, hasDuplicateName = false)
    }

    override suspend fun updateProvider(request: ProviderUpdateRequest): ProviderSaveResult {
        validateProviderOptions(request.includeLiveTv, request.includeMovies, request.includeSeries)
        val existing = providerDao.getProvider(request.providerId)?.toDomain()
            ?: error("Provider not found: ${request.providerId}")
        val name = request.name.trim()
        require(name.isNotEmpty()) { "Provider name must not be blank." }
        require(!hasDuplicateName(name = name, excludeProviderId = existing.id)) { "Provider name must be unique." }

        val providerId = existing.id
        val sourceConfigKey = existing.sourceConfigKey
        val oldType = existing.type
        val newType = request.type
        // Read the OLD M3U mode before any write overwrites the mode secret.
        val oldMode = if (oldType == ProviderType.M3u) sourceConfigKey.readM3uSourceMode() else null
        val newMode = if (newType == ProviderType.M3u) {
            request.m3uSourceMode ?: oldMode ?: M3uSourceMode.Url
        } else {
            null
        }
        val typeChanged = oldType != newType
        // A source switch = type change, or an M3U URL↔File mode change. Either replaces the whole catalog.
        val sourceSwitched = typeChanged || (oldType == ProviderType.M3u && newType == ProviderType.M3u && oldMode != newMode)

        // On a type switch, write the NEW type's credentials first — a blank switch fails here (requireFresh)
        // BEFORE anything is deleted, so a failed switch never leaves the provider sourceless. Then drop the
        // OLD type's now-dead secrets/file (the writer only touches the new type's fields).
        writeCredentialsForUpdate(providerId, sourceConfigKey, newType, request, requireFreshSource = typeChanged)
        if (typeChanged) deleteOldTypeSecrets(providerId, sourceConfigKey, newType)

        val fileModeM3u = newType == ProviderType.M3u && newMode == M3uSourceMode.File
        val leavingXtream = oldType == ProviderType.Xtream && newType != ProviderType.Xtream
        val updated = existing.copy(
            type = newType,
            name = name,
            includeLiveTv = request.includeLiveTv,
            includeMovies = request.includeMovies,
            includeSeries = request.includeSeries,
            refreshIntervalHours = if (fileModeM3u) {
                REFRESH_INTERVAL_OFF
            } else {
                request.refreshIntervalHours.coerceIn(MIN_REFRESH_INTERVAL_HOURS, MAX_REFRESH_INTERVAL_HOURS)
            },
            logoPriority = normalizeLogoPriority(request.logoPriority),
            xtreamOutputFormat = normalizeXtreamOutputFormat(request.xtreamOutputFormat),
            updatedAt = clock(),
            userAgent = request.userAgent?.trim()?.takeIf { it.isNotEmpty() },
            refreshOnAppStartEnabled = !fileModeM3u && request.refreshOnAppStartEnabled,
            // Leaving Xtream: the cached account snapshot no longer applies (it is shown by value, not by
            // type), so clear it — otherwise an M3U provider would keep showing an expiry/connection badge.
            xtreamExpiresAtMillis = if (leavingXtream) null else existing.xtreamExpiresAtMillis,
            xtreamMaxConnections = if (leavingXtream) null else existing.xtreamMaxConnections,
        )
        database.withTransaction {
            // The old catalog belongs to the old source; clear it (and its favorites/history/progress/EPG
            // mappings) so the post-save refresh rebuilds cleanly instead of resolving stale streams.
            if (sourceSwitched) clearProviderCatalog(providerId)
            providerDao.upsertProvider(updated.toEntity())
            androidTvSearchDao.rebuildEntries()
        }
        // Stream references live outside Room; clear them on any source switch (the M3U refresh rewrites
        // them, an Xtream refresh never touches them). Also see deleteProvider.
        if (sourceSwitched) m3uStreamReferenceStore.deleteProviderReferences(providerId)
        return ProviderSaveResult(
            provider = updated,
            hasDuplicateName = false,
            switchedFromType = if (typeChanged) oldType else null,
        )
    }

    /**
     * Clears everything derived from a provider's source (catalog + its favorites/history/progress and the
     * channel-level EPG mappings/programs), but KEEPS the provider row and its EPG-source assignments — a
     * source switch replaces the content, not the provider or which EPG sources feed it.
     */
    /** After a type switch, remove the OLD type's now-dead secrets/file (the new type's were just written). */
    private suspend fun deleteOldTypeSecrets(providerId: String, sourceConfigKey: String, newType: ProviderType) {
        when (newType) {
            ProviderType.M3u -> deleteXtreamCredentials(sourceConfigKey)
            ProviderType.Xtream -> {
                secureValueStore.delete(sourceConfigKey.secureKey(FIELD_M3U_URL))
                secureValueStore.delete(sourceConfigKey.secureKey(FIELD_M3U_SOURCE_MODE))
                secureValueStore.delete(sourceConfigKey.secureKey(FIELD_M3U_INLINE_CONTENT))
                m3uFileSourceStore.delete(providerId)
            }
        }
    }

    private suspend fun clearProviderCatalog(providerId: String) {
        catalogDao.deleteCatalogForProvider(providerId)
        epgDao.deleteProgramsForProvider(providerId)
        epgDao.deleteMappingsForProvider(providerId)
        favoritesDao.deleteFavoritesForProvider(providerId)
        playbackDao.deleteProgressForProvider(providerId)
        playbackDao.deleteHistoryForProvider(providerId)
    }

    override suspend fun saveProvider(provider: Provider) {
        database.withTransaction {
            providerDao.upsertProvider(provider.toEntity())
            androidTvSearchDao.rebuildEntries()
        }
    }

    override suspend fun setProviderStatus(providerId: String, status: ProviderStatus) {
        database.withTransaction {
            // updatedAt nur bei erfolgreichem Refresh bumpen; Start (Refreshing) und Fehler-Status
            // lassen "Updated" unverändert.
            if (status == ProviderStatus.Active || status == ProviderStatus.ActiveWithPartialErrors) {
                // Only a successful refresh reaches this branch (the refresher's success status), so stamp
                // lastRefreshAt here too. Enabling a provider goes through providerDao directly and does not.
                providerDao.setProviderRefreshed(providerId, status.storageValue, clock())
            } else {
                providerDao.setProviderStatusOnly(providerId, status.storageValue)
            }
            androidTvSearchDao.rebuildEntries()
        }
    }

    override suspend fun setProviderActive(providerId: String, isActive: Boolean) {
        database.withTransaction {
            providerDao.setProviderActive(providerId, isActive, clock())
            androidTvSearchDao.rebuildEntries()
        }
    }

    override suspend fun setProviderEnabled(providerId: String, isEnabled: Boolean) {
        val now = clock()
        database.withTransaction {
            providerDao.setProviderActive(providerId, isEnabled, now)
            providerDao.setProviderStatus(
                providerId = providerId,
                status = if (isEnabled) ProviderStatus.Active.storageValue else ProviderStatus.Disabled.storageValue,
                updatedAt = now,
            )
            androidTvSearchDao.rebuildEntries()
        }
    }

    override suspend fun updateXtreamAccountInfo(providerId: String, expiresAtMillis: Long?, maxConnections: Int?) {
        providerDao.updateXtreamAccountInfo(providerId, expiresAtMillis, maxConnections)
    }

    override suspend fun deleteProvider(providerId: String) {
        val existing = providerDao.getProvider(providerId)
        database.withTransaction {
            catalogDao.deleteCatalogForProvider(providerId)
            // Stage hygiene: drop any half-staged import rows for this provider so a delete racing a
            // mid-stage refresh leaves no orphan *_stage rows.
            catalogDao.clearChannelsStage(providerId)
            catalogDao.clearMoviesStage(providerId)
            catalogDao.clearSeriesStage(providerId)
            catalogDao.clearEpisodesStage(providerId)
            epgDao.clearProgramsStageForProvider(providerId)
            providerCategorySettingsDao.deleteSettingsForProvider(providerId)
            epgDao.deleteProgramsForProvider(providerId)
            epgDao.deleteMappingsForProvider(providerId)
            epgDao.deleteProviderEpgSources(providerId)
            favoritesDao.deleteFavoritesForProvider(providerId)
            playbackDao.deleteProgressForProvider(providerId)
            playbackDao.deleteHistoryForProvider(providerId)
            providerDao.deleteProvider(providerId)
            // Only this provider's rows changed — a targeted delete beats a full all-providers rebuild.
            existing?.let { androidTvSearchDao.deleteEntriesForProvider(it.stableKey) }
        }
        existing?.sourceConfigKey?.let { deleteCredentials(it) }
        m3uFileSourceStore.delete(providerId)
        // Stream references are not covered by deleteCatalogForProvider (they live outside Room); clear them
        // too so a deleted provider leaves no orphaned stream URLs behind.
        m3uStreamReferenceStore.deleteProviderReferences(providerId)
    }

    private suspend fun hasDuplicateName(name: String, excludeProviderId: String?): Boolean =
        providerDao.getProviders().any { provider ->
            provider.id != excludeProviderId && provider.name.equals(name, ignoreCase = true)
        }

    private suspend fun writeCredentialsForCreate(
        providerId: String,
        sourceConfigKey: String,
        request: ProviderCreateRequest,
    ) {
        when (request.type) {
            ProviderType.M3u -> {
                writeM3uCredentials(providerId, sourceConfigKey, request.m3uSourceMode, request.m3uUrl, request.m3uContent)
                deleteXtreamCredentials(sourceConfigKey)
            }

            ProviderType.Xtream -> {
                secureValueStore.write(sourceConfigKey.secureKey(FIELD_XTREAM_SERVER_URL), request.xtreamServerUrl.requireSecret("Xtream server URL"))
                secureValueStore.write(sourceConfigKey.secureKey(FIELD_XTREAM_USERNAME), request.xtreamUsername.requireSecret("Xtream username"))
                secureValueStore.write(sourceConfigKey.secureKey(FIELD_XTREAM_PASSWORD), request.xtreamPassword.requireSecret("Xtream password"))
                secureValueStore.delete(sourceConfigKey.secureKey(FIELD_M3U_URL))
                m3uFileSourceStore.delete(providerId)
            }
        }
    }

    private suspend fun writeCredentialsForUpdate(
        providerId: String,
        sourceConfigKey: String,
        type: ProviderType,
        request: ProviderUpdateRequest,
        requireFreshSource: Boolean,
    ) {
        when (type) {
            ProviderType.M3u -> {
                val sourceMode = request.m3uSourceMode ?: sourceConfigKey.readM3uSourceMode()
                // requireFreshSource (type switch): the new source is mandatory — writeM3uCredentials
                // requires the URL/content, so a blank switch fails loudly instead of leaving the provider
                // sourceless (the old source was already wiped). Same-type edit: only rewrite when the user
                // actually supplied a new source, else keep the stored one.
                if (requireFreshSource || request.m3uSourceMode != null ||
                    !request.m3uUrl.isNullOrBlank() || !request.m3uContent.isNullOrBlank()
                ) {
                    writeM3uCredentials(providerId, sourceConfigKey, sourceMode, request.m3uUrl, request.m3uContent)
                }
            }

            ProviderType.Xtream ->
                if (requireFreshSource) {
                    // Type switch to Xtream: all three fields are mandatory (mirrors create), so an
                    // incomplete switch cannot leave the provider with partial/no credentials.
                    secureValueStore.write(sourceConfigKey.secureKey(FIELD_XTREAM_SERVER_URL), request.xtreamServerUrl.requireSecret("Xtream server URL"))
                    secureValueStore.write(sourceConfigKey.secureKey(FIELD_XTREAM_USERNAME), request.xtreamUsername.requireSecret("Xtream username"))
                    secureValueStore.write(sourceConfigKey.secureKey(FIELD_XTREAM_PASSWORD), request.xtreamPassword.requireSecret("Xtream password"))
                } else {
                    request.xtreamServerUrl.writeIfPresent(sourceConfigKey, FIELD_XTREAM_SERVER_URL)
                    request.xtreamUsername.writeIfPresent(sourceConfigKey, FIELD_XTREAM_USERNAME)
                    request.xtreamPassword.writeIfPresent(sourceConfigKey, FIELD_XTREAM_PASSWORD)
                }
        }
    }

    private suspend fun String?.writeIfPresent(sourceConfigKey: String, field: String) {
        val secret = this?.takeIf { it.isNotBlank() } ?: return
        secureValueStore.write(sourceConfigKey.secureKey(field), secret.trim())
    }

    private suspend fun writeM3uCredentials(
        providerId: String,
        sourceConfigKey: String,
        sourceMode: M3uSourceMode,
        m3uUrl: String?,
        m3uContent: String?,
    ) {
        secureValueStore.write(sourceConfigKey.secureKey(FIELD_M3U_SOURCE_MODE), sourceMode.name)
        when (sourceMode) {
            M3uSourceMode.Url -> {
                secureValueStore.write(sourceConfigKey.secureKey(FIELD_M3U_URL), m3uUrl.requireSecret("M3U URL"))
                secureValueStore.delete(sourceConfigKey.secureKey(FIELD_M3U_INLINE_CONTENT))
                // Switching a File playlist to URL: the stored file is no longer used.
                m3uFileSourceStore.delete(providerId)
            }
            M3uSourceMode.File -> {
                val content = m3uContent.requireSecret("M3U content")
                require(content.length <= MAX_M3U_INLINE_SOURCE_CHARS) { "M3U content is too large." }
                m3uFileSourceStore.write(providerId, content)
                secureValueStore.delete(sourceConfigKey.secureKey(FIELD_M3U_INLINE_CONTENT))
                secureValueStore.delete(sourceConfigKey.secureKey(FIELD_M3U_URL))
            }
        }
    }

    private suspend fun String.readM3uSourceMode(): M3uSourceMode =
        when (secureValueStore.read(secureKey(FIELD_M3U_SOURCE_MODE)).orEmpty()) {
            // Legacy "Clipboard" providers stored inline content identical to File.
            "File", "Clipboard" -> M3uSourceMode.File
            else -> M3uSourceMode.Url
        }

    /** True for a File-mode M3U playlist (no fetchable source ⇒ never auto-refreshed). */
    private suspend fun isFileModeM3u(
        type: ProviderType,
        sourceConfigKey: String,
        requestedMode: M3uSourceMode?,
    ): Boolean =
        type == ProviderType.M3u && (requestedMode ?: sourceConfigKey.readM3uSourceMode()) == M3uSourceMode.File

    private suspend fun deleteCredentials(sourceConfigKey: String) {
        secureValueStore.delete(sourceConfigKey.secureKey(FIELD_M3U_URL))
        secureValueStore.delete(sourceConfigKey.secureKey(FIELD_M3U_INLINE_CONTENT))
        secureValueStore.delete(sourceConfigKey.secureKey(FIELD_M3U_SOURCE_MODE))
        deleteXtreamCredentials(sourceConfigKey)
    }

    private suspend fun deleteXtreamCredentials(sourceConfigKey: String) {
        secureValueStore.delete(sourceConfigKey.secureKey(FIELD_XTREAM_SERVER_URL))
        secureValueStore.delete(sourceConfigKey.secureKey(FIELD_XTREAM_USERNAME))
        secureValueStore.delete(sourceConfigKey.secureKey(FIELD_XTREAM_PASSWORD))
    }

    private fun validateProviderOptions(includeLiveTv: Boolean, includeMovies: Boolean, includeSeries: Boolean) {
        require(includeLiveTv || includeMovies || includeSeries) { "At least one provider content type must be selected." }
    }
}

private fun ProviderEntity.toDomain(): Provider =
    Provider(
        id = id,
        stableKey = stableKey,
        name = name,
        type = type.toProviderType(),
        sourceConfigKey = sourceConfigKey,
        isActive = isActive,
        status = status.toProviderStatus(),
        includeLiveTv = includeLiveTv,
        includeMovies = includeMovies,
        includeSeries = includeSeries,
        refreshIntervalHours = refreshIntervalHours,
        logoPriority = logoPriority,
        xtreamOutputFormat = xtreamOutputFormat,
        createdAt = createdAt,
        updatedAt = updatedAt,
        xtreamExpiresAtMillis = xtreamExpiresAtMillis,
        xtreamMaxConnections = xtreamMaxConnections,
        userAgent = userAgent,
        refreshOnAppStartEnabled = refreshOnAppStartEnabled,
        lastRefreshAt = lastRefreshAt,
    )

private fun Provider.toEntity(): ProviderEntity =
    ProviderEntity(
        id = id,
        stableKey = stableKey,
        name = name,
        type = type.storageValue,
        sourceConfigKey = sourceConfigKey,
        isActive = isActive,
        status = status.storageValue,
        includeLiveTv = includeLiveTv,
        includeMovies = includeMovies,
        includeSeries = includeSeries,
        refreshIntervalHours = refreshIntervalHours,
        logoPriority = logoPriority,
        xtreamOutputFormat = xtreamOutputFormat,
        createdAt = createdAt,
        updatedAt = updatedAt,
        xtreamExpiresAtMillis = xtreamExpiresAtMillis,
        xtreamMaxConnections = xtreamMaxConnections,
        userAgent = userAgent,
        refreshOnAppStartEnabled = refreshOnAppStartEnabled,
        lastRefreshAt = lastRefreshAt,
    )

private val ProviderType.storageValue: String
    get() = when (this) {
        ProviderType.M3u -> "M3U"
        ProviderType.Xtream -> "XTREAM"
    }

private fun String.toProviderType(): ProviderType =
    when (this) {
        "M3U" -> ProviderType.M3u
        "XTREAM" -> ProviderType.Xtream
        else -> ProviderType.M3u
    }

private val ProviderStatus.storageValue: String
    get() = when (this) {
        ProviderStatus.Active -> "ACTIVE"
        ProviderStatus.ActiveWithPartialErrors -> "ACTIVE_WITH_PARTIAL_ERRORS"
        ProviderStatus.Refreshing -> "REFRESHING"
        ProviderStatus.ConnectionError -> "CONNECTION_ERROR"
        ProviderStatus.InvalidCredentials -> "INVALID_CREDENTIALS"
        ProviderStatus.Expired -> "EXPIRED"
        ProviderStatus.Disabled -> "DISABLED"
        ProviderStatus.CredentialsRequired -> "CREDENTIALS_REQUIRED"
    }

private fun String.toProviderStatus(): ProviderStatus =
    when (this) {
        "ACTIVE" -> ProviderStatus.Active
        "ACTIVE_WITH_PARTIAL_ERRORS" -> ProviderStatus.ActiveWithPartialErrors
        "REFRESHING" -> ProviderStatus.Refreshing
        "CONNECTION_ERROR" -> ProviderStatus.ConnectionError
        "INVALID_CREDENTIALS" -> ProviderStatus.InvalidCredentials
        "EXPIRED" -> ProviderStatus.Expired
        "DISABLED" -> ProviderStatus.Disabled
        "CREDENTIALS_REQUIRED" -> ProviderStatus.CredentialsRequired
        else -> ProviderStatus.Disabled
    }

private fun ProviderEntity.secureKey(field: String): SecureKey =
    sourceConfigKey.secureKey(field)

private fun String.secureKey(field: String): SecureKey =
    SecureKey("$this:$field")

private fun String?.requireSecret(label: String): String {
    val value = this?.trim()
    require(!value.isNullOrBlank()) { "$label must not be blank." }
    return value
}

private fun sourceConfigKeyFor(providerId: String): String =
    "$SOURCE_CONFIG_KEY_PREFIX$providerId:credentials"

private const val SOURCE_CONFIG_KEY_PREFIX = "provider:"
private const val FIELD_M3U_URL = "m3u_url"
private const val FIELD_M3U_INLINE_CONTENT = "m3u_inline_content"
private const val FIELD_M3U_SOURCE_MODE = "m3u_source_mode"
private const val FIELD_XTREAM_SERVER_URL = "xtream_server_url"
private const val FIELD_XTREAM_USERNAME = "xtream_username"
private const val FIELD_XTREAM_PASSWORD = "xtream_password"
private const val MIN_REFRESH_INTERVAL_HOURS = 0
private const val MAX_REFRESH_INTERVAL_HOURS = 168
