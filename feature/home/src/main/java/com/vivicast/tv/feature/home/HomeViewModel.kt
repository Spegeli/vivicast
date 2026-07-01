package com.vivicast.tv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.playback.PlaybackRepository
import com.vivicast.tv.domain.model.ChannelHistory
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.PlaybackProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val RECENT_CHANNELS_LIMIT = 12

/**
 * Presentation-state holder for the home screen. Combines the playback repository
 * flows (continue watching, recent channels) and enriches each entry with the
 * media repository, exposing an immutable [HomeUiState] as [StateFlow].
 * No Android Context/Resources, no Compose types, no navigation, no localized strings.
 *
 * [scope] lets unit tests inject a controlled scope; in production it defaults to
 * [viewModelScope].
 */
internal class HomeViewModel(
    private val playbackRepository: PlaybackRepository,
    private val mediaRepository: MediaRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {

    private val coroutineScope: CoroutineScope = scope ?: viewModelScope

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        coroutineScope.launch {
            playbackRepository.observeAllContinueWatching().collect { progress ->
                _uiState.update { it.copy(continueItems = resolveContinueItems(progress)) }
            }
        }
        coroutineScope.launch {
            playbackRepository.observeAllRecentChannels(RECENT_CHANNELS_LIMIT).collect { history ->
                _uiState.update { it.copy(recentChannels = resolveRecentChannels(history)) }
            }
        }
    }

    private suspend fun resolveContinueItems(progress: List<PlaybackProgress>): List<ContinueHomeItem> =
        progress.mapNotNull { item ->
            when (item.mediaType) {
                MediaType.Movie -> mediaRepository.getMovie(item.providerId, item.mediaId)?.let { movie ->
                    ContinueHomeItem.MovieItem(progress = item, movie = movie)
                }
                MediaType.Episode -> mediaRepository.getEpisode(item.providerId, item.mediaId)?.let { episode ->
                    ContinueHomeItem.EpisodeItem(progress = item, episode = episode)
                }
                MediaType.Channel,
                MediaType.Series -> null
            }
        }

    private suspend fun resolveRecentChannels(history: List<ChannelHistory>): List<RecentChannelHomeItem> =
        history.mapNotNull { item ->
            mediaRepository.getChannel(item.providerId, item.channelId)?.let { channel ->
                RecentChannelHomeItem(history = item, channel = channel)
            }
        }
}
