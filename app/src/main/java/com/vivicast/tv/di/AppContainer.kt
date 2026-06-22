package com.vivicast.tv.di

import android.content.Context
import androidx.work.WorkManager
import com.vivicast.tv.core.cache.FileMediaCacheStore
import com.vivicast.tv.core.cache.MediaCacheStore
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.VivicastDatabaseFactory
import com.vivicast.tv.core.datastore.DataStoreUserPreferencesStore
import com.vivicast.tv.core.datastore.UserPreferencesStore
import com.vivicast.tv.core.network.NetworkClientFactory
import com.vivicast.tv.core.player.DefaultVivicastPlayerController
import com.vivicast.tv.core.player.Media3PlaybackEngine
import com.vivicast.tv.core.player.VivicastPlayerController
import com.vivicast.tv.core.security.AndroidKeystoreSecureValueStore
import com.vivicast.tv.core.security.SecureValueStore
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
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.RoomProviderRepository
import com.vivicast.tv.iptv.m3u.DefaultM3uParser
import com.vivicast.tv.iptv.xtream.DefaultXtreamClient
import com.vivicast.tv.iptv.xtream.DefaultXtreamParser
import com.vivicast.tv.iptv.xtream.OkHttpXtreamTransport
import com.vivicast.tv.iptv.xmltv.DefaultXmltvParser
import com.vivicast.tv.worker.ActiveProviderPlaylistSource
import com.vivicast.tv.worker.DefaultCacheCleaner
import com.vivicast.tv.worker.DefaultEpgRefresher
import com.vivicast.tv.worker.DefaultLogoRefresher
import com.vivicast.tv.worker.DefaultPlaylistRefresher
import com.vivicast.tv.worker.DefaultRefreshWorkerRunner
import com.vivicast.tv.worker.GlobalRefreshOrchestrator
import com.vivicast.tv.worker.InMemoryRefreshDiagnostics
import com.vivicast.tv.worker.NoOpEpgMappingApplier
import com.vivicast.tv.worker.OkHttpBinaryFetcher
import com.vivicast.tv.worker.OkHttpTextFetcher
import com.vivicast.tv.worker.RefreshDiagnostics
import com.vivicast.tv.worker.RefreshWorkScheduler
import com.vivicast.tv.worker.RefreshWorkerRegistry
import com.vivicast.tv.worker.RefreshWorkerRunner
import com.vivicast.tv.worker.RoomEpgSourceReader
import com.vivicast.tv.worker.RoomMediaImageRefreshSource
import com.vivicast.tv.worker.WorkManagerRefreshWorkScheduler
import kotlinx.coroutines.flow.first
import java.io.File

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

    val secureValueStore: SecureValueStore by lazy {
        AndroidKeystoreSecureValueStore(appContext)
    }

    val providerRepository: ProviderRepository by lazy {
        RoomProviderRepository(
            database = database,
            secureValueStore = secureValueStore,
        )
    }

    private val okHttpClient by lazy {
        NetworkClientFactory().createOkHttpClient()
    }

    val catalogImportRepository: CatalogImportRepository by lazy {
        RoomCatalogImportRepository(database = database)
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

    val playerController: VivicastPlayerController by lazy {
        DefaultVivicastPlayerController(
            engine = Media3PlaybackEngine(appContext),
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
        )
        val logoRefresher = DefaultLogoRefresher(
            mediaImageRefreshSource = RoomMediaImageRefreshSource(database),
            mediaCacheStore = mediaCacheStore,
            binaryFetcher = binaryFetcher,
        )
        val cacheCleaner = DefaultCacheCleaner(mediaCacheStore) {
            val maxSizeMb = userPreferencesStore.values.first().cache.maxCacheSizeMb
            if (maxSizeMb <= 0) -1L else maxSizeMb * BYTES_PER_MEGABYTE
        }
        val orchestrator = GlobalRefreshOrchestrator(
            playlistSource = ActiveProviderPlaylistSource(providerRepository),
            playlistRefresher = playlistRefresher,
            epgSourceResolver = epgSourceReader,
            epgRefresher = epgRefresher,
            epgMappingApplier = NoOpEpgMappingApplier(),
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

    private companion object {
        const val BYTES_PER_MEGABYTE = 1024L * 1024L
    }
}
