package com.vivicast.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vivicast.tv.core.cache.MediaCacheStore
import com.vivicast.tv.core.datastore.UserPreferencesStore
import com.vivicast.tv.data.epg.EpgSourceRepository
import com.vivicast.tv.data.media.CategoryGroupRepository
import com.vivicast.tv.data.provider.ProviderRepository

/**
 * Manual factory wiring the existing [UserPreferencesStore], [MediaCacheStore],
 * [EpgSourceRepository] and [ProviderRepository] into [SettingsViewModel]. Kept deliberately
 * simple (no Hilt/Koin) to match the current AppContainer-based DI.
 */
internal class SettingsViewModelFactory(
    private val userPreferencesStore: UserPreferencesStore,
    private val mediaCacheStore: MediaCacheStore,
    private val epgSourceRepository: EpgSourceRepository,
    private val providerRepository: ProviderRepository,
    private val categoryGroupRepository: CategoryGroupRepository,
    private val imageCacheSizeBytes: suspend () -> Long = { 0L },
    private val clearImageCache: suspend () -> Unit = {},
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(
            userPreferencesStore,
            mediaCacheStore,
            epgSourceRepository,
            providerRepository,
            categoryGroupRepository,
            imageCacheSizeBytes,
            clearImageCache,
        ) as T
    }
}
