package com.vivicast.tv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.playback.PlaybackRepository

/**
 * Manual factory wiring the existing repositories into [HomeViewModel].
 * Kept deliberately simple (no Hilt/Koin) to match the current AppContainer-based DI.
 */
internal class HomeViewModelFactory(
    private val playbackRepository: PlaybackRepository,
    private val mediaRepository: MediaRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return HomeViewModel(playbackRepository, mediaRepository) as T
    }
}
