package com.vivicast.tv.di

import android.content.Context
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.VivicastDatabaseFactory
import com.vivicast.tv.core.datastore.DataStoreUserPreferencesStore
import com.vivicast.tv.core.datastore.UserPreferencesStore
import com.vivicast.tv.core.network.NetworkClientFactory
import com.vivicast.tv.core.security.AndroidKeystoreSecureValueStore
import com.vivicast.tv.core.security.SecureValueStore
import com.vivicast.tv.data.epg.EpgImportRepository
import com.vivicast.tv.data.epg.EpgSourceRepository
import com.vivicast.tv.data.epg.RoomEpgRepository
import com.vivicast.tv.data.epg.SecureEpgSourceRepository
import com.vivicast.tv.data.media.CatalogImportRepository
import com.vivicast.tv.data.media.RoomCatalogImportRepository
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.RoomProviderRepository
import com.vivicast.tv.iptv.m3u.DefaultM3uParser
import com.vivicast.tv.iptv.xtream.DefaultXtreamClient
import com.vivicast.tv.iptv.xtream.DefaultXtreamParser
import com.vivicast.tv.iptv.xtream.OkHttpXtreamTransport
import com.vivicast.tv.iptv.xmltv.DefaultXmltvParser
import com.vivicast.tv.worker.ActiveProviderPlaylistSource
import com.vivicast.tv.worker.DefaultEpgRefresher
import com.vivicast.tv.worker.DefaultPlaylistRefresher
import com.vivicast.tv.worker.DefaultRefreshWorkerRunner
import com.vivicast.tv.worker.GlobalRefreshOrchestrator
import com.vivicast.tv.worker.InMemoryRefreshDiagnostics
import com.vivicast.tv.worker.NoOpCacheCleaner
import com.vivicast.tv.worker.NoOpEpgMappingApplier
import com.vivicast.tv.worker.NoOpLogoRefresher
import com.vivicast.tv.worker.OkHttpTextFetcher
import com.vivicast.tv.worker.RefreshDiagnostics
import com.vivicast.tv.worker.RefreshWorkerRegistry
import com.vivicast.tv.worker.RefreshWorkerRunner
import com.vivicast.tv.worker.RoomEpgSourceReader

class AppContainer(
    context: Context,
) {
    val database: VivicastDatabase by lazy {
        VivicastDatabaseFactory.create(context)
    }

    val userPreferencesStore: UserPreferencesStore by lazy {
        DataStoreUserPreferencesStore(context)
    }

    val secureValueStore: SecureValueStore by lazy {
        AndroidKeystoreSecureValueStore(context)
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

    val refreshWorkerRunner: RefreshWorkerRunner by lazy {
        val textFetcher = OkHttpTextFetcher(okHttpClient)
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
        val logoRefresher = NoOpLogoRefresher()
        val cacheCleaner = NoOpCacheCleaner()
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
}
