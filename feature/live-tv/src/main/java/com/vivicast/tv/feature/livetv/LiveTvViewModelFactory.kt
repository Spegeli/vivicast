package com.vivicast.tv.feature.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vivicast.tv.data.epg.EpgRepository
import com.vivicast.tv.data.favorites.FavoritesRepository
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.provider.ProviderRepository

/**
 * Manual factory wiring the existing repositories into [LiveTvViewModel].
 * Kept deliberately simple (no Hilt/Koin) to match the current AppContainer-based DI.
 */
internal class LiveTvViewModelFactory(
    private val providerRepository: ProviderRepository,
    private val mediaRepository: MediaRepository,
    private val epgRepository: EpgRepository,
    private val favoritesRepository: FavoritesRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return LiveTvViewModel(
            providerRepository = providerRepository,
            mediaRepository = mediaRepository,
            epgRepository = epgRepository,
            favoritesRepository = favoritesRepository,
        ) as T
    }
}
