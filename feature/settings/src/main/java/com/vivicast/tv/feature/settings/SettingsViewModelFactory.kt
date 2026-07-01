package com.vivicast.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vivicast.tv.core.cache.MediaCacheStore
import com.vivicast.tv.core.datastore.UserPreferencesStore

/**
 * Manual factory wiring the existing [UserPreferencesStore] and [MediaCacheStore] into
 * [SettingsViewModel]. Kept deliberately simple (no Hilt/Koin) to match the current
 * AppContainer-based DI.
 */
internal class SettingsViewModelFactory(
    private val userPreferencesStore: UserPreferencesStore,
    private val mediaCacheStore: MediaCacheStore,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(userPreferencesStore, mediaCacheStore) as T
    }
}
