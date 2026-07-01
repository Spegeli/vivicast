package com.vivicast.tv.feature.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vivicast.tv.data.favorites.FavoritesRepository
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.playback.PlaybackRepository
import com.vivicast.tv.data.provider.ProviderRepository

/**
 * Manual factory wiring the existing repositories into [MoviesViewModel].
 * Kept deliberately simple (no Hilt/Koin) to match the current AppContainer-based DI.
 */
internal class MoviesViewModelFactory(
    private val providerRepository: ProviderRepository,
    private val mediaRepository: MediaRepository,
    private val favoritesRepository: FavoritesRepository,
    private val playbackRepository: PlaybackRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MoviesViewModel(
            providerRepository = providerRepository,
            mediaRepository = mediaRepository,
            favoritesRepository = favoritesRepository,
            playbackRepository = playbackRepository,
        ) as T
    }
}
