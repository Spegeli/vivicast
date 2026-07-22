package com.vivicast.tv.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vivicast.tv.core.player.VivicastPlayerController
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.playback.PlaybackProgressRecorder
import com.vivicast.tv.data.playback.PlaybackRequestFactory

/**
 * Manual factory wiring the already-built player singletons into [PlayerViewModel] (no Hilt/Koin — matches the
 * AppContainer-based DI). [playerController] is created App-side (Media3 needs a Context) and handed in as-is; the
 * VM never constructs it.
 */
internal class PlayerViewModelFactory(
    private val playerController: VivicastPlayerController,
    private val playbackRequestFactory: PlaybackRequestFactory,
    private val playbackProgressRecorder: PlaybackProgressRecorder,
    private val mediaRepository: MediaRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PlayerViewModel(
            playerController = playerController,
            playbackRequestFactory = playbackRequestFactory,
            playbackProgressRecorder = playbackProgressRecorder,
            mediaRepository = mediaRepository,
        ) as T
    }
}
