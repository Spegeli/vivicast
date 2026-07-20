package com.vivicast.tv.feature.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.R
import com.vivicast.tv.core.designsystem.StatusBadge
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.data.favorites.FavoritesRepository
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.playback.PlaybackRepository
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.Favorite
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.PlaybackProgress
import com.vivicast.tv.domain.model.Season
import com.vivicast.tv.domain.model.Series
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Self-contained series-detail destination. Loads its series by STABLE keys (works on direct entry /
 * deep-link, no loaded grid), fetches seasons/episodes on demand (Xtream) and owns season/episode selection,
 * favorite and mark-seen. Season/episode stay in-page (no separate episode screen); [episodeStableKey], when
 * set, pre-selects the season + episode that contains it. BACK is handled by the NavHost (pop to the grid);
 * the "Zurück" action calls [onBack]. App-hoisted bits — the on-demand fetch, backdrop, player open — come in
 * as callbacks.
 */
@Composable
fun SeriesDetailScreen(
    providerStableKey: String,
    seriesStableKey: String,
    episodeStableKey: String? = null,
    mediaRepository: MediaRepository? = null,
    favoritesRepository: FavoritesRepository? = null,
    playbackRepository: PlaybackRepository? = null,
    ensureSeriesDetail: suspend (Series) -> Unit = {},
    resolveSeriesBackdropModel: suspend (Series) -> Any? = { null },
    onOpenPlayer: (Episode) -> Unit = {},
    onBack: () -> Unit = {},
) {
    if (mediaRepository == null || favoritesRepository == null || playbackRepository == null) {
        SeriesDetailUnavailable()
        return
    }
    val viewModel: SeriesDetailViewModel = viewModel(
        factory = SeriesDetailViewModelFactory(
            providerStableKey = providerStableKey,
            seriesStableKey = seriesStableKey,
            initialEpisodeStableKey = episodeStableKey,
            mediaRepository = mediaRepository,
            favoritesRepository = favoritesRepository,
            playbackRepository = playbackRepository,
            ensureSeriesDetail = ensureSeriesDetail,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val series = uiState.series
    val backdropModel by produceState<Any?>(initialValue = null, series?.id, series?.backdropUrl) {
        value = series?.let { resolveSeriesBackdropModel(it) }
    }

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxSize()) {
            when {
                series != null -> SeriesDetailPage(
                    series = series,
                    backdropModel = backdropModel,
                    isFavorite = uiState.isFavorite,
                    seasons = uiState.seasons,
                    selectedSeasonId = uiState.selectedSeasonId,
                    episodes = uiState.episodes,
                    selectedEpisodeId = uiState.selectedEpisodeId,
                    selectedEpisodeProgress = uiState.selectedEpisodeProgress,
                    continueTarget = uiState.continueTarget,
                    onSeasonSelected = { viewModel.onSeasonSelected(it.id) },
                    onEpisodeSelected = { viewModel.onEpisodeSelected(it.id) },
                    onEpisodePlay = onOpenPlayer,
                    onContinueEpisode = { target ->
                        viewModel.onContinueEpisode(target)
                        onOpenPlayer(target.episode)
                    },
                    onMarkEpisodeSeen = { viewModel.onMarkEpisodeSeen() },
                    onMarkEpisodeUnseen = { viewModel.onMarkEpisodeUnseen() },
                    onToggleFavorite = { viewModel.onToggleFavorite() },
                    onClose = onBack,
                )
                uiState.loaded -> SeriesDetailUnavailable()
                else -> Unit // still loading
            }
        }
    }
}

@Composable
private fun SeriesDetailUnavailable() {
    InfoPanel(
        title = stringResource(R.string.series_unavailable),
        body = stringResource(R.string.movies_no_content),
        badge = stringResource(R.string.common_empty_badge),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SeriesDetailPage(
    series: Series,
    backdropModel: Any?,
    isFavorite: Boolean,
    seasons: List<Season>,
    selectedSeasonId: String?,
    episodes: List<Episode>,
    selectedEpisodeId: String?,
    selectedEpisodeProgress: PlaybackProgress?,
    continueTarget: SeriesContinueTarget?,
    onSeasonSelected: (Season) -> Unit,
    onEpisodeSelected: (Episode) -> Unit,
    onEpisodePlay: (Episode) -> Unit,
    onContinueEpisode: (SeriesContinueTarget) -> Unit,
    onMarkEpisodeSeen: () -> Unit,
    onMarkEpisodeUnseen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit,
) {
    SeriesHero(
        series = series,
        provider = null,
        backdropModel = backdropModel,
        isFavorite = isFavorite,
        episode = episodes.firstOrNull { it.id == selectedEpisodeId } ?: episodes.firstOrNull(),
        showActions = false,
        onOpenPlayer = {},
        onToggleFavorite = onToggleFavorite,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
        if (continueTarget != null) {
            ActionPill(
                stringResource(R.string.series_continue),
                modifier = Modifier.testTag(seriesContinueActionTag(series.id)),
                onClick = { onContinueEpisode(continueTarget) },
            )
            StatusBadge(continueTarget.cardMeta)
        }
        ActionPill(if (isFavorite) stringResource(R.string.common_favorites) else stringResource(R.string.series_add_favorite), selected = isFavorite, onClick = onToggleFavorite)
        ActionPill(stringResource(R.string.series_back), onClick = onClose)
    }
    SeriesEpisodeSelector(
        seasons = seasons,
        selectedSeasonId = selectedSeasonId,
        onSeasonSelected = onSeasonSelected,
        episodes = episodes,
        selectedEpisodeId = selectedEpisodeId,
        selectedEpisodeProgress = selectedEpisodeProgress,
        onEpisodeSelected = onEpisodeSelected,
        onEpisodePlay = onEpisodePlay,
        onMarkEpisodeSeen = onMarkEpisodeSeen,
        onMarkEpisodeUnseen = onMarkEpisodeUnseen,
    )
}

@Composable
private fun SeriesEpisodeSelector(
    seasons: List<Season>,
    selectedSeasonId: String?,
    onSeasonSelected: (Season) -> Unit,
    episodes: List<Episode>,
    selectedEpisodeId: String?,
    selectedEpisodeProgress: PlaybackProgress? = null,
    onEpisodeSelected: (Episode) -> Unit,
    onEpisodePlay: (Episode) -> Unit,
    onMarkEpisodeSeen: () -> Unit = {},
    onMarkEpisodeUnseen: () -> Unit = {},
) {
    if (seasons.isEmpty()) return

    val staffelStr = stringResource(R.string.series_season)
    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
            items(seasons, key = { it.id }) { season ->
                ActionPill(
                    label = season.name.ifBlank { "$staffelStr ${season.seasonNumber}" },
                    selected = season.id == selectedSeasonId,
                    onClick = { onSeasonSelected(season) },
                )
            }
        }

        if (episodes.isEmpty()) {
            InfoPanel(
                title = stringResource(R.string.series_no_episodes),
                body = stringResource(R.string.series_no_episodes_body),
                badge = stringResource(R.string.common_empty_badge),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                items(episodes, key = { it.id }) { episode ->
                    ActionPill(
                        label = "S${episode.seasonNumber}E${episode.episodeNumber} ${episode.name}",
                        selected = episode.id == selectedEpisodeId,
                        modifier = Modifier.testTag(seriesEpisodeTag(episode.id)),
                        onClick = {
                            onEpisodeSelected(episode)
                            onEpisodePlay(episode)
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                if (selectedEpisodeProgress?.isCompleted == true) {
                    StatusBadge(stringResource(R.string.series_watched))
                    ActionPill(stringResource(R.string.movies_mark_unwatched), onClick = onMarkEpisodeUnseen)
                } else {
                    ActionPill(stringResource(R.string.movies_mark_watched), onClick = onMarkEpisodeSeen)
                }
            }
        }
    }
}

internal fun seriesEpisodeTag(episodeId: String): String = "series-episode-$episodeId"

internal fun seriesContinueActionTag(seriesId: String): String = "series-continue-$seriesId"

internal data class SeriesDetailUiState(
    val loaded: Boolean = false,
    val series: Series? = null,
    val isFavorite: Boolean = false,
    val seasons: List<Season> = emptyList(),
    val selectedSeasonId: String? = null,
    val episodes: List<Episode> = emptyList(),
    val selectedEpisodeId: String? = null,
    val selectedEpisode: Episode? = null,
    val selectedEpisodeProgress: PlaybackProgress? = null,
    val continueTarget: SeriesContinueTarget? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class SeriesDetailViewModel(
    private val providerStableKey: String,
    private val seriesStableKey: String,
    private val initialEpisodeStableKey: String?,
    private val mediaRepository: MediaRepository,
    private val favoritesRepository: FavoritesRepository,
    private val playbackRepository: PlaybackRepository,
    private val ensureSeriesDetail: suspend (Series) -> Unit = {},
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    scope: CoroutineScope? = null,
) : ViewModel() {

    private val coroutineScope: CoroutineScope = scope ?: viewModelScope

    private val seriesFlow = MutableStateFlow<Series?>(null)
    private val selectedSeasonIdFlow = MutableStateFlow<String?>(null)
    private val selectedEpisodeIdFlow = MutableStateFlow<String?>(null)

    private var series: Series? = null
    private var loaded = false
    private var isFavorite = false
    private var seasons: List<Season> = emptyList()
    private var episodes: List<Episode> = emptyList()
    private var selectedEpisodeProgress: PlaybackProgress? = null
    private var continueTarget: SeriesContinueTarget? = null

    private val _uiState = MutableStateFlow(SeriesDetailUiState())
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()

    init {
        coroutineScope.launch {
            val loadedSeries = mediaRepository.getSeriesByStableKeys(providerStableKey, seriesStableKey)
            series = loadedSeries
            loaded = true
            if (loadedSeries != null) {
                // Episode deep-link / continue: pre-select the season + episode containing the given episode.
                initialEpisodeStableKey?.let { episodeKey ->
                    mediaRepository.getEpisodeByStableKeys(providerStableKey, episodeKey)?.let { episode ->
                        selectedSeasonIdFlow.value = episode.seasonId
                        selectedEpisodeIdFlow.value = episode.id
                    }
                }
                seriesFlow.value = loadedSeries
                // On-demand season/episode fetch (Xtream getSeriesInfo -> import; no-op for M3U). Launched
                // separately so the network call never blocks the flows; guarded to avoid a refetch.
                coroutineScope.launch {
                    if (mediaRepository.observeSeasons(loadedSeries.providerId, loadedSeries.id).first().isEmpty()) {
                        runCatching { ensureSeriesDetail(loadedSeries) }
                    }
                }
            }
            rebuild()
        }

        coroutineScope.launch {
            seriesFlow.flatMapLatest { current ->
                if (current == null) flowOf(emptyList()) else mediaRepository.observeSeasons(current.providerId, current.id)
            }.collect { loadedSeasons ->
                seasons = loadedSeasons
                ensureSeasonSelected()
                rebuild()
            }
        }

        coroutineScope.launch {
            combine(seriesFlow, selectedSeasonIdFlow) { current, seasonId -> current to seasonId }
                .flatMapLatest { (current, seasonId) ->
                    if (current == null || seasonId == null) {
                        flowOf(emptyList())
                    } else {
                        mediaRepository.observeEpisodes(current.providerId, seasonId)
                    }
                }
                .collect { loadedEpisodes ->
                    episodes = loadedEpisodes
                    ensureEpisodeSelected()
                    rebuild()
                }
        }

        coroutineScope.launch {
            combine(seriesFlow, selectedEpisodeIdFlow) { current, episodeId -> current to episodeId }
                .collect { (current, episodeId) ->
                    selectedEpisodeProgress = if (current != null && episodeId != null) {
                        playbackRepository.getProgress(current.providerId, MediaType.Episode, episodeId)
                    } else {
                        null
                    }
                    rebuild()
                }
        }

        coroutineScope.launch {
            seriesFlow.flatMapLatest { current ->
                if (current == null) {
                    flowOf(Pair(emptyList<Favorite>(), emptyList<PlaybackProgress>()))
                } else {
                    combine(
                        favoritesRepository.observeFavorites(current.providerId, MediaType.Series),
                        playbackRepository.observeContinueWatching(current.providerId),
                    ) { favorites, progress -> favorites to progress }
                }
            }.collect { (favorites, progress) ->
                val current = series
                isFavorite = current != null && favorites.any { it.mediaId == current.id }
                continueTarget = if (current == null) {
                    null
                } else {
                    progress
                        .filter { it.mediaType == MediaType.Episode }
                        .sortedByDescending { it.lastWatchedAt }
                        .firstNotNullOfOrNull { p ->
                            mediaRepository.getEpisode(p.providerId, p.mediaId)
                                ?.takeIf { it.seriesId == current.id }
                                ?.let { episode -> SeriesContinueTarget(p, episode) }
                        }
                }
                rebuild()
            }
        }
    }

    fun onSeasonSelected(seasonId: String) {
        selectedEpisodeIdFlow.value = null
        selectedSeasonIdFlow.value = seasonId
    }

    fun onEpisodeSelected(episodeId: String) {
        selectedEpisodeIdFlow.value = episodeId
    }

    fun onContinueEpisode(target: SeriesContinueTarget) {
        selectedSeasonIdFlow.value = target.episode.seasonId
        selectedEpisodeIdFlow.value = target.episode.id
    }

    fun onToggleFavorite() {
        val current = series ?: return
        coroutineScope.launch { favoritesRepository.toggleFavorite(current.providerId, MediaType.Series, current.id) }
    }

    fun onMarkEpisodeSeen() {
        val episode = currentSelectedEpisode() ?: return
        coroutineScope.launch {
            val completed = episode.completedProgress(selectedEpisodeProgress, nowProvider())
            playbackRepository.saveProgress(completed)
            selectedEpisodeProgress = completed
            rebuild()
        }
    }

    fun onMarkEpisodeUnseen() {
        val current = series ?: return
        val episode = currentSelectedEpisode() ?: return
        coroutineScope.launch {
            playbackRepository.deleteProgress(current.providerId, MediaType.Episode, episode.id)
            selectedEpisodeProgress = null
            rebuild()
        }
    }

    private fun currentSelectedEpisode(): Episode? =
        episodes.firstOrNull { it.id == selectedEpisodeIdFlow.value } ?: episodes.firstOrNull()

    private fun ensureSeasonSelected() {
        val current = selectedSeasonIdFlow.value
        if (current == null || seasons.none { it.id == current }) {
            selectedSeasonIdFlow.value = seasons.firstOrNull()?.id
        }
    }

    private fun ensureEpisodeSelected() {
        val current = selectedEpisodeIdFlow.value
        if (current == null || episodes.none { it.id == current }) {
            selectedEpisodeIdFlow.value = episodes.firstOrNull()?.id
        }
    }

    private fun rebuild() {
        val selectedEpisode = episodes.firstOrNull { it.id == selectedEpisodeIdFlow.value } ?: episodes.firstOrNull()
        _uiState.value = SeriesDetailUiState(
            loaded = loaded,
            series = series,
            isFavorite = isFavorite,
            seasons = seasons,
            selectedSeasonId = selectedSeasonIdFlow.value,
            episodes = episodes,
            selectedEpisodeId = selectedEpisodeIdFlow.value,
            selectedEpisode = selectedEpisode,
            selectedEpisodeProgress = selectedEpisodeProgress,
            continueTarget = continueTarget,
        )
    }
}

internal class SeriesDetailViewModelFactory(
    private val providerStableKey: String,
    private val seriesStableKey: String,
    private val initialEpisodeStableKey: String?,
    private val mediaRepository: MediaRepository,
    private val favoritesRepository: FavoritesRepository,
    private val playbackRepository: PlaybackRepository,
    private val ensureSeriesDetail: suspend (Series) -> Unit = {},
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SeriesDetailViewModel(
            providerStableKey = providerStableKey,
            seriesStableKey = seriesStableKey,
            initialEpisodeStableKey = initialEpisodeStableKey,
            mediaRepository = mediaRepository,
            favoritesRepository = favoritesRepository,
            playbackRepository = playbackRepository,
            ensureSeriesDetail = ensureSeriesDetail,
        ) as T
    }
}

internal fun Episode.completedProgress(existing: PlaybackProgress?, now: Long): PlaybackProgress =
    PlaybackProgress(
        id = existing?.id ?: playbackProgressId(providerId, MediaType.Episode, id),
        providerId = providerId,
        mediaType = MediaType.Episode,
        mediaId = id,
        positionMillis = existing?.durationMillis?.takeIf { it > 0L } ?: existing?.positionMillis?.takeIf { it > 0L } ?: 1L,
        durationMillis = existing?.durationMillis?.takeIf { it > 0L } ?: 1L,
        progressPercent = 100,
        isCompleted = true,
        lastWatchedAt = now,
        createdAt = existing?.createdAt ?: now,
        updatedAt = now,
    )

private fun playbackProgressId(providerId: String, mediaType: MediaType, mediaId: String): String =
    "progress-$providerId-${mediaType.name.lowercase(Locale.getDefault())}-$mediaId"
