package com.vivicast.tv.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vivicast.tv.data.media.MediaRepository

/**
 * Manual factory that wires the existing [MediaRepository] into [SearchViewModel].
 * Kept deliberately simple (no Hilt/Koin) to match the current AppContainer-based DI.
 */
internal class SearchViewModelFactory(
    private val mediaRepository: MediaRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SearchViewModel(mediaRepository) as T
    }
}
