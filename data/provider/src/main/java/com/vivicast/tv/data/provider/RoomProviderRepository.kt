package com.vivicast.tv.data.provider

import androidx.room.withTransaction
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
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ProviderRepository {
    private val providerDao = database.providerDao()
    private val catalogDao = database.catalogDao()
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
                    M3uSourceMode.File -> {
                        ProviderCredentials.M3u(
                            sourceMode = sourceMode,
                            inlineContent = TransientM3uSourceStore.read(provider.id),
                        )
                    }
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

    override suspend fun createProvider(request: ProviderCreateRequest): ProviderSaveResult {
        validateProviderOptions(request.includeLiveTv, request.includeMovies, request.includeSeries)
        val providerId = UUID.randomUUID().toString()
        val sourceConfigKey = sourceConfigKeyFor(providerId)
        val now = clock()
        val name = request.name.trim()
        require(name.isNotEmpty()) { "Provider name must not be blank." }
        require(!hasDuplicateName(name = name, excludeProviderId = null)) { "Provider name must be unique." }

        writeCredentialsForCreate(sourceConfigKey, request)

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
            refreshIntervalHours = request.refreshIntervalHours.coerceIn(MIN_REFRESH_INTERVAL_HOURS, MAX_REFRESH_INTERVAL_HOURS),
            logoPriority = normalizeLogoPriority(request.logoPriority),
            xtreamOutputFormat = normalizeXtreamOutputFormat(request.xtreamOutputFormat),
            createdAt = now,
            updatedAt = now,
            userAgent = request.userAgent?.trim()?.takeIf { it.isNotEmpty() },
            refreshOnAppStartEnabled = request.refreshOnAppStartEnabled,
        )
        database.withTransaction {
            providerDao.upsertProvider(provider.toEntity())
            androidTvSearchDao.rebuildEntries()
        }
        if (request.type == ProviderType.M3u && !request.m3uSourceMode.isAutomaticallyRefreshable) {
            request.m3uContent?.takeIf { it.isNotBlank() }?.let { TransientM3uSourceStore.put(providerId, it) }
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

        writeCredentialsForUpdate(existing.sourceConfigKey, existing.type, request)

        val updated = existing.copy(
            name = name,
            includeLiveTv = request.includeLiveTv,
            includeMovies = request.includeMovies,
            includeSeries = request.includeSeries,
            refreshIntervalHours = request.refreshIntervalHours.coerceIn(MIN_REFRESH_INTERVAL_HOURS, MAX_REFRESH_INTERVAL_HOURS),
            logoPriority = normalizeLogoPriority(request.logoPriority),
            xtreamOutputFormat = normalizeXtreamOutputFormat(request.xtreamOutputFormat),
            updatedAt = clock(),
            userAgent = request.userAgent?.trim()?.takeIf { it.isNotEmpty() },
            refreshOnAppStartEnabled = request.refreshOnAppStartEnabled,
        )
        database.withTransaction {
            providerDao.upsertProvider(updated.toEntity())
            androidTvSearchDao.rebuildEntries()
        }
        if (existing.type == ProviderType.M3u && request.m3uSourceMode != null && !request.m3uSourceMode.isAutomaticallyRefreshable) {
            request.m3uContent?.takeIf { it.isNotBlank() }?.let { TransientM3uSourceStore.put(existing.id, it) }
        }
        return ProviderSaveResult(provider = updated, hasDuplicateName = false)
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
            epgDao.deleteProgramsForProvider(providerId)
            epgDao.deleteMappingsForProvider(providerId)
            epgDao.deleteProviderEpgSources(providerId)
            favoritesDao.deleteFavoritesForProvider(providerId)
            playbackDao.deleteProgressForProvider(providerId)
            playbackDao.deleteHistoryForProvider(providerId)
            providerDao.deleteProvider(providerId)
            androidTvSearchDao.rebuildEntries()
        }
        existing?.sourceConfigKey?.let { deleteCredentials(it) }
    }

    private suspend fun hasDuplicateName(name: String, excludeProviderId: String?): Boolean =
        providerDao.getProviders().any { provider ->
            provider.id != excludeProviderId && provider.name.equals(name, ignoreCase = true)
        }

    private suspend fun writeCredentialsForCreate(sourceConfigKey: String, request: ProviderCreateRequest) {
        when (request.type) {
            ProviderType.M3u -> {
                writeM3uCredentials(sourceConfigKey, request.m3uSourceMode, request.m3uUrl, request.m3uContent)
                deleteXtreamCredentials(sourceConfigKey)
            }

            ProviderType.Xtream -> {
                secureValueStore.write(sourceConfigKey.secureKey(FIELD_XTREAM_SERVER_URL), request.xtreamServerUrl.requireSecret("Xtream server URL"))
                secureValueStore.write(sourceConfigKey.secureKey(FIELD_XTREAM_USERNAME), request.xtreamUsername.requireSecret("Xtream username"))
                secureValueStore.write(sourceConfigKey.secureKey(FIELD_XTREAM_PASSWORD), request.xtreamPassword.requireSecret("Xtream password"))
                secureValueStore.delete(sourceConfigKey.secureKey(FIELD_M3U_URL))
            }
        }
    }

    private suspend fun writeCredentialsForUpdate(
        sourceConfigKey: String,
        type: ProviderType,
        request: ProviderUpdateRequest,
    ) {
        when (type) {
            ProviderType.M3u -> {
                val sourceMode = request.m3uSourceMode ?: sourceConfigKey.readM3uSourceMode()
                if (request.m3uSourceMode != null || !request.m3uUrl.isNullOrBlank() || !request.m3uContent.isNullOrBlank()) {
                    writeM3uCredentials(sourceConfigKey, sourceMode, request.m3uUrl, request.m3uContent)
                }
            }

            ProviderType.Xtream -> {
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
            }
            M3uSourceMode.File -> {
                val content = m3uContent.requireSecret("M3U content")
                require(content.length <= MAX_M3U_INLINE_SOURCE_CHARS) { "M3U content is too large." }
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
