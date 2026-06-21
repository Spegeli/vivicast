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

    override fun observeProviders(): Flow<List<Provider>> =
        providerDao.observeProviders().map { providers -> providers.map { it.toDomain() } }

    override suspend fun getProvider(providerId: String): Provider? =
        providerDao.getProvider(providerId)?.toDomain()

    override suspend fun getCredentials(providerId: String): ProviderCredentials? {
        val provider = providerDao.getProvider(providerId) ?: return null
        return when (provider.type.toProviderType()) {
            ProviderType.M3u -> {
                val url = secureValueStore.read(provider.secureKey(FIELD_M3U_URL)) ?: return null
                ProviderCredentials.M3u(url = url)
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
        val credentialsKey = credentialsKeyFor(providerId)
        val now = clock()
        val name = request.name.trim()
        require(name.isNotEmpty()) { "Provider name must not be blank." }

        writeCredentialsForCreate(credentialsKey, request)

        val provider = Provider(
            id = providerId,
            name = name,
            type = request.type,
            credentialsKey = credentialsKey,
            isActive = true,
            status = ProviderStatus.Active,
            includeLiveTv = request.includeLiveTv,
            includeMovies = request.includeMovies,
            includeSeries = request.includeSeries,
            refreshIntervalHours = request.refreshIntervalHours.coerceIn(MIN_REFRESH_INTERVAL_HOURS, MAX_REFRESH_INTERVAL_HOURS),
            logoPriority = DEFAULT_LOGO_PRIORITY,
            createdAt = now,
            updatedAt = now,
        )
        val hasDuplicateName = hasDuplicateName(name = name, excludeProviderId = null)
        providerDao.upsertProvider(provider.toEntity())
        return ProviderSaveResult(provider = provider, hasDuplicateName = hasDuplicateName)
    }

    override suspend fun updateProvider(request: ProviderUpdateRequest): ProviderSaveResult {
        validateProviderOptions(request.includeLiveTv, request.includeMovies, request.includeSeries)
        val existing = providerDao.getProvider(request.providerId)?.toDomain()
            ?: error("Provider not found: ${request.providerId}")
        val name = request.name.trim()
        require(name.isNotEmpty()) { "Provider name must not be blank." }

        writeCredentialsForUpdate(existing.credentialsKey, existing.type, request)

        val updated = existing.copy(
            name = name,
            includeLiveTv = request.includeLiveTv,
            includeMovies = request.includeMovies,
            includeSeries = request.includeSeries,
            refreshIntervalHours = request.refreshIntervalHours.coerceIn(MIN_REFRESH_INTERVAL_HOURS, MAX_REFRESH_INTERVAL_HOURS),
            updatedAt = clock(),
        )
        val hasDuplicateName = hasDuplicateName(name = name, excludeProviderId = existing.id)
        providerDao.upsertProvider(updated.toEntity())
        return ProviderSaveResult(provider = updated, hasDuplicateName = hasDuplicateName)
    }

    override suspend fun saveProvider(provider: Provider) {
        providerDao.upsertProvider(provider.toEntity())
    }

    override suspend fun setProviderStatus(providerId: String, status: ProviderStatus) {
        providerDao.setProviderStatus(providerId, status.storageValue, clock())
    }

    override suspend fun setProviderActive(providerId: String, isActive: Boolean) {
        providerDao.setProviderActive(providerId, isActive, clock())
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
        }
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
        }
        existing?.credentialsKey?.let { deleteCredentials(it) }
    }

    private suspend fun hasDuplicateName(name: String, excludeProviderId: String?): Boolean =
        providerDao.getProviders().any { provider ->
            provider.id != excludeProviderId && provider.name.equals(name, ignoreCase = true)
        }

    private suspend fun writeCredentialsForCreate(credentialsKey: String, request: ProviderCreateRequest) {
        when (request.type) {
            ProviderType.M3u -> {
                val url = request.m3uUrl.requireSecret("M3U URL")
                secureValueStore.write(credentialsKey.secureKey(FIELD_M3U_URL), url)
                deleteXtreamCredentials(credentialsKey)
            }

            ProviderType.Xtream -> {
                secureValueStore.write(credentialsKey.secureKey(FIELD_XTREAM_SERVER_URL), request.xtreamServerUrl.requireSecret("Xtream server URL"))
                secureValueStore.write(credentialsKey.secureKey(FIELD_XTREAM_USERNAME), request.xtreamUsername.requireSecret("Xtream username"))
                secureValueStore.write(credentialsKey.secureKey(FIELD_XTREAM_PASSWORD), request.xtreamPassword.requireSecret("Xtream password"))
                secureValueStore.delete(credentialsKey.secureKey(FIELD_M3U_URL))
            }
        }
    }

    private suspend fun writeCredentialsForUpdate(
        credentialsKey: String,
        type: ProviderType,
        request: ProviderUpdateRequest,
    ) {
        when (type) {
            ProviderType.M3u -> {
                request.m3uUrl.writeIfPresent(credentialsKey, FIELD_M3U_URL)
            }

            ProviderType.Xtream -> {
                request.xtreamServerUrl.writeIfPresent(credentialsKey, FIELD_XTREAM_SERVER_URL)
                request.xtreamUsername.writeIfPresent(credentialsKey, FIELD_XTREAM_USERNAME)
                request.xtreamPassword.writeIfPresent(credentialsKey, FIELD_XTREAM_PASSWORD)
            }
        }
    }

    private suspend fun String?.writeIfPresent(credentialsKey: String, field: String) {
        val secret = this?.takeIf { it.isNotBlank() } ?: return
        secureValueStore.write(credentialsKey.secureKey(field), secret.trim())
    }

    private suspend fun deleteCredentials(credentialsKey: String) {
        secureValueStore.delete(credentialsKey.secureKey(FIELD_M3U_URL))
        deleteXtreamCredentials(credentialsKey)
    }

    private suspend fun deleteXtreamCredentials(credentialsKey: String) {
        secureValueStore.delete(credentialsKey.secureKey(FIELD_XTREAM_SERVER_URL))
        secureValueStore.delete(credentialsKey.secureKey(FIELD_XTREAM_USERNAME))
        secureValueStore.delete(credentialsKey.secureKey(FIELD_XTREAM_PASSWORD))
    }

    private fun validateProviderOptions(includeLiveTv: Boolean, includeMovies: Boolean, includeSeries: Boolean) {
        require(includeLiveTv || includeMovies || includeSeries) { "At least one provider content type must be selected." }
    }
}

private fun ProviderEntity.toDomain(): Provider =
    Provider(
        id = id,
        name = name,
        type = type.toProviderType(),
        credentialsKey = credentialsKey,
        isActive = isActive,
        status = status.toProviderStatus(),
        includeLiveTv = includeLiveTv,
        includeMovies = includeMovies,
        includeSeries = includeSeries,
        refreshIntervalHours = refreshIntervalHours,
        logoPriority = logoPriority,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun Provider.toEntity(): ProviderEntity =
    ProviderEntity(
        id = id,
        name = name,
        type = type.storageValue,
        credentialsKey = credentialsKey,
        isActive = isActive,
        status = status.storageValue,
        includeLiveTv = includeLiveTv,
        includeMovies = includeMovies,
        includeSeries = includeSeries,
        refreshIntervalHours = refreshIntervalHours,
        logoPriority = logoPriority,
        createdAt = createdAt,
        updatedAt = updatedAt,
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
        ProviderStatus.Refreshing -> "REFRESHING"
        ProviderStatus.ConnectionError -> "CONNECTION_ERROR"
        ProviderStatus.InvalidCredentials -> "INVALID_CREDENTIALS"
        ProviderStatus.Expired -> "EXPIRED"
        ProviderStatus.Disabled -> "DISABLED"
    }

private fun String.toProviderStatus(): ProviderStatus =
    when (this) {
        "ACTIVE" -> ProviderStatus.Active
        "REFRESHING" -> ProviderStatus.Refreshing
        "CONNECTION_ERROR" -> ProviderStatus.ConnectionError
        "INVALID_CREDENTIALS" -> ProviderStatus.InvalidCredentials
        "EXPIRED" -> ProviderStatus.Expired
        "DISABLED" -> ProviderStatus.Disabled
        else -> ProviderStatus.Disabled
    }

private fun ProviderEntity.secureKey(field: String): SecureKey =
    credentialsKey.secureKey(field)

private fun String.secureKey(field: String): SecureKey =
    SecureKey("$this:$field")

private fun String?.requireSecret(label: String): String {
    val value = this?.trim()
    require(!value.isNullOrBlank()) { "$label must not be blank." }
    return value
}

private fun credentialsKeyFor(providerId: String): String =
    "$CREDENTIALS_KEY_PREFIX$providerId:credentials"

private const val CREDENTIALS_KEY_PREFIX = "provider:"
private const val FIELD_M3U_URL = "m3u_url"
private const val FIELD_XTREAM_SERVER_URL = "xtream_server_url"
private const val FIELD_XTREAM_USERNAME = "xtream_username"
private const val FIELD_XTREAM_PASSWORD = "xtream_password"
private const val DEFAULT_LOGO_PRIORITY = "provider"
private const val MIN_REFRESH_INTERVAL_HOURS = 1
private const val MAX_REFRESH_INTERVAL_HOURS = 168
