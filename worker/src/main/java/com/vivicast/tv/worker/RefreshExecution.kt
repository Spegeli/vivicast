package com.vivicast.tv.worker

import com.vivicast.tv.core.cache.MediaCacheKey
import com.vivicast.tv.core.cache.MediaCacheStore
import com.vivicast.tv.core.cache.MediaCacheType
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.security.SecureKey
import com.vivicast.tv.core.security.SecureValueStore
import com.vivicast.tv.data.epg.EpgImportRepository
import com.vivicast.tv.data.epg.EpgStreamSource
import com.vivicast.tv.data.media.CatalogImportRepository
import com.vivicast.tv.data.media.XtreamCatalog
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.isAutomaticallyRefreshable
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderStatus
import com.vivicast.tv.domain.model.ProviderType
import com.vivicast.tv.iptv.m3u.M3uParser
import com.vivicast.tv.iptv.xmltv.XmltvChannel
import com.vivicast.tv.iptv.xmltv.XmltvDocument
import com.vivicast.tv.iptv.xmltv.XmltvParser
import com.vivicast.tv.iptv.xmltv.XmltvProgram
import com.vivicast.tv.iptv.xmltv.XmltvStreamHandler
import com.vivicast.tv.iptv.xtream.XtreamClient
import com.vivicast.tv.iptv.xtream.XtreamCredentials
import com.vivicast.tv.iptv.xtream.XtreamHttpException
import com.vivicast.tv.iptv.xtream.XtreamParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

class DefaultRefreshWorkerRunner(
    private val orchestrator: MaintenanceRefreshOrchestrator,
    private val playlistRefresher: PlaylistRefresher,
    private val epgRefresher: EpgRefresher,
    private val seriesDetailsRefresher: SeriesDetailsRefresher,
    private val scheduler: RefreshWorkScheduler,
    private val refreshEpgOnPlaylistChangeProvider: suspend () -> Boolean = { true },
    // Per-item refresh results feed the diagnostics log — the primary signal for "import failed" reports.
    private val diagnostics: RefreshDiagnostics = NoOpRefreshDiagnostics,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RefreshWorkerRunner {
    override suspend fun runMaintenanceRefresh(): RefreshWorkerResult =
        // The maintenance orchestrator only runs logos + cache now (playlists/EPG are per-item workers),
        // so there is nothing to inspect on success — just succeed.
        runCancellableCatching { orchestrator.refresh() }
            .fold(
                onSuccess = { RefreshWorkerResult.Success },
                onFailure = { RefreshWorkerResult.Retry },
            )

    override suspend fun runPlaylistRefresh(providerId: String?): RefreshWorkerResult {
        val target = providerId?.takeIf { it.isNotBlank() }?.let(::PlaylistRefreshTarget)
            ?: return RefreshWorkerResult.Failure
        val startedAt = clock()
        return runCancellableCatching { playlistRefresher.refresh(target) }
            .fold(
                onSuccess = { outcome ->
                    if (outcome.success) {
                        if (outcome.needsSeriesDetailsRefresh) {
                            scheduler.enqueueSeriesDetailsRefresh(target.providerId)
                        }
                        // "Refresh EPG on playlist change": the catalog just changed, so re-refresh the
                        // EPG sources assigned to this provider (mapping re-runs against the fresh
                        // channels). epgSourceIds is empty when the provider has no assigned source, so
                        // an unassigned source is never refreshed by this trigger.
                        if (refreshEpgOnPlaylistChangeProvider()) {
                            outcome.epgSourceIds.forEach { scheduler.enqueueEpgRefresh(it) }
                        }
                        recordRefresh(
                            RefreshDiagnosticType.PlaylistRefreshSucceeded, target.providerId, startedAt,
                            extra = mapOf(
                                "channels" to outcome.channelsImported.toString(),
                                "skipped" to outcome.skippedEntries.toString(),
                            ),
                        )
                    } else if (!outcome.skipped) {
                        recordRefresh(RefreshDiagnosticType.PlaylistRefreshFailed, target.providerId, startedAt)
                    }
                    when {
                        outcome.success -> RefreshWorkerResult.Success
                        // Skipped (already refreshing in-process): the in-flight run covers it — a Retry
                        // would just re-fetch the whole playlist a few seconds later.
                        outcome.skipped -> RefreshWorkerResult.Success
                        else -> RefreshWorkerResult.Retry
                    }
                },
                onFailure = { error ->
                    recordRefresh(RefreshDiagnosticType.PlaylistRefreshFailed, target.providerId, startedAt, error)
                    RefreshWorkerResult.Retry
                },
            )
    }

    override suspend fun runSeriesDetailsRefresh(providerId: String?): RefreshWorkerResult {
        val id = providerId?.takeIf { it.isNotBlank() } ?: return RefreshWorkerResult.Failure
        return runCancellableCatching { seriesDetailsRefresher.refresh(id) }
            .fold(
                onSuccess = { if (it.success) RefreshWorkerResult.Success else RefreshWorkerResult.Retry },
                onFailure = { RefreshWorkerResult.Retry },
            )
    }

    override suspend fun runEpgRefresh(epgSourceId: String?): RefreshWorkerResult {
        val target = epgSourceId?.takeIf { it.isNotBlank() }?.let(::EpgRefreshTarget)
            ?: return RefreshWorkerResult.Failure
        val startedAt = clock()
        return runCancellableCatching { epgRefresher.refresh(target) }
            .fold(
                onSuccess = { outcome ->
                    if (outcome.success) {
                        recordRefresh(
                            RefreshDiagnosticType.EpgRefreshSucceeded, target.epgSourceId, startedAt,
                            extra = mapOf(
                                "channels" to outcome.channels.toString(),
                                "mappingsAdded" to outcome.mappingsAdded.toString(),
                                "mappingsUpdated" to outcome.mappingsUpdated.toString(),
                                "programs" to outcome.programsImported.toString(),
                            ),
                        )
                    } else if (!outcome.skipped) {
                        recordRefresh(RefreshDiagnosticType.EpgRefreshFailed, target.epgSourceId, startedAt)
                    }
                    when {
                        outcome.success -> RefreshWorkerResult.Success
                        outcome.skipped -> RefreshWorkerResult.Success
                        else -> RefreshWorkerResult.Retry
                    }
                },
                onFailure = { error ->
                    recordRefresh(RefreshDiagnosticType.EpgRefreshFailed, target.epgSourceId, startedAt, error)
                    RefreshWorkerResult.Retry
                },
            )
    }

    // Records one per-item refresh result. `target` is a provider/EPG-source id (not a URL/credential);
    // durations + error reason are what a "why did the refresh fail" report needs. `extra` carries the
    // sanitized import counts (channels/mappings/programs) on success. URLs in the error text are redacted
    // downstream (StoreRefreshDiagnostics → DiagnosticsSanitizer).
    private fun recordRefresh(
        type: RefreshDiagnosticType,
        target: String,
        startedAt: Long,
        error: Throwable? = null,
        extra: Map<String, String> = emptyMap(),
    ) {
        val metadata = buildMap {
            put("target", target)
            put("durationMs", (clock() - startedAt).toString())
            error?.let { put("error", it.message ?: it::class.java.simpleName) }
            putAll(extra)
        }
        diagnostics.record(RefreshDiagnosticEvent(type, type.name, metadata))
    }
}

// Status + sanitized import counts returned by the per-type playlist refresh, so the caller can both
// set the provider status and record the counts in the diagnostics event.
private data class PlaylistImportSummary(
    val status: ProviderStatus,
    val channels: Int,
    val skipped: Int,
)

class DefaultPlaylistRefresher(
    private val providerRepository: ProviderRepository,
    private val catalogImportRepository: CatalogImportRepository,
    private val m3uParser: M3uParser,
    private val textFetcher: TextFetcher,
    private val xtreamClient: XtreamClient,
    private val xtreamParser: XtreamParser,
    private val epgSourceReader: EpgSourceReader,
    private val refreshRunGuard: RefreshRunGuard = RefreshRunGuard(),
) : PlaylistRefresher {
    override suspend fun refresh(target: PlaylistRefreshTarget): PlaylistRefreshOutcome {
        val provider = providerRepository.getProvider(target.providerId)
            ?: return PlaylistRefreshOutcome(target.providerId, success = false, epgSourceIds = emptyList())
        if (!provider.isActive || provider.status == ProviderStatus.Disabled) {
            return PlaylistRefreshOutcome(provider.id, success = true, epgSourceIds = emptyList())
        }
        if (!refreshRunGuard.tryEnter(provider.id)) {
            return PlaylistRefreshOutcome(provider.id, success = false, epgSourceIds = emptyList(), skipped = true)
        }

        try {
            providerRepository.setProviderStatus(provider.id, ProviderStatus.Refreshing)
            return runCatching {
                val summary = when (val credentials = providerRepository.getCredentials(provider.id)) {
                    is ProviderCredentials.M3u -> refreshM3uProvider(provider, credentials)
                    is ProviderCredentials.Xtream -> refreshXtreamProvider(provider, credentials)
                    null -> throw RefreshAuthenticationException("Provider credentials are missing.")
                }
                providerRepository.setProviderStatus(provider.id, summary.status)
                PlaylistRefreshOutcome(
                    providerId = provider.id,
                    success = true,
                    epgSourceIds = epgSourceReader.getActiveSourceIdsForProvider(provider.id),
                    needsSeriesDetailsRefresh = provider.type == ProviderType.Xtream && provider.includeSeries,
                    channelsImported = summary.channels,
                    skippedEntries = summary.skipped,
                )
            }.getOrElse { error ->
                // A cancelled refresh (worker stopped) is not a provider error — propagate without
                // overwriting the status, so the provider isn't left marked as failed on shutdown.
                if (error is CancellationException) throw error
                providerRepository.setProviderStatus(provider.id, error.toProviderStatus())
                throw error
            }
        } finally {
            refreshRunGuard.exit(provider.id)
        }
    }

    private suspend fun refreshM3uProvider(provider: Provider, credentials: ProviderCredentials.M3u): PlaylistImportSummary {
        require(provider.type == ProviderType.M3u) { "Provider type mismatch." }
        val source = if (credentials.sourceMode.isAutomaticallyRefreshable) {
            textFetcher.fetch(
                credentials.url ?: throw RefreshAuthenticationException("M3U URL is missing."),
                userAgent = provider.userAgent,
            )
        } else {
            providerRepository.getProviderM3uInlineContent(provider.id)
                ?: throw RefreshAuthenticationException("M3U content is missing.")
        }
        val playlist = m3uParser.parse(source)
        if (playlist.channels.isEmpty()) {
            throw RefreshImportException("M3U playlist contains no importable entries.")
        }
        val result = catalogImportRepository.importM3uCatalog(provider.id, playlist)
        return PlaylistImportSummary(
            status = if (result.skippedEntries > 0) ProviderStatus.ActiveWithPartialErrors else ProviderStatus.Active,
            channels = result.channelsAdded + result.channelsUpdated,
            skipped = result.skippedEntries,
        )
    }

    private suspend fun refreshXtreamProvider(provider: Provider, credentials: ProviderCredentials.Xtream): PlaylistImportSummary {
        require(provider.type == ProviderType.Xtream) { "Provider type mismatch." }
        val xtreamCredentials = XtreamCredentials(
            serverUrl = credentials.serverUrl,
            username = credentials.username,
            password = credentials.password,
            userAgent = provider.userAgent,
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
            // Season/episode detail (one getSeriesInfo per series) is intentionally NOT fetched here —
            // it is scheduled as a separate background job (SeriesDetailsRefreshWorker) so the heavy
            // per-series loop does not block/kill the main catalog refresh. See needsSeriesDetailsRefresh.
            seriesInfos = emptyList(),
        )
        val result = catalogImportRepository.importXtreamCatalog(provider.id, catalog)
        // Best-effort account snapshot (expiry + max connections). A user_info failure must NOT
        // fail the catalog refresh that already succeeded, so it is isolated in runCancellableCatching.
        runCancellableCatching {
            val userInfo = xtreamParser.parseUserInfo(xtreamClient.getUserInfo(xtreamCredentials))
            providerRepository.updateXtreamAccountInfo(
                providerId = provider.id,
                expiresAtMillis = userInfo.expiresAtSeconds?.let { it * MILLIS_PER_SECOND },
                maxConnections = userInfo.maxConnections,
            )
        }
        // Xtream import has no per-entry "skipped" concept; report live channels only for the count.
        return PlaylistImportSummary(
            status = ProviderStatus.Active,
            channels = result.channels.added + result.channels.updated,
            skipped = 0,
        )
    }
}

private const val MILLIS_PER_SECOND = 1_000L

class DefaultSeriesDetailsRefresher(
    private val providerRepository: ProviderRepository,
    private val catalogImportRepository: CatalogImportRepository,
    private val xtreamClient: XtreamClient,
    private val xtreamParser: XtreamParser,
) : SeriesDetailsRefresher {
    override suspend fun refresh(providerId: String): SeriesDetailsRefreshOutcome {
        val provider = providerRepository.getProvider(providerId)
            ?: return SeriesDetailsRefreshOutcome(providerId, success = false)
        if (provider.type != ProviderType.Xtream || !provider.includeSeries ||
            !provider.isActive || provider.status == ProviderStatus.Disabled
        ) {
            return SeriesDetailsRefreshOutcome(providerId, success = true)
        }
        val credentials = providerRepository.getCredentials(providerId) as? ProviderCredentials.Xtream
            ?: return SeriesDetailsRefreshOutcome(providerId, success = false)
        val xtreamCredentials = XtreamCredentials(
            serverUrl = credentials.serverUrl,
            username = credentials.username,
            password = credentials.password,
            userAgent = provider.userAgent,
        )
        val seriesItems = xtreamParser.parseSeries(xtreamClient.getSeries(xtreamCredentials))
        // Full per cycle: fetch getSeriesInfo for every series (sequential — 1-connection-safe), then
        // import all season/episode detail in one reconciling call.
        val seriesInfos = seriesItems.map { series ->
            xtreamParser.parseSeriesInfo(series.remoteId, xtreamClient.getSeriesInfo(xtreamCredentials, series.remoteId))
        }
        catalogImportRepository.importXtreamSeriesDetails(providerId, seriesInfos)
        return SeriesDetailsRefreshOutcome(providerId, success = true)
    }
}

class RoomEpgSourceReader(
    private val database: VivicastDatabase,
    private val secureValueStore: SecureValueStore,
) : EpgSourceReader {
    private val epgDao = database.epgDao()

    override suspend fun getActiveSourceIdsForProvider(providerId: String): List<String> =
        epgDao.getProviderEpgSources(providerId)
            .mapNotNull { link -> epgDao.getEpgSource(link.epgSourceId)?.takeIf { it.isActive }?.id }

    override suspend fun getActiveSource(epgSourceId: String): ResolvedEpgSource? {
        val source = epgDao.getEpgSource(epgSourceId)?.takeIf { it.isActive } ?: return null
        val url = secureValueStore.read(SecureKey(source.sourceConfigKey))?.takeIf { it.isNotBlank() } ?: return null
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
    private val epgStreamSource: EpgStreamSource,
    private val xmltvParser: XmltvParser,
    private val epgImportRepository: EpgImportRepository,
    private val epgPastRetentionDaysProvider: suspend () -> Int = { 1 },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val refreshRunGuard: RefreshRunGuard = RefreshRunGuard(),
) : EpgRefresher {
    override suspend fun refresh(target: EpgRefreshTarget): EpgRefreshOutcome {
        val source = epgSourceReader.getActiveSource(target.epgSourceId)
            ?: return EpgRefreshOutcome(target.epgSourceId, success = false)
        if (!refreshRunGuard.tryEnter(source.id)) {
            return EpgRefreshOutcome(target.epgSourceId, success = false, skipped = true)
        }

        try {
            epgImportRepository.setEpgSourceRefreshing(source.id, refreshing = true)
            // Stream the feed (SAX, constant memory) and collect channels + programmes instead of
            // building a DOM tree of the whole document. gzip bodies are decompressed by the parser.
            val channels = ArrayList<XmltvChannel>()
            val programs = ArrayList<XmltvProgram>()
            var skippedPrograms = 0
            epgStreamSource.open(source.url) { input ->
                skippedPrograms = xmltvParser.parseStreaming(
                    input,
                    object : XmltvStreamHandler {
                        override fun onChannel(channel: XmltvChannel) { channels.add(channel) }
                        override fun onProgram(program: XmltvProgram) { programs.add(program) }
                    },
                )
            }
            if (programs.isEmpty()) {
                throw RefreshImportException("XMLTV document contains no importable programs.")
            }
            val document = XmltvDocument(channels = channels, programs = programs, skippedPrograms = skippedPrograms)
            // Channel count + last-refresh timestamp are feed properties, so record them for every source
            // with a valid feed — even one linked to no active provider (its programmes just stay unmapped).
            epgImportRepository.markEpgSourceRefreshed(
                sourceId = source.id,
                refreshedAt = clock(),
                channelCount = document.channels.size,
                programCount = document.programs.size,
            )
            val activeProviderIds = source.providerIds.filter { providerId ->
                providerRepository.getProvider(providerId)?.isActive == true
            }
            var mappingsAdded = 0
            var mappingsUpdated = 0
            var programsImported = 0
            activeProviderIds.forEach { providerId ->
                val importResult = epgImportRepository.importXmltv(providerId, source.id, document)
                mappingsAdded += importResult.mappingsAdded
                mappingsUpdated += importResult.mappingsUpdated
                programsImported += importResult.programsImported
            }
            epgImportRepository.cleanupProgramsOutsideRetention(
                nowMillis = clock(),
                pastDays = epgPastRetentionDaysProvider(),
            )
            return EpgRefreshOutcome(
                target.epgSourceId,
                success = true,
                channels = document.channels.size,
                mappingsAdded = mappingsAdded,
                mappingsUpdated = mappingsUpdated,
                programsImported = programsImported,
            )
        } finally {
            epgImportRepository.setEpgSourceRefreshing(source.id, refreshing = false)
            refreshRunGuard.exit(source.id)
        }
    }
}

class RefreshRunGuard {
    private val runningKeys = mutableSetOf<String>()

    fun tryEnter(key: String): Boolean =
        synchronized(runningKeys) { runningKeys.add(key) }

    fun exit(key: String) {
        synchronized(runningKeys) { runningKeys.remove(key) }
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
    suspend fun fetch(url: String, userAgent: String? = null): String
}

class OkHttpTextFetcher(
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TextFetcher {
    override suspend fun fetch(url: String, userAgent: String?): String =
        withContext(ioDispatcher) {
            withFetchRetry {
                val request = Request.Builder()
                    .url(url)
                    .applyUserAgent(userAgent)
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw RefreshHttpException(response.code)
                    }
                    val body = response.body
                    // Fast-fail on declared size; -1 (unknown) skips this branch.
                    if (body.contentLength() > MAX_M3U_URL_BYTES) {
                        throw M3uSourceTooLargeException(body.contentLength())
                    }
                    val source = body.source()
                    // Buffer at most cap+1 bytes; if the source can serve that many, the
                    // body exceeds the cap and we abort before downloading the rest.
                    if (source.request(MAX_M3U_URL_BYTES + 1)) {
                        throw M3uSourceTooLargeException(source.buffer.size)
                    }
                    body.string()
                }
            }
        }
}

/**
 * Streams an EPG source body (constant memory) instead of buffering it as a String like [TextFetcher].
 * EPG feeds are commonly tens of MB, so this enforces a much larger cap ([MAX_EPG_URL_BYTES]) and hands
 * the raw body stream to the caller, who parses it incrementally (gzip is detected by the parser).
 */
class OkHttpEpgStreamSource(
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : EpgStreamSource {
    override suspend fun open(url: String, block: (InputStream) -> Unit) {
        withContext(ioDispatcher) {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RefreshHttpException(response.code)
                }
                val body = response.body
                // Fast-fail on declared size; -1 (unknown) skips this branch and the stream cap catches it.
                if (body.contentLength() > MAX_EPG_URL_BYTES) {
                    throw EpgSourceTooLargeException(body.contentLength())
                }
                block(CappedInputStream(body.byteStream(), MAX_EPG_URL_BYTES))
            }
        }
    }
}

/** Aborts mid-stream once more than [cap] bytes have been read, so an unbounded body can't exhaust heap. */
private class CappedInputStream(delegate: InputStream, private val cap: Long) : FilterInputStream(delegate) {
    private var readSoFar = 0L

    override fun read(): Int = super.read().also { if (it >= 0) count(1) }

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        super.read(b, off, len).also { if (it > 0) count(it.toLong()) }

    private fun count(bytes: Long) {
        readSoFar += bytes
        if (readSoFar > cap) throw EpgSourceTooLargeException(readSoFar)
    }
}

/** URL EPG document exceeds [MAX_EPG_URL_BYTES]; aborted before fully downloading. */
class EpgSourceTooLargeException(
    val byteCount: Long,
) : RuntimeException("EPG document exceeds the $MAX_EPG_URL_BYTES byte limit (got $byteCount).")

/** Sets a per-request User-Agent header when [userAgent] is non-blank; the OkHttp interceptor supplies
 * the global User-Agent for requests without one. */
private fun Request.Builder.applyUserAgent(userAgent: String?): Request.Builder =
    userAgent?.trim()?.takeIf { it.isNotEmpty() }?.let { header("User-Agent", it) } ?: this

interface BinaryFetcher {
    suspend fun fetch(url: String): ByteArray
}

class OkHttpBinaryFetcher(
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BinaryFetcher {
    override suspend fun fetch(url: String): ByteArray =
        withContext(ioDispatcher) {
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
    private val mediaImageRefreshSource: MediaImageRefreshSource,
    private val mediaCacheStore: MediaCacheStore,
    private val binaryFetcher: BinaryFetcher,
) : LogoRefresher {
    override suspend fun refreshLogos() {
        mediaImageRefreshSource.collectImageTargets().forEach { target ->
            val key = MediaCacheKey(
                type = target.type,
                ownerId = target.ownerId,
                sourceUrl = target.sourceUrl,
            )
            if (!mediaCacheStore.hasEntry(key)) {
                runCancellableCatching {
                    mediaCacheStore.put(key, binaryFetcher.fetch(target.sourceUrl))
                }
            }
        }
    }
}

interface MediaImageRefreshSource {
    suspend fun collectImageTargets(): List<MediaImageRefreshTarget>
}

data class MediaImageRefreshTarget(
    val type: MediaCacheType,
    val ownerId: String,
    val sourceUrl: String,
)

class RoomMediaImageRefreshSource(
    private val database: VivicastDatabase,
) : MediaImageRefreshSource {
    override suspend fun collectImageTargets(): List<MediaImageRefreshTarget> {
        val catalogDao = database.catalogDao()
        return buildList {
            catalogDao.getChannelsWithLogoUrls().forEach { row ->
                addImageTarget(MediaCacheType.ChannelLogo, row.channel.id, row.effectiveLogoUrl)
            }
            catalogDao.getMoviesWithImageUrls().forEach { movie ->
                addImageTarget(MediaCacheType.MoviePoster, movie.id, movie.posterUrl)
                addImageTarget(MediaCacheType.MovieBackdrop, movie.id, movie.backdropUrl)
            }
            catalogDao.getSeriesWithImageUrls().forEach { series ->
                addImageTarget(MediaCacheType.SeriesPoster, series.id, series.posterUrl)
                addImageTarget(MediaCacheType.SeriesBackdrop, series.id, series.backdropUrl)
            }
            catalogDao.getSeasonsWithImageUrls().forEach { season ->
                addImageTarget(MediaCacheType.SeasonImage, season.id, season.posterUrl)
            }
            catalogDao.getEpisodesWithImageUrls().forEach { episode ->
                addImageTarget(MediaCacheType.EpisodeImage, episode.id, episode.thumbnailUrl)
            }
        }
    }

    private fun MutableList<MediaImageRefreshTarget>.addImageTarget(
        type: MediaCacheType,
        ownerId: String,
        sourceUrl: String?,
    ) {
        val normalizedUrl = sourceUrl?.trim()?.takeIf { it.isNotBlank() } ?: return
        add(MediaImageRefreshTarget(type = type, ownerId = ownerId, sourceUrl = normalizedUrl))
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

class RefreshAuthenticationException(message: String) : RuntimeException(message)

class RefreshImportException(message: String) : RuntimeException(message)

class RefreshHttpException(
    val statusCode: Int,
) : RuntimeException("Refresh request failed with HTTP status $statusCode.")

/** URL M3U playlist exceeds [MAX_M3U_URL_BYTES]; aborted before fully downloading. */
class M3uSourceTooLargeException(
    val byteCount: Long,
) : RuntimeException("M3U playlist exceeds the $MAX_M3U_URL_BYTES byte limit (got $byteCount).")

// ponytail: 32MB cap + full-in-RAM parse; upgrade to a streaming parser if mega-playlists OOM.
private const val MAX_M3U_URL_BYTES: Long = 32L * 1024 * 1024

// EPG/XMLTV feeds are commonly tens of MB (compressed or not); a much larger cap than M3U, still a guard
// against a runaway/unbounded body. Streaming keeps memory flat regardless of the actual size.
private const val MAX_EPG_URL_BYTES: Long = 200L * 1024 * 1024

private const val MAX_FETCH_ATTEMPTS = 2
private const val RETRY_BASE_DELAY_MS = 750L
private const val RETRY_MAX_DELAY_MS = 3_000L

/**
 * Retries transient failures only: [IOException] (timeouts/connect) and HTTP 5xx.
 * 4xx (credentials/not-found) and non-transient errors (e.g. too-large) propagate
 * immediately. CancellationException is neither type, so it is never swallowed.
 */
private suspend fun <T> withFetchRetry(block: suspend () -> T): T {
    var attempt = 1
    while (true) {
        try {
            return block()
        } catch (e: IOException) {
            if (attempt >= MAX_FETCH_ATTEMPTS) throw e
        } catch (e: RefreshHttpException) {
            if (e.statusCode < 500 || attempt >= MAX_FETCH_ATTEMPTS) throw e
        }
        delay((RETRY_BASE_DELAY_MS * attempt).coerceAtMost(RETRY_MAX_DELAY_MS))
        attempt++
    }
}

private fun Throwable.toProviderStatus(): ProviderStatus =
    when (this) {
        is RefreshAuthenticationException -> ProviderStatus.CredentialsRequired
        is RefreshImportException -> ProviderStatus.ConnectionError
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
