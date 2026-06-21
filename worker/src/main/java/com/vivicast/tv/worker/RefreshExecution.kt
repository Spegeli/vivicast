package com.vivicast.tv.worker

import com.vivicast.tv.core.cache.MediaCacheKey
import com.vivicast.tv.core.cache.MediaCacheStore
import com.vivicast.tv.core.cache.MediaCacheType
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.security.SecureKey
import com.vivicast.tv.core.security.SecureValueStore
import com.vivicast.tv.data.epg.EpgImportRepository
import com.vivicast.tv.data.media.CatalogImportRepository
import com.vivicast.tv.data.media.XtreamCatalog
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderStatus
import com.vivicast.tv.domain.model.ProviderType
import com.vivicast.tv.iptv.m3u.M3uParser
import com.vivicast.tv.iptv.xmltv.XmltvParser
import com.vivicast.tv.iptv.xtream.XtreamClient
import com.vivicast.tv.iptv.xtream.XtreamCredentials
import com.vivicast.tv.iptv.xtream.XtreamHttpException
import com.vivicast.tv.iptv.xtream.XtreamParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class DefaultRefreshWorkerRunner(
    private val orchestrator: GlobalRefreshOrchestrator,
    private val playlistRefresher: PlaylistRefresher,
    private val epgRefresher: EpgRefresher,
    private val logoRefresher: LogoRefresher,
    private val cacheCleaner: CacheCleaner,
) : RefreshWorkerRunner {
    override suspend fun runGlobalRefresh(): RefreshWorkerResult =
        runCatching { orchestrator.refresh() }
            .fold(
                onSuccess = { report ->
                    if (report.playlistsSucceeded == 0 && report.playlistsFailed > 0) {
                        RefreshWorkerResult.Retry
                    } else {
                        RefreshWorkerResult.Success
                    }
                },
                onFailure = { RefreshWorkerResult.Retry },
            )

    override suspend fun runPlaylistRefresh(providerId: String?): RefreshWorkerResult {
        val target = providerId?.takeIf { it.isNotBlank() }?.let(::PlaylistRefreshTarget)
            ?: return RefreshWorkerResult.Failure
        return runCatching { playlistRefresher.refresh(target) }
            .fold(
                onSuccess = { if (it.success) RefreshWorkerResult.Success else RefreshWorkerResult.Retry },
                onFailure = { RefreshWorkerResult.Retry },
            )
    }

    override suspend fun runEpgRefresh(epgSourceId: String?): RefreshWorkerResult {
        val target = epgSourceId?.takeIf { it.isNotBlank() }?.let(::EpgRefreshTarget)
            ?: return RefreshWorkerResult.Failure
        return runCatching { epgRefresher.refresh(target) }
            .fold(
                onSuccess = { if (it.success) RefreshWorkerResult.Success else RefreshWorkerResult.Retry },
                onFailure = { RefreshWorkerResult.Retry },
            )
    }

    override suspend fun runLogoRefresh(): RefreshWorkerResult =
        runCatching { logoRefresher.refreshLogos() }
            .fold(onSuccess = { RefreshWorkerResult.Success }, onFailure = { RefreshWorkerResult.Retry })

    override suspend fun runCacheCleanup(): RefreshWorkerResult =
        runCatching { cacheCleaner.cleanup() }
            .fold(onSuccess = { RefreshWorkerResult.Success }, onFailure = { RefreshWorkerResult.Retry })
}

class ActiveProviderPlaylistSource(
    private val providerRepository: ProviderRepository,
) : PlaylistRefreshSource {
    override suspend fun collectDuePlaylists(): List<PlaylistRefreshTarget> =
        providerRepository.observeProviders()
            .first()
            .filter { it.isActive && it.status != ProviderStatus.Disabled }
            .map { PlaylistRefreshTarget(providerId = it.id) }
}

class DefaultPlaylistRefresher(
    private val providerRepository: ProviderRepository,
    private val catalogImportRepository: CatalogImportRepository,
    private val m3uParser: M3uParser,
    private val textFetcher: TextFetcher,
    private val xtreamClient: XtreamClient,
    private val xtreamParser: XtreamParser,
    private val epgSourceReader: EpgSourceReader,
) : PlaylistRefresher {
    override suspend fun refresh(target: PlaylistRefreshTarget): PlaylistRefreshOutcome {
        val provider = providerRepository.getProvider(target.providerId)
            ?: return PlaylistRefreshOutcome(target.providerId, success = false, epgSourceIds = emptyList())
        if (!provider.isActive || provider.status == ProviderStatus.Disabled) {
            return PlaylistRefreshOutcome(provider.id, success = true, epgSourceIds = emptyList())
        }

        providerRepository.setProviderStatus(provider.id, ProviderStatus.Refreshing)
        return runCatching {
            when (val credentials = providerRepository.getCredentials(provider.id)) {
                is ProviderCredentials.M3u -> refreshM3uProvider(provider, credentials)
                is ProviderCredentials.Xtream -> refreshXtreamProvider(provider, credentials)
                null -> throw RefreshAuthenticationException("Provider credentials are missing.")
            }
            providerRepository.setProviderStatus(provider.id, ProviderStatus.Active)
            PlaylistRefreshOutcome(
                providerId = provider.id,
                success = true,
                epgSourceIds = epgSourceReader.getActiveSourceIdsForProvider(provider.id),
            )
        }.getOrElse { error ->
            providerRepository.setProviderStatus(provider.id, error.toProviderStatus())
            throw error
        }
    }

    private suspend fun refreshM3uProvider(provider: Provider, credentials: ProviderCredentials.M3u) {
        require(provider.type == ProviderType.M3u) { "Provider type mismatch." }
        val playlist = m3uParser.parse(textFetcher.fetch(credentials.url))
        catalogImportRepository.importM3uLiveChannels(provider.id, playlist)
    }

    private suspend fun refreshXtreamProvider(provider: Provider, credentials: ProviderCredentials.Xtream) {
        require(provider.type == ProviderType.Xtream) { "Provider type mismatch." }
        val xtreamCredentials = XtreamCredentials(
            serverUrl = credentials.serverUrl,
            username = credentials.username,
            password = credentials.password,
        )
        val seriesItems = if (provider.includeSeries) {
            xtreamParser.parseSeries(xtreamClient.getSeries(xtreamCredentials))
        } else {
            emptyList()
        }

        val catalog = XtreamCatalog(
            liveCategories = if (provider.includeLiveTv) {
                xtreamParser.parseCategories(xtreamClient.getLiveCategories(xtreamCredentials))
            } else {
                emptyList()
            },
            liveStreams = if (provider.includeLiveTv) {
                xtreamParser.parseLiveStreams(xtreamClient.getLiveStreams(xtreamCredentials))
            } else {
                emptyList()
            },
            vodCategories = if (provider.includeMovies) {
                xtreamParser.parseCategories(xtreamClient.getVodCategories(xtreamCredentials))
            } else {
                emptyList()
            },
            vodItems = if (provider.includeMovies) {
                xtreamParser.parseVodItems(xtreamClient.getVodStreams(xtreamCredentials))
            } else {
                emptyList()
            },
            seriesCategories = if (provider.includeSeries) {
                xtreamParser.parseCategories(xtreamClient.getSeriesCategories(xtreamCredentials))
            } else {
                emptyList()
            },
            seriesItems = seriesItems,
            seriesInfos = seriesItems.map { series ->
                xtreamParser.parseSeriesInfo(series.remoteId, xtreamClient.getSeriesInfo(xtreamCredentials, series.remoteId))
            },
        )
        catalogImportRepository.importXtreamCatalog(provider.id, catalog)
    }
}

class RoomEpgSourceReader(
    private val database: VivicastDatabase,
    private val secureValueStore: SecureValueStore,
) : EpgSourceReader, EpgSourceResolver {
    private val epgDao = database.epgDao()

    override suspend fun collectRequiredSources(playlistOutcomes: List<PlaylistRefreshOutcome>): List<EpgRefreshTarget> =
        playlistOutcomes
            .filter { it.success }
            .flatMap { it.epgSourceIds }
            .distinct()
            .map(::EpgRefreshTarget)

    override suspend fun getActiveSourceIdsForProvider(providerId: String): List<String> =
        epgDao.getProviderEpgSources(providerId)
            .mapNotNull { link -> epgDao.getEpgSource(link.epgSourceId)?.takeIf { it.isActive }?.id }

    override suspend fun getActiveSource(epgSourceId: String): ResolvedEpgSource? {
        val source = epgDao.getEpgSource(epgSourceId)?.takeIf { it.isActive } ?: return null
        val url = secureValueStore.read(SecureKey(source.urlKey))?.takeIf { it.isNotBlank() } ?: return null
        val providerIds = epgDao.getProviderEpgSourcesForSource(source.id)
            .map { it.providerId }
            .distinct()
        return ResolvedEpgSource(
            id = source.id,
            url = url,
            providerIds = providerIds,
        )
    }
}

class DefaultEpgRefresher(
    private val epgSourceReader: EpgSourceReader,
    private val providerRepository: ProviderRepository,
    private val textFetcher: TextFetcher,
    private val xmltvParser: XmltvParser,
    private val epgImportRepository: EpgImportRepository,
) : EpgRefresher {
    override suspend fun refresh(target: EpgRefreshTarget): EpgRefreshOutcome {
        val source = epgSourceReader.getActiveSource(target.epgSourceId)
            ?: return EpgRefreshOutcome(target.epgSourceId, success = false)
        val document = xmltvParser.parse(textFetcher.fetch(source.url))
        val activeProviderIds = source.providerIds.filter { providerId ->
            providerRepository.getProvider(providerId)?.isActive == true
        }
        activeProviderIds.forEach { providerId ->
            epgImportRepository.importXmltv(providerId, source.id, document)
        }
        return EpgRefreshOutcome(target.epgSourceId, success = true)
    }
}

interface EpgSourceReader {
    suspend fun getActiveSourceIdsForProvider(providerId: String): List<String>

    suspend fun getActiveSource(epgSourceId: String): ResolvedEpgSource?
}

data class ResolvedEpgSource(
    val id: String,
    val url: String,
    val providerIds: List<String>,
)

interface TextFetcher {
    suspend fun fetch(url: String): String
}

class OkHttpTextFetcher(
    private val client: OkHttpClient,
) : TextFetcher {
    override suspend fun fetch(url: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RefreshHttpException(response.code)
                }
                response.body.string()
            }
        }
}

interface BinaryFetcher {
    suspend fun fetch(url: String): ByteArray
}

class OkHttpBinaryFetcher(
    private val client: OkHttpClient,
) : BinaryFetcher {
    override suspend fun fetch(url: String): ByteArray =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RefreshHttpException(response.code)
                }
                response.body.bytes()
            }
        }
}

class DefaultLogoRefresher(
    private val logoRefreshSource: LogoRefreshSource,
    private val mediaCacheStore: MediaCacheStore,
    private val binaryFetcher: BinaryFetcher,
) : LogoRefresher {
    override suspend fun refreshLogos() {
        logoRefreshSource.collectLogoTargets().forEach { target ->
            val key = MediaCacheKey(
                type = MediaCacheType.ChannelLogo,
                ownerId = target.ownerId,
                sourceUrl = target.logoUrl,
            )
            if (!mediaCacheStore.hasEntry(key)) {
                runCatching {
                    mediaCacheStore.put(key, binaryFetcher.fetch(target.logoUrl))
                }
            }
        }
    }
}

interface LogoRefreshSource {
    suspend fun collectLogoTargets(): List<LogoRefreshTarget>
}

data class LogoRefreshTarget(
    val ownerId: String,
    val logoUrl: String,
)

class RoomLogoRefreshSource(
    private val database: VivicastDatabase,
) : LogoRefreshSource {
    override suspend fun collectLogoTargets(): List<LogoRefreshTarget> =
        database.catalogDao().getChannelsWithLogoUrls().mapNotNull { channel ->
            val logoUrl = channel.logoUrl?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            LogoRefreshTarget(ownerId = channel.id, logoUrl = logoUrl)
        }
}

class DefaultCacheCleaner(
    private val mediaCacheStore: MediaCacheStore,
    private val maxSizeBytesProvider: suspend () -> Long,
) : CacheCleaner {
    override suspend fun cleanup() {
        mediaCacheStore.cleanup(maxSizeBytesProvider())
    }
}

class NoOpEpgMappingApplier : EpgMappingApplier {
    override suspend fun applyMappings(epgOutcomes: List<EpgRefreshOutcome>) = Unit
}

class RefreshAuthenticationException(message: String) : RuntimeException(message)

class RefreshHttpException(
    val statusCode: Int,
) : RuntimeException("Refresh request failed with HTTP status $statusCode.")

private fun Throwable.toProviderStatus(): ProviderStatus =
    when (this) {
        is RefreshAuthenticationException -> ProviderStatus.InvalidCredentials
        is RefreshHttpException -> if (statusCode == 401 || statusCode == 403) {
            ProviderStatus.InvalidCredentials
        } else {
            ProviderStatus.ConnectionError
        }
        is XtreamHttpException -> if (statusCode == 401 || statusCode == 403) {
            ProviderStatus.InvalidCredentials
        } else {
            ProviderStatus.ConnectionError
        }
        is IllegalArgumentException -> ProviderStatus.InvalidCredentials
        else -> ProviderStatus.ConnectionError
    }
