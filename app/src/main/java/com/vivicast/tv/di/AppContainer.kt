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
import com.vivicast.tv.BuildConfig
import com.vivicast.tv.core.network.NetworkClientFactory
import com.vivicast.tv.core.player.DefaultVivicastPlayerController
import com.vivicast.tv.core.player.Media3PlaybackEngine
import com.vivicast.tv.core.player.VivicastPlayerController
import com.vivicast.tv.core.security.AndroidKeystoreSecureValueStore
import com.vivicast.tv.core.security.PinSecurityStateStore
import com.vivicast.tv.core.security.SecureValueStore
import com.vivicast.tv.core.security.SecureValuePinSecurityStateStore
import com.vivicast.tv.data.epg.EpgConnectionResponseException
import com.vivicast.tv.data.epg.EpgConnectionTestResult
import com.vivicast.tv.data.epg.EpgImportRepository
import com.vivicast.tv.data.epg.EpgSourceRepository
import com.vivicast.tv.data.epg.TestEpgSourceConnectionUseCase
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
import com.vivicast.tv.data.provider.ProviderConnectionResponseException
import com.vivicast.tv.data.provider.ProviderInvalidCredentialsException
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.data.provider.RoomProviderRepository
import com.vivicast.tv.data.provider.ProviderConnectionTestResult
import com.vivicast.tv.data.provider.TestProviderConnectionUseCase
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
import com.vivicast.tv.worker.DefaultCacheCleaner
import com.vivicast.tv.worker.DefaultEpgRefresher
import com.vivicast.tv.worker.DefaultLogoRefresher
import com.vivicast.tv.worker.DefaultPlaylistRefresher
import com.vivicast.tv.worker.DefaultRefreshWorkerRunner
import com.vivicast.tv.worker.DefaultSeriesDetailsRefresher
import com.vivicast.tv.worker.MaintenanceRefreshOrchestrator
import com.vivicast.tv.worker.InMemoryRefreshDiagnostics
import com.vivicast.tv.worker.M3uSourceTooLargeException
import com.vivicast.tv.worker.OkHttpBinaryFetcher
import com.vivicast.tv.worker.EpgSourceTooLargeException
import com.vivicast.tv.worker.OkHttpEpgStreamSource
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    // Process-scoped so the "refresh on app start" one-shots fire once per real launch (cold start) and
    // survive Activity recreation (e.g. a language-change recreate()), which a Compose-remembered flag
    // would not.
    var appStartRefreshTriggered: Boolean = false

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
        // trustAllCertificates is BuildConfig.DEBUG-gated: release builds fold this to `false`, so TLS
        // validation is always on in production. Debug builds skip validation so the emulator can reach
        // hosts whose valid chain its trust store rejects.
        NetworkClientFactory().createOkHttpClient(
            userAgentProvider = userAgentPolicy::current,
            trustAllCertificates = BuildConfig.DEBUG,
        )
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
            providerUserAgent = { providerId -> providerRepository.getProvider(providerId)?.userAgent },
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
            epgStreamSource = OkHttpEpgStreamSource(okHttpClient),
            xmltvParser = DefaultXmltvParser(),
            epgImportRepository = epgImportRepository,
            epgPastRetentionDaysProvider = { userPreferencesStore.values.first().epg.pastRetentionDays },
        )
        val seriesDetailsRefresher = DefaultSeriesDetailsRefresher(
            providerRepository = providerRepository,
            catalogImportRepository = catalogImportRepository,
            xtreamClient = DefaultXtreamClient(OkHttpXtreamTransport(okHttpClient)),
            xtreamParser = DefaultXtreamParser(),
        )
        val logoRefresher = DefaultLogoRefresher(
            mediaImageRefreshSource = RoomMediaImageRefreshSource(database),
            mediaCacheStore = mediaCacheStore,
            binaryFetcher = binaryFetcher,
        )
        val cacheCleaner = DefaultCacheCleaner(mediaCacheStore) {
            DEFAULT_MEDIA_CACHE_SIZE_BYTES
        }
        val orchestrator = MaintenanceRefreshOrchestrator(
            logoRefresher = logoRefresher,
            cacheCleaner = cacheCleaner,
            diagnostics = refreshDiagnostics,
        )
        DefaultRefreshWorkerRunner(
            orchestrator = orchestrator,
            playlistRefresher = playlistRefresher,
            epgRefresher = epgRefresher,
            seriesDetailsRefresher = seriesDetailsRefresher,
            scheduler = refreshWorkScheduler,
            refreshEpgOnPlaylistChangeProvider = {
                userPreferencesStore.values.first().epg.refreshOnPlaylistChangeEnabled
            },
        )
    }

    fun installWorkerRunner() {
        RefreshWorkerRegistry.install(refreshWorkerRunner)
    }

    /**
     * Clears transient "refreshing" state that a cancelled/killed refresh left stuck (provider status
     * REFRESHING, EPG source isRefreshing=1). Run once at app start so a stuck badge / status doesn't
     * linger and block nothing.
     */
    suspend fun recoverStuckRefreshState() {
        database.providerDao().clearStuckRefreshingStatus()
        database.epgDao().clearStuckRefreshingState()
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

    val testProviderConnectionUseCase: TestProviderConnectionUseCase by lazy {
        TestProviderConnectionUseCase(
            m3uParser = DefaultM3uParser(),
            xtreamClient = DefaultXtreamClient(OkHttpXtreamTransport(okHttpClient)),
            xtreamParser = DefaultXtreamParser(),
            fetchText = { url, userAgent -> OkHttpTextFetcher(okHttpClient).fetch(url, userAgent) },
        )
    }

    suspend fun testProviderConnection(request: ProviderCreateRequest): ProviderConnectionTestResult =
        // Off the main thread: URL fetch is blocking I/O and file mode parses the whole playlist.
        runCatching { withContext(Dispatchers.IO) { testProviderConnectionUseCase.test(request) } }
            .fold(
                onSuccess = { summary -> ProviderConnectionTestResult(errorMessage = null, summary = summary) },
                onFailure = { ProviderConnectionTestResult(errorMessage = it.toProviderConnectionMessage(), summary = null) },
            )

    private val testEpgSourceConnectionUseCase: TestEpgSourceConnectionUseCase by lazy {
        TestEpgSourceConnectionUseCase(streamSource = OkHttpEpgStreamSource(okHttpClient))
    }

    suspend fun testEpgSourceConnection(url: String): EpgConnectionTestResult =
        // Off the main thread: URL fetch is blocking I/O and XMLTV parsing walks the whole document.
        runCatching { withContext(Dispatchers.IO) { testEpgSourceConnectionUseCase.test(url) } }
            .fold(
                onSuccess = { summary -> EpgConnectionTestResult(errorMessage = null, summary = summary) },
                onFailure = { EpgConnectionTestResult(errorMessage = it.toEpgConnectionMessage(), summary = null) },
            )

    private companion object {
        const val DEFAULT_MEDIA_CACHE_SIZE_BYTES = 500L * 1024L * 1024L
    }
}

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
        is M3uSourceTooLargeException -> "Wiedergabeliste zu groß."
        is ProviderInvalidCredentialsException -> "Zugangsdaten ungültig."
        is ProviderConnectionResponseException -> "Antwortformat nicht nutzbar."
        is IllegalArgumentException -> "Adresse oder Zugangsdaten sind ungültig."
        else -> "Quelle nicht erreichbar."
    }

private fun Throwable.toEpgConnectionMessage(): String =
    when (this) {
        is RefreshHttpException -> if (statusCode == 401 || statusCode == 403) {
            "Zugangsdaten ungültig."
        } else {
            "Quelle nicht erreichbar."
        }
        is IllegalArgumentException -> "Adresse ist ungültig."
        is EpgSourceTooLargeException -> "EPG-Datei zu groß (max. 200 MB)."
        // Empty document or a parser exception (malformed XML) — the file is not usable XMLTV.
        is EpgConnectionResponseException -> "Keine gültige EPG-Datei (XMLTV)."
        else -> "Keine gültige EPG-Datei (XMLTV)."
    }
