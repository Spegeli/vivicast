package com.vivicast.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class TvAppViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = TvAppGraph(application)

    val controller: ViviCastTvController = graph.controller
    val uiState = LiveTvUiState()

    override fun onCleared() {
        graph.close()
    }
}

class TvAppViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TvAppViewModel::class.java)) {
            return TvAppViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
