package com.vivicast.tv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.playback.PlaybackRepository
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.domain.model.ChannelHistory
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.PlaybackProgress
import com.vivicast.tv.domain.model.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach

internal const val RECENT_CHANNELS_LIMIT = 6

/**
 * Presentation-state holder for the home screen. Combines the provider list with the playback flows
 * (in-progress movies, episode history, recent channels) and the per-type catalog-presence flags, and
 * resolves an immutable [HomeUiState].
 *
 * Only content from **active** providers is surfaced (disabled playlists contribute nothing). Series are
 * resolved series-centric with next-episode advancement (see [resolveSeriesContinue]). No Android
 * Context/Resources, no Compose types, no navigation, no localized strings.
 *
 * [scope] lets unit tests inject a controlled scope; in production it defaults to [viewModelScope].
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class HomeViewModel(
    private val playbackRepository: PlaybackRepository,
    private val mediaRepository: MediaRepository,
    private val providerRepository: ProviderRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {

    private val coroutineScope: CoroutineScope = scope ?: viewModelScope

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        val hasTypes = combine(
            mediaRepository.observeHasLiveContent(),
            mediaRepository.observeHasMovieContent(),
            mediaRepository.observeHasSeriesContent(),
        ) { live, movies, series -> Triple(live, movies, series) }

        combine(
            providerRepository.observeProviders(),
            playbackRepository.observeAllContinueWatching(),
            playbackRepository.observeAllEpisodeProgress(),
            playbackRepository.observeAllRecentChannels(RECENT_CHANNELS_LIMIT),
            hasTypes,
        ) { providers, continueWatching, episodeProgress, recentChannels, has ->
            HomeInputs(
                providers = providers,
                continueWatching = continueWatching,
                episodeProgress = episodeProgress,
                recentChannels = recentChannels,
                hasLive = has.first,
                hasMovies = has.second,
                hasSeries = has.third,
            )
        }
            .mapLatest { resolve(it) }
            .onEach { _uiState.value = it }
            .launchIn(coroutineScope)
    }

    private data class HomeInputs(
        val providers: List<Provider>,
        val continueWatching: List<PlaybackProgress>,
        val episodeProgress: List<PlaybackProgress>,
        val recentChannels: List<ChannelHistory>,
        val hasLive: Boolean,
        val hasMovies: Boolean,
        val hasSeries: Boolean,
    )

    private suspend fun resolve(input: HomeInputs): HomeUiState {
        val activeIds = input.providers.filter { it.isActive }.map { it.id }.toSet()

        val movieItems = input.continueWatching
            .filter { it.mediaType == MediaType.Movie && it.providerId in activeIds }
            .mapNotNull { progress ->
                mediaRepository.getMovie(progress.providerId, progress.mediaId)
                    ?.let { MovieContinueHomeItem(progress = progress, movie = it) }
            }

        val seriesItems = resolveSeriesContinue(input.episodeProgress, activeIds)

        val recentChannels = input.recentChannels
            .filter { it.providerId in activeIds }
            .mapNotNull { history ->
                mediaRepository.getChannel(history.providerId, history.channelId)
                    ?.let { RecentChannelHomeItem(history = history, channel = it) }
            }

        val emptyReason = when {
            input.hasLive || input.hasMovies || input.hasSeries -> null
            input.providers.isEmpty() -> HomeEmptyReason.NoPlaylist
            activeIds.isEmpty() -> HomeEmptyReason.AllDisabled
            else -> HomeEmptyReason.EmptyCatalog
        }

        return HomeUiState(
            loaded = true,
            movieItems = movieItems,
            seriesItems = seriesItems,
            recentChannels = recentChannels,
            hasLive = input.hasLive,
            hasMovies = input.hasMovies,
            hasSeries = input.hasSeries,
            emptyReason = emptyReason,
        )
    }

    /**
     * Series-centric resume. [episodeProgress] is every episode row (completed + in-progress), newest
     * first. Per series we take the most-recently-watched episode: if it's still in progress we resume it;
     * if it's completed and a next episode exists we advance to that next episode (progress 0); otherwise
     * the series drops off. One entry per series (no duplicates).
     */
    private suspend fun resolveSeriesContinue(
        episodeProgress: List<PlaybackProgress>,
        activeIds: Set<String>,
    ): List<SeriesContinueHomeItem> {
        val seenSeries = HashSet<String>()
        val result = ArrayList<SeriesContinueHomeItem>()
        for (progress in episodeProgress) {
            if (progress.providerId !in activeIds) continue
            val episode = mediaRepository.getEpisode(progress.providerId, progress.mediaId) ?: continue
            val seriesKey = "${episode.providerId}:${episode.seriesId}"
            if (!seenSeries.add(seriesKey)) continue // already resolved this series from a newer row
            val series = mediaRepository.getSeries(episode.providerId, episode.seriesId) ?: continue
            if (!progress.isCompleted) {
                result += SeriesContinueHomeItem(series = series, episode = episode, progressPercent = progress.progressPercent)
            } else {
                val next = mediaRepository.getNextEpisode(episode) ?: continue
                result += SeriesContinueHomeItem(series = series, episode = next, progressPercent = 0)
            }
        }
        return result
    }
}
