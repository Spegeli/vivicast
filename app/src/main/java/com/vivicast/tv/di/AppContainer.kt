package com.vivicast.tv.di

import android.content.Context
import androidx.work.WorkManager
import com.vivicast.tv.backup.StandardBackupExporter
import com.vivicast.tv.backup.StandardBackupRestorer
import com.vivicast.tv.diagnostics.DiagnosticsStore
import com.vivicast.tv.core.cache.FileMediaCacheStore
import com.vivicast.tv.core.cache.M3uStreamReferenceStore
import com.vivicast.tv.core.cache.MediaCacheStore
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.VivicastDatabaseFactory
import com.vivicast.tv.core.datastore.DataStoreUserPreferencesStore
import com.vivicast.tv.core.datastore.DEFAULT_GLOBAL_USER_AGENT
import com.vivicast.tv.core.datastore.UserPreferencesStore
import com.vivicast.tv.core.network.NetworkClientFactory
import com.vivicast.tv.core.player.DefaultVivicastPlayerController
import com.vivicast.tv.core.player.Media3PlaybackEngine
import com.vivicast.tv.core.player.VivicastPlayerController
import com.vivicast.tv.core.security.AndroidKeystoreSecureValueStore
import com.vivicast.tv.core.security.PinSecurityStateStore
import com.vivicast.tv.core.security.SecureValueStore
import com.vivicast.tv.core.security.SecureValuePinSecurityStateStore
import com.vivicast.tv.data.epg.EpgImportRepository
import com.vivicast.tv.data.epg.EpgSourceRepository
import com.vivicast.tv.data.epg.RoomEpgRepository
import com.vivicast.tv.data.epg.SecureEpgSourceRepository
import com.vivicast.tv.data.favorites.FavoritesRepository
import com.vivicast.tv.data.favorites.RoomFavoritesRepository
import com.vivicast.tv.data.media.CatalogImportRepository
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.media.RoomCatalogImportRepository
import com.vivicast.tv.data.media.RoomMediaRepository
import com.vivicast.tv.data.playback.DefaultPlaybackStreamResolver
import com.vivicast.tv.data.playback.PlaybackProgressRecorder
import com.vivicast.tv.data.playback.PlaybackRepository
import com.vivicast.tv.data.playback.PlaybackRequestFactory
import com.vivicast.tv.data.playback.PlaybackStreamResolver
import com.vivicast.tv.data.playback.RoomPlaybackRepository
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.data.provider.isAutomaticallyRefreshable
import com.vivicast.tv.data.provider.RoomProviderRepository
import com.vivicast.tv.system.AndroidTvWatchNextPublisher
import com.vivicast.tv.system.SystemIntegrationPlaybackRepository
import com.vivicast.tv.system.SystemIntegrationProviderRepository
import com.vivicast.tv.system.WatchNextSynchronizer
import com.vivicast.tv.iptv.m3u.DefaultM3uParser
import com.vivicast.tv.iptv.xtream.DefaultXtreamClient
import com.vivicast.tv.iptv.xtream.DefaultXtreamParser
import com.vivicast.tv.iptv.xtream.OkHttpXtreamTransport
import com.vivicast.tv.iptv.xtream.XtreamCredentials
import com.vivicast.tv.iptv.xtream.XtreamHttpException
import com.vivicast.tv.iptv.xmltv.DefaultXmltvParser
import com.vivicast.tv.domain.model.ProviderType
import com.vivicast.tv.worker.ActiveProviderPlaylistSource
import com.vivicast.tv.worker.DefaultCacheCleaner
import com.vivicast.tv.worker.DefaultEpgRefresher
import com.vivicast.tv.worker.DefaultLogoRefresher
import com.vivicast.tv.worker.DefaultPlaylistRefresher
import com.vivicast.tv.worker.DefaultRefreshWorkerRunner
import com.vivicast.tv.worker.GlobalRefreshOrchestrator
import com.vivicast.tv.worker.InMemoryRefreshDiagnostics
import com.vivicast.tv.worker.OkHttpBinaryFetcher
import com.vivicast.tv.worker.OkHttpTextFetcher
import com.vivicast.tv.worker.RefreshDiagnostics
import com.vivicast.tv.worker.RefreshHttpException
import com.vivicast.tv.worker.RefreshWorkScheduler
import com.vivicast.tv.worker.RefreshWorkerRegistry
import com.vivicast.tv.worker.RefreshWorkerRunner
import com.vivicast.tv.worker.RoomEpgSourceReader
import com.vivicast.tv.worker.RoomMediaImageRefreshSource
import com.vivicast.tv.worker.WorkManagerRefreshWorkScheduler
import java.io.File
import kotlinx.coroutines.flow.first

class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    val database: VivicastDatabase by lazy {
        VivicastDatabaseFactory.create(appContext)
    }

    val userPreferencesStore: UserPreferencesStore by lazy {
        DataStoreUserPreferencesStore(appContext)
    }

    val diagnosticsStore: DiagnosticsStore by lazy {
        DiagnosticsStore(appContext)
    }

    private val userAgentPolicy = RuntimeUserAgentPolicy()

    val secureValueStore: SecureValueStore by lazy {
        AndroidKeystoreSecureValueStore(appContext)
    }

    val pinSecurityStateStore: PinSecurityStateStore by lazy {
        SecureValuePinSecurityStateStore(secureValueStore)
    }

    val standardBackupExporter: StandardBackupExporter by lazy {
        StandardBackupExporter(
            database = database,
            userPreferencesStore = userPreferencesStore,
            secureValueStore = secureValueStore,
            pinSecurityStateStore = pinSecurityStateStore,
        )
    }

    val standardBackupRestorer: StandardBackupRestorer by lazy {
        StandardBackupRestorer(
            database = database,
            userPreferencesStore = userPreferencesStore,
            secureValueStore = secureValueStore,
            pinSecurityStateStore = pinSecurityStateStore,
            createInternalSafetyBackup = { createStandardRestoreSafetyBackup() },
        )
    }

    private val rawProviderRepository: ProviderRepository by lazy {
        RoomProviderRepository(
            database = database,
            secureValueStore = secureValueStore,
        )
    }

    val providerRepository: ProviderRepository by lazy {
        SystemIntegrationProviderRepository(rawProviderRepository) {
            syncWatchNext()
        }
    }

    private val okHttpClient by lazy {
        NetworkClientFactory().createOkHttpClient(userAgentPolicy::current)
    }

    val catalogImportRepository: CatalogImportRepository by lazy {
        RoomCatalogImportRepository(
            database = database,
            m3uStreamReferenceStore = m3uStreamReferenceStore,
        )
    }

    val mediaRepository: MediaRepository by lazy {
        RoomMediaRepository(database = database)
    }

    val favoritesRepository: FavoritesRepository by lazy {
        RoomFavoritesRepository(database = database)
    }

    val epgImportRepository: EpgImportRepository by lazy {
        RoomEpgRepository(database = database)
    }

    val epgSourceRepository: EpgSourceRepository by lazy {
        SecureEpgSourceRepository(
            database = database,
            secureValueStore = secureValueStore,
        )
    }

    val refreshDiagnostics: RefreshDiagnostics by lazy {
        InMemoryRefreshDiagnostics()
    }

    val refreshWorkScheduler: RefreshWorkScheduler by lazy {
        WorkManagerRefreshWorkScheduler(WorkManager.getInstance(appContext))
    }

    val mediaCacheStore: MediaCacheStore by lazy {
        FileMediaCacheStore(File(appContext.cacheDir, "media"))
    }

    private val m3uStreamReferenceStore: M3uStreamReferenceStore by lazy {
        SecureM3uStreamReferenceStore(secureValueStore)
    }

    val playbackStreamResolver: PlaybackStreamResolver by lazy {
        DefaultPlaybackStreamResolver(
            providerRepository = providerRepository,
            m3uStreamReferenceStore = m3uStreamReferenceStore,
        )
    }

    private val rawPlaybackRepository: PlaybackRepository by lazy {
        RoomPlaybackRepository(database = database)
    }

    val playbackRepository: PlaybackRepository by lazy {
        SystemIntegrationPlaybackRepository(rawPlaybackRepository) {
            syncWatchNext()
        }
    }

    val playbackRequestFactory: PlaybackRequestFactory by lazy {
        PlaybackRequestFactory(
            playbackStreamResolver = playbackStreamResolver,
            playbackRepository = playbackRepository,
        )
    }

    val playbackProgressRecorder: PlaybackProgressRecorder by lazy {
        PlaybackProgressRecorder(playbackRepository = playbackRepository)
    }

    val watchNextSynchronizer: WatchNextSynchronizer by lazy {
        WatchNextSynchronizer(
            providerRepository = rawProviderRepository,
            mediaRepository = mediaRepository,
            playbackRepository = rawPlaybackRepository,
            pinSecurityStateStore = pinSecurityStateStore,
            publisher = AndroidTvWatchNextPublisher(appContext),
        )
    }

    val playerController: VivicastPlayerController by lazy {
        DefaultVivicastPlayerController(
            engine = Media3PlaybackEngine(
                context = appContext,
                userAgentProvider = userAgentPolicy::current,
            ),
        )
    }

    val refreshWorkerRunner: RefreshWorkerRunner by lazy {
        val textFetcher = OkHttpTextFetcher(okHttpClient)
        val binaryFetcher = OkHttpBinaryFetcher(okHttpClient)
        val epgSourceReader = RoomEpgSourceReader(
            database = database,
            secureValueStore = secureValueStore,
        )
        val playlistRefresher = DefaultPlaylistRefresher(
            providerRepository = providerRepository,
            catalogImportRepository = catalogImportRepository,
            m3uParser = DefaultM3uParser(),
            textFetcher = textFetcher,
            xtreamClient = DefaultXtreamClient(OkHttpXtreamTransport(okHttpClient)),
            xtreamParser = DefaultXtreamParser(),
            epgSourceReader = epgSourceReader,
        )
        val epgRefresher = DefaultEpgRefresher(
            epgSourceReader = epgSourceReader,
            providerRepository = providerRepository,
            textFetcher = textFetcher,
            xmltvParser = DefaultXmltvParser(),
            epgImportRepository = epgImportRepository,
            epgPastRetentionDaysProvider = { userPreferencesStore.values.first().epg.pastRetentionDays },
            epgFutureRetentionDaysProvider = { userPreferencesStore.values.first().epg.futureRetentionDays },
        )
        val logoRefresher = DefaultLogoRefresher(
            mediaImageRefreshSource = RoomMediaImageRefreshSource(database),
            mediaCacheStore = mediaCacheStore,
            binaryFetcher = binaryFetcher,
        )
        val cacheCleaner = DefaultCacheCleaner(mediaCacheStore) {
            DEFAULT_MEDIA_CACHE_SIZE_BYTES
        }
        val orchestrator = GlobalRefreshOrchestrator(
            playlistSource = ActiveProviderPlaylistSource(providerRepository),
            playlistRefresher = playlistRefresher,
            epgSourceResolver = epgSourceReader,
            epgRefresher = epgRefresher,
            logoRefresher = logoRefresher,
            cacheCleaner = cacheCleaner,
            diagnostics = refreshDiagnostics,
        )
        DefaultRefreshWorkerRunner(
            orchestrator = orchestrator,
            playlistRefresher = playlistRefresher,
            epgRefresher = epgRefresher,
            logoRefresher = logoRefresher,
            cacheCleaner = cacheCleaner,
        )
    }

    fun installWorkerRunner() {
        RefreshWorkerRegistry.install(refreshWorkerRunner)
    }

    fun updateGlobalUserAgent(userAgent: String) {
        userAgentPolicy.update(userAgent)
    }

    suspend fun syncWatchNext() {
        watchNextSynchronizer.sync()
    }

    private suspend fun createStandardRestoreSafetyBackup(): Boolean =
        runCatching {
            val directory = File(appContext.filesDir, "restore-safety-backups")
            if (!directory.exists() && !directory.mkdirs()) {
                false
            } else {
                val file = File(directory, "standard-${System.currentTimeMillis()}.json")
                file.writeText(standardBackupExporter.exportJson(indentSpaces = 0), Charsets.UTF_8)
                true
            }
        }.getOrDefault(false)

    suspend fun testProviderConnection(request: ProviderCreateRequest): String? =
        runCatching {
            when (request.type) {
                ProviderType.M3u -> testM3uConnection(request)
                ProviderType.Xtream -> testXtreamConnection(request)
            }
        }.fold(
            onSuccess = { null },
            onFailure = { it.toProviderConnectionMessage() },
        )

    private suspend fun testM3uConnection(request: ProviderCreateRequest) {
        val source = if (request.m3uSourceMode.isAutomaticallyRefreshable) {
            val url = request.m3uUrl?.trim()?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException()
            OkHttpTextFetcher(okHttpClient).fetch(url)
        } else {
            request.m3uContent?.trim()?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException()
        }
        val playlist = DefaultM3uParser().parse(source)
        if (playlist.channels.isEmpty()) {
            throw ProviderConnectionResponseException()
        }
    }

    private suspend fun testXtreamConnection(request: ProviderCreateRequest) {
        val credentials = XtreamCredentials(
            serverUrl = request.xtreamServerUrl?.trim()?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException(),
            username = request.xtreamUsername?.trim()?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException(),
            password = request.xtreamPassword?.trim()?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException(),
        )
        val client = DefaultXtreamClient(OkHttpXtreamTransport(okHttpClient))
        val response = when {
            request.includeLiveTv -> client.getLiveCategories(credentials)
            request.includeMovies -> client.getVodCategories(credentials)
            request.includeSeries -> client.getSeriesCategories(credentials)
            else -> throw IllegalArgumentException()
        }
        if (!response.trimStart().startsWith("[")) {
            throw ProviderConnectionResponseException()
        }
    }

    private companion object {
        const val DEFAULT_MEDIA_CACHE_SIZE_BYTES = 500L * 1024L * 1024L
    }
}

private class ProviderConnectionResponseException : RuntimeException()

private class RuntimeUserAgentPolicy {
    @Volatile
    private var value: String = DEFAULT_GLOBAL_USER_AGENT

    fun current(): String = value

    fun update(userAgent: String) {
        value = userAgent.trim().ifBlank { DEFAULT_GLOBAL_USER_AGENT }
    }
}

private fun Throwable.toProviderConnectionMessage(): String =
    when (this) {
        is RefreshHttpException -> if (statusCode == 401 || statusCode == 403) {
            "Zugangsdaten ungültig."
        } else {
            "Quelle nicht erreichbar."
        }
        is XtreamHttpException -> if (statusCode == 401 || statusCode == 403) {
            "Zugangsdaten ungültig."
        } else {
            "Quelle nicht erreichbar."
        }
        is ProviderConnectionResponseException -> "Antwortformat nicht nutzbar."
        is IllegalArgumentException -> "Adresse oder Zugangsdaten sind ungültig."
        else -> "Quelle nicht erreichbar."
    }
