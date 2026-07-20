package com.vivicast.tv.feature.movies

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.R
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.data.favorites.FavoritesRepository
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.playback.PlaybackRepository
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.PlaybackProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Self-contained movie-detail destination. Loads its movie by STABLE keys (works on direct entry / deep-link
 * with no loaded grid), and owns favorite / mark-seen / trailer / play. BACK is handled by the NavHost (pop
 * to the grid); the "Zurück" action calls [onBack] (navigateUp). App-hoisted bits — backdrop resolution,
 * player open, trailer intent — come in as callbacks.
 */
@Composable
fun MovieDetailScreen(
    providerStableKey: String,
    movieStableKey: String,
    mediaRepository: MediaRepository? = null,
    favoritesRepository: FavoritesRepository? = null,
    playbackRepository: PlaybackRepository? = null,
    resolveMovieBackdropModel: suspend (Movie) -> Any? = { null },
    onOpenPlayer: (Movie, Boolean) -> Unit = { _, _ -> },
    openTrailer: ((Movie) -> Boolean)? = null,
    onBack: () -> Unit = {},
) {
    if (mediaRepository == null || favoritesRepository == null || playbackRepository == null) {
        MovieDetailUnavailable()
        return
    }
    val context = LocalContext.current
    val viewModel: MovieDetailViewModel = viewModel(
        factory = MovieDetailViewModelFactory(
            providerStableKey = providerStableKey,
            movieStableKey = movieStableKey,
            mediaRepository = mediaRepository,
            favoritesRepository = favoritesRepository,
            playbackRepository = playbackRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val trailerHintStr = stringResource(R.string.movies_trailer_hint)
    var trailerHint by remember { mutableStateOf<String?>(null) }
    val effectiveOpenTrailer = openTrailer ?: { movie -> openMovieTrailer(context, movie) }

    val movie = uiState.movie
    val backdropModel by produceState<Any?>(initialValue = null, movie?.id, movie?.backdropUrl) {
        value = movie?.let { resolveMovieBackdropModel(it) }
    }

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxSize()) {
            when {
                movie != null -> MovieDetailPage(
                    movie = movie,
                    backdropModel = backdropModel,
                    isFavorite = uiState.isFavorite,
                    progress = uiState.progress,
                    onOpenPlayer = { resumeProgress -> onOpenPlayer(movie, resumeProgress) },
                    onToggleFavorite = { viewModel.onToggleFavorite() },
                    onMarkSeen = { viewModel.onMarkSeen() },
                    onMarkUnseen = { viewModel.onMarkUnseen() },
                    onOpenTrailer = {
                        trailerHint = if (effectiveOpenTrailer(movie)) null else trailerHintStr
                    },
                    onClose = onBack,
                    trailerHint = trailerHint,
                )
                uiState.loaded -> MovieDetailUnavailable()
                else -> Unit // still loading
            }
        }
    }
}

@Composable
private fun MovieDetailUnavailable() {
    InfoPanel(
        title = stringResource(R.string.movies_unavailable),
        body = stringResource(R.string.movies_no_content),
        badge = stringResource(R.string.common_empty_badge),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MovieDetailPage(
    movie: Movie,
    backdropModel: Any?,
    isFavorite: Boolean,
    progress: PlaybackProgress?,
    onOpenPlayer: (Boolean) -> Unit,
    onToggleFavorite: () -> Unit,
    onMarkSeen: () -> Unit,
    onMarkUnseen: () -> Unit,
    onOpenTrailer: () -> Unit,
    onClose: () -> Unit,
    trailerHint: String?,
) {
    MovieHero(
        movie = movie,
        provider = null,
        backdropModel = backdropModel,
        isFavorite = isFavorite,
        progress = progress,
        showActions = false,
        onOpenPlayer = onOpenPlayer,
        onToggleFavorite = onToggleFavorite,
    )
    val playFromStartStr = stringResource(R.string.movies_play_from_start)
    val watchedStr = stringResource(R.string.movies_watched)
    val trailerStr = stringResource(R.string.movies_trailer)
    val detailsTitleStr = stringResource(R.string.movies_details_title)
    val typeBadgeStr = stringResource(R.string.movies_type_badge)
    val noDescStr = stringResource(R.string.movies_no_description)
    val labelDirector = stringResource(R.string.movies_label_director)
    val labelCast = stringResource(R.string.movies_label_cast)
    val labelProgress = stringResource(R.string.movies_label_progress)
    Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
        if (progress?.isCompleted == true) {
            ActionPill(watchedStr, selected = true)
            ActionPill(playFromStartStr, onClick = { onOpenPlayer(false) })
        } else if (progress != null) {
            ActionPill(stringResource(R.string.movies_continue), onClick = { onOpenPlayer(true) })
            ActionPill(playFromStartStr, onClick = { onOpenPlayer(false) })
        } else {
            ActionPill(stringResource(R.string.movies_play), onClick = { onOpenPlayer(false) })
        }
        ActionPill(trailerStr, onClick = onOpenTrailer)
        ActionPill(if (isFavorite) stringResource(R.string.common_favorites) else stringResource(R.string.movies_add_favorite), selected = isFavorite, onClick = onToggleFavorite)
        if (progress?.isCompleted == true) {
            ActionPill(stringResource(R.string.movies_mark_unwatched), onClick = onMarkUnseen)
        } else {
            ActionPill(stringResource(R.string.movies_mark_watched), onClick = onMarkSeen)
        }
        ActionPill(stringResource(R.string.movies_back), onClick = onClose)
    }
    InfoPanel(
        title = detailsTitleStr,
        body = movie.detailBody(progress, noDescStr, labelDirector, labelCast, labelProgress),
        badge = if (progress?.isCompleted == true) watchedStr else progress?.let { "${it.progressPercent} %" } ?: typeBadgeStr,
        modifier = Modifier.fillMaxWidth(),
    )
    if (trailerHint != null) {
        InfoPanel(
            title = trailerStr,
            body = trailerHint,
            badge = "YouTube",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal data class MovieDetailUiState(
    val loaded: Boolean = false,
    val movie: Movie? = null,
    val progress: PlaybackProgress? = null,
    val isFavorite: Boolean = false,
)

internal class MovieDetailViewModel(
    private val providerStableKey: String,
    private val movieStableKey: String,
    private val mediaRepository: MediaRepository,
    private val favoritesRepository: FavoritesRepository,
    private val playbackRepository: PlaybackRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    scope: CoroutineScope? = null,
) : ViewModel() {

    private val coroutineScope: CoroutineScope = scope ?: viewModelScope
    private var movie: Movie? = null
    private var progress: PlaybackProgress? = null
    private var favoriteIds: Set<String> = emptySet()
    private var loaded = false

    private val _uiState = MutableStateFlow(MovieDetailUiState())
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    init {
        coroutineScope.launch {
            val loadedMovie = mediaRepository.getMovieByStableKeys(providerStableKey, movieStableKey)
            movie = loadedMovie
            loaded = true
            progress = loadedMovie?.let { playbackRepository.getProgress(it.providerId, MediaType.Movie, it.id) }
            rebuild()
            if (loadedMovie != null) {
                favoritesRepository.observeFavorites(loadedMovie.providerId, MediaType.Movie).collect { favs ->
                    favoriteIds = favs.mapTo(mutableSetOf()) { it.mediaId }
                    rebuild()
                }
            }
        }
    }

    fun onToggleFavorite() {
        val current = movie ?: return
        coroutineScope.launch { favoritesRepository.toggleFavorite(current.providerId, MediaType.Movie, current.id) }
    }

    fun onMarkSeen() {
        val current = movie ?: return
        coroutineScope.launch {
            val completed = current.completedProgress(progress, nowProvider())
            playbackRepository.saveProgress(completed)
            progress = completed
            rebuild()
        }
    }

    fun onMarkUnseen() {
        val current = movie ?: return
        coroutineScope.launch {
            playbackRepository.deleteProgress(current.providerId, MediaType.Movie, current.id)
            progress = null
            rebuild()
        }
    }

    private fun rebuild() {
        val current = movie
        _uiState.value = MovieDetailUiState(
            loaded = loaded,
            movie = current,
            progress = progress,
            isFavorite = current != null && current.id in favoriteIds,
        )
    }
}

internal class MovieDetailViewModelFactory(
    private val providerStableKey: String,
    private val movieStableKey: String,
    private val mediaRepository: MediaRepository,
    private val favoritesRepository: FavoritesRepository,
    private val playbackRepository: PlaybackRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MovieDetailViewModel(
            providerStableKey = providerStableKey,
            movieStableKey = movieStableKey,
            mediaRepository = mediaRepository,
            favoritesRepository = favoritesRepository,
            playbackRepository = playbackRepository,
        ) as T
    }
}

internal fun Movie.completedProgress(existing: PlaybackProgress?, now: Long): PlaybackProgress =
    PlaybackProgress(
        id = existing?.id ?: playbackProgressId(providerId, MediaType.Movie, id),
        providerId = providerId,
        mediaType = MediaType.Movie,
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
