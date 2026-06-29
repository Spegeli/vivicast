package com.vivicast.tv.feature.movies

import androidx.activity.compose.BackHandler
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.R
import com.vivicast.tv.core.designsystem.HeroPanel
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.PosterCard
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.data.favorites.FavoritesRepository
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.playback.PlaybackRepository
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryType
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.PlaybackProgress
import com.vivicast.tv.domain.model.Provider
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.Locale

@Composable
fun MoviesRoute(
    providerRepository: ProviderRepository? = null,
    mediaRepository: MediaRepository? = null,
    favoritesRepository: FavoritesRepository? = null,
    playbackRepository: PlaybackRepository? = null,
    resolveMoviePosterModel: suspend (Movie) -> Any? = { null },
    resolveMovieBackdropModel: suspend (Movie) -> Any? = { null },
    openTrailer: ((Movie) -> Boolean)? = null,
    onOpenPlayer: (Movie, Boolean) -> Unit = { _, _ -> },
    targetProviderId: String? = null,
    targetCategoryId: String? = null,
    targetMovieId: String? = null,
    onTargetConsumed: () -> Unit = {},
) {
    if (providerRepository == null || mediaRepository == null || favoritesRepository == null || playbackRepository == null) {
        MoviesUnavailableState()
    } else {
        val context = LocalContext.current
        RoomMoviesRoute(
            providerRepository = providerRepository,
            mediaRepository = mediaRepository,
            favoritesRepository = favoritesRepository,
            playbackRepository = playbackRepository,
            resolveMoviePosterModel = resolveMoviePosterModel,
            resolveMovieBackdropModel = resolveMovieBackdropModel,
            openTrailer = openTrailer ?: { movie -> openMovieTrailer(context, movie) },
            onOpenPlayer = onOpenPlayer,
            targetProviderId = targetProviderId,
            targetCategoryId = targetCategoryId,
            targetMovieId = targetMovieId,
            onTargetConsumed = onTargetConsumed,
        )
    }
}

@Composable
private fun MoviesUnavailableState() {
    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        InfoPanel(
            title = stringResource(R.string.movies_unavailable),
            body = stringResource(R.string.movies_select_provider),
            badge = stringResource(R.string.common_empty_badge),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RoomMoviesRoute(
    providerRepository: ProviderRepository,
    mediaRepository: MediaRepository,
    favoritesRepository: FavoritesRepository,
    playbackRepository: PlaybackRepository,
    resolveMoviePosterModel: suspend (Movie) -> Any?,
    resolveMovieBackdropModel: suspend (Movie) -> Any?,
    openTrailer: (Movie) -> Boolean,
    onOpenPlayer: (Movie, Boolean) -> Unit,
    targetProviderId: String?,
    targetCategoryId: String?,
    targetMovieId: String?,
    onTargetConsumed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val trailerHintStr = stringResource(R.string.movies_trailer_hint)
    val strFavorites = stringResource(R.string.common_favorites)
    val strContinue = stringResource(R.string.movies_continue)
    val strMovieTypeBadge = stringResource(R.string.movies_type_badge)
    val providers by providerRepository.observeProviders().collectAsState(initial = emptyList())
    var selectedProviderId by remember { mutableStateOf<String?>(null) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedMovieId by remember { mutableStateOf<String?>(null) }
    var detailMovieId by remember { mutableStateOf<String?>(null) }
    var detailMovieProgress by remember { mutableStateOf<PlaybackProgress?>(null) }
    var trailerHint by remember { mutableStateOf<String?>(null) }
    var moviePageCount by remember { mutableStateOf(1) }

    BackHandler(enabled = detailMovieId != null) {
        detailMovieId = null
    }

    val movieProviders = remember(providers) { providers.filter { it.includeMovies } }

    LaunchedEffect(providers, movieProviders) {
        if (selectedProviderId == null || movieProviders.none { it.id == selectedProviderId }) {
            selectedProviderId = movieProviders.firstOrNull { it.isActive }?.id
                ?: movieProviders.firstOrNull()?.id
        }
    }
    LaunchedEffect(targetProviderId, targetCategoryId, targetMovieId) {
        if (targetMovieId == null) return@LaunchedEffect
        selectedProviderId = targetProviderId
        selectedCategoryId = targetCategoryId
    }

    val categoriesFlow = remember(selectedProviderId) {
        selectedProviderId?.let { mediaRepository.observeCategories(it, CategoryType.Movies) } ?: flowOf(emptyList())
    }
    val categories by categoriesFlow.collectAsState(initial = emptyList())
    val favoritesFlow = remember(selectedProviderId) {
        selectedProviderId?.let { favoritesRepository.observeFavorites(it, MediaType.Movie) } ?: flowOf(emptyList())
    }
    val favorites by favoritesFlow.collectAsState(initial = emptyList())
    val favoriteMovieIds = remember(favorites) { favorites.mapTo(mutableSetOf()) { it.mediaId } }
    val favoriteOrder = remember(favorites) { favorites.mapIndexed { index, favorite -> favorite.mediaId to index }.toMap() }
    val continueFlow = remember(selectedProviderId) {
        selectedProviderId?.let { playbackRepository.observeContinueWatching(it) } ?: flowOf(emptyList())
    }
    val continueProgress by continueFlow.collectAsState(initial = emptyList())
    val continueMovieProgress = remember(continueProgress) {
        continueProgress
            .filter { it.mediaType == MediaType.Movie }
            .associateBy { it.mediaId }
    }
    val continueOrder = remember(continueMovieProgress) {
        continueMovieProgress.values
            .sortedByDescending { it.lastWatchedAt }
            .mapIndexed { index, progress -> progress.mediaId to index }
            .toMap()
    }
    val categoriesWithSpecials = remember(selectedProviderId, categories, continueMovieProgress) {
        selectedProviderId?.let { providerId ->
            buildList {
                add(specialCategory(providerId, FAVORITES_CATEGORY_ID, strFavorites))
                if (continueMovieProgress.isNotEmpty()) {
                    add(specialCategory(providerId, CONTINUE_CATEGORY_ID, strContinue))
                }
                addAll(categories)
            }
        } ?: categories
    }

    LaunchedEffect(selectedProviderId, categoriesWithSpecials, favoriteMovieIds, continueMovieProgress) {
        if (selectedCategoryId == null || categoriesWithSpecials.none { it.id == selectedCategoryId }) {
            selectedCategoryId = categories.firstOrNull()?.id ?: categoriesWithSpecials.firstOrNull()?.id
        }
    }
    LaunchedEffect(selectedProviderId, selectedCategoryId) {
        moviePageCount = 1
    }

    val moviesFlow = remember(selectedProviderId, selectedCategoryId, moviePageCount) {
        selectedProviderId?.takeIf { selectedCategoryId !in SPECIAL_CATEGORY_IDS }?.let { providerId ->
            mediaRepository.observeMoviesPage(
                providerId = providerId,
                categoryId = selectedCategoryId,
                limit = moviePageCount * VOD_PAGE_SIZE,
            )
        } ?: flowOf(emptyList())
    }
    val observedMovies by moviesFlow.collectAsState(initial = emptyList())
    val favoriteMovies by produceState<List<Movie>>(initialValue = emptyList(), mediaRepository, favorites) {
        value = favorites.mapNotNull { mediaRepository.getMovie(it.providerId, it.mediaId) }
    }
    val continueMovies by produceState<List<Movie>>(initialValue = emptyList(), mediaRepository, continueMovieProgress) {
        value = continueMovieProgress.values
            .sortedByDescending { it.lastWatchedAt }
            .mapNotNull { mediaRepository.getMovie(it.providerId, it.mediaId) }
    }
    val movies = remember(observedMovies, selectedCategoryId, favoriteMovies, favoriteOrder, continueMovies, continueOrder) {
        when (selectedCategoryId) {
            CONTINUE_CATEGORY_ID -> continueMovies
                .sortedWith(compareBy<Movie> { continueOrder[it.id] ?: Int.MAX_VALUE }.thenBy { it.name.lowercase(Locale.getDefault()) })
            FAVORITES_CATEGORY_ID -> favoriteMovies
                .sortedWith(compareBy<Movie> { favoriteOrder[it.id] ?: Int.MAX_VALUE }.thenBy { it.name.lowercase(Locale.getDefault()) })
            else -> observedMovies
        }
    }

    LaunchedEffect(movies) {
        if (selectedMovieId == null || movies.none { it.id == selectedMovieId }) {
            selectedMovieId = movies.firstOrNull()?.id
        }
        if (detailMovieId != null && movies.isNotEmpty() && movies.none { it.id == detailMovieId }) {
            detailMovieId = null
        }
    }
    LaunchedEffect(targetMovieId, movies) {
        val movieId = targetMovieId ?: return@LaunchedEffect
        if (movies.any { it.id == movieId } || targetProviderId?.let { mediaRepository.getMovie(it, movieId) } != null) {
            selectedMovieId = movieId
            detailMovieId = movieId
            onTargetConsumed()
        }
    }

    val selectedMovie = movies.firstOrNull { it.id == selectedMovieId }
    val loadedDetailMovie by produceState<Movie?>(initialValue = null, mediaRepository, selectedProviderId, detailMovieId) {
        val providerId = selectedProviderId
        val movieId = detailMovieId
        value = if (providerId != null && movieId != null) {
            mediaRepository.getMovie(providerId, movieId)
        } else {
            null
        }
    }
    val detailMovie = movies.firstOrNull { it.id == detailMovieId } ?: loadedDetailMovie
    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }
    val selectedCategory = categoriesWithSpecials.firstOrNull { it.id == selectedCategoryId }
    val selectedProgress = selectedMovie?.let { continueMovieProgress[it.id] }
    val detailProgress = detailMovieProgress ?: detailMovie?.let { continueMovieProgress[it.id] }
    val backdropModel by produceState<Any?>(initialValue = null, selectedMovie?.id, selectedMovie?.backdropUrl) {
        value = selectedMovie?.let { resolveMovieBackdropModel(it) }
    }
    val canLoadMoreMovies = selectedCategoryId !in SPECIAL_CATEGORY_IDS &&
        observedMovies.size >= moviePageCount * VOD_PAGE_SIZE

    LaunchedEffect(selectedProviderId, detailMovieId) {
        val providerId = selectedProviderId
        val movieId = detailMovieId
        detailMovieProgress = if (providerId != null && movieId != null) {
            playbackRepository.getProgress(providerId, MediaType.Movie, movieId)
        } else {
            null
        }
    }

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxSize()) {
            if (detailMovie != null) {
                MovieDetailPage(
                    movie = detailMovie,
                    provider = selectedProvider,
                    backdropModel = backdropModel,
                    isFavorite = detailMovie.id in favoriteMovieIds,
                    progress = detailProgress,
                    onOpenPlayer = { resumeProgress -> onOpenPlayer(detailMovie, resumeProgress) },
                    onToggleFavorite = {
                        val providerId = selectedProviderId
                        if (providerId != null) {
                            scope.launch { favoritesRepository.toggleFavorite(providerId, MediaType.Movie, detailMovie.id) }
                        }
                    },
                    onMarkSeen = {
                        val providerId = selectedProviderId ?: return@MovieDetailPage
                        scope.launch {
                            val now = System.currentTimeMillis()
                            val completedProgress = detailMovie.completedProgress(detailProgress, now)
                            playbackRepository.saveProgress(completedProgress)
                            detailMovieProgress = completedProgress
                        }
                    },
                    onMarkUnseen = {
                        val providerId = selectedProviderId ?: return@MovieDetailPage
                        scope.launch {
                            playbackRepository.deleteProgress(providerId, MediaType.Movie, detailMovie.id)
                            detailMovieProgress = null
                        }
                    },
                    onOpenTrailer = {
                        trailerHint = if (openTrailer(detailMovie)) null else trailerHintStr
                    },
                    onClose = { detailMovieId = null },
                    trailerHint = trailerHint,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4), modifier = Modifier.fillMaxSize()) {
                    MovieCategoryColumn(
                        providers = movieProviders,
                        selectedProviderId = selectedProviderId,
                        categories = categoriesWithSpecials,
                        selectedCategoryId = selectedCategoryId,
                        onProviderSelected = {
                            selectedProviderId = it.id
                            selectedCategoryId = null
                            selectedMovieId = null
                        },
                        onCategorySelected = {
                            selectedCategoryId = it.id
                            selectedMovieId = null
                        },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.weight(1f)) {
                        MovieHero(
                            movie = selectedMovie,
                            provider = selectedProvider,
                            backdropModel = backdropModel,
                            isFavorite = selectedMovie?.id in favoriteMovieIds,
                            progress = selectedProgress,
                            showActions = false,
                            onOpenPlayer = { resumeProgress -> selectedMovie?.let { onOpenPlayer(it, resumeProgress) } },
                            onToggleFavorite = {
                                val providerId = selectedProviderId
                                val movieId = selectedMovie?.id
                                if (providerId != null && movieId != null) {
                                    scope.launch { favoritesRepository.toggleFavorite(providerId, MediaType.Movie, movieId) }
                                }
                            },
                        )
                        SectionTitle(stringResource(R.string.nav_movies))
                        if (movies.isEmpty()) {
                            InfoPanel(
                                title = emptyTitle(selectedProvider, selectedCategory),
                                body = emptyBody(selectedProvider, selectedCategory),
                                badge = stringResource(R.string.common_empty_badge),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 150.dp),
                                horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space5),
                                verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(movies, key = { it.id }) { movie ->
                                    val posterModel by produceState<Any?>(initialValue = null, movie.id, movie.posterUrl) {
                                        value = resolveMoviePosterModel(movie)
                                    }
                                    PosterCard(
                                        title = movie.name,
                                        rating = movie.rating?.takeIf { it.isNotBlank() } ?: "-",
                                        meta = continueMovieProgress[movie.id]?.let { "${it.progressPercent} %" } ?: movie.cardMeta(strMovieTypeBadge),
                                        hasPoster = !movie.posterUrl.isNullOrBlank() || posterModel != null,
                                        progressPercent = continueMovieProgress[movie.id]?.progressPercent ?: 0,
                                        favorite = movie.id in favoriteMovieIds,
                                        seen = false,
                                        imageModel = posterModel,
                                        surfaceModifier = Modifier
                                            .testTag(moviePosterTag(movie.id))
                                            .semantics {
                                                onClick {
                                                    selectedMovieId = movie.id
                                                    detailMovieId = movie.id
                                                    true
                                                }
                                            },
                                        onFocused = { selectedMovieId = movie.id },
                                        onClick = {
                                            selectedMovieId = movie.id
                                            detailMovieId = movie.id
                                        },
                                    )
                                }
                                if (canLoadMoreMovies) {
                                    item(span = { GridItemSpan(maxLineSpan) }, key = "load-more-movies") {
                                        ActionPill(
                                            stringResource(R.string.common_load_more),
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = { moviePageCount += 1 },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieCategoryColumn(
    providers: List<Provider>,
    selectedProviderId: String?,
    categories: List<Category>,
    selectedCategoryId: String?,
    onProviderSelected: (Provider) -> Unit,
    onCategorySelected: (Category) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.width(220.dp)) {
        SectionTitle(stringResource(R.string.common_provider_section))
        providers.forEach { provider ->
            ActionPill(
                label = provider.name,
                selected = selectedProviderId == provider.id,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onProviderSelected(provider) },
            )
        }
        SectionTitle(stringResource(R.string.common_categories_section))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.fillMaxSize()) {
            items(categories, key = { it.id }) { category ->
                ActionPill(
                    label = category.localizedDisplayName(),
                    selected = selectedCategoryId == category.id,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onCategorySelected(category) },
                )
            }
        }
    }
}

@Composable
private fun MovieHero(
    movie: Movie?,
    provider: Provider?,
    backdropModel: Any?,
    isFavorite: Boolean,
    progress: PlaybackProgress?,
    showActions: Boolean = true,
    onOpenPlayer: (Boolean) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val noMoviesStr = stringResource(R.string.movies_no_content)
    val selectProviderStr = stringResource(R.string.movies_select_provider)
    val playFromStartStr = stringResource(R.string.movies_play_from_start)
    HeroPanel(
        title = movie?.name ?: noMoviesStr,
        body = movie?.plot?.takeIf { it.isNotBlank() } ?: selectProviderStr,
        meta = movie?.localizedHeroMeta() ?: provider?.name,
        modifier = Modifier.fillMaxWidth(),
        backdropModel = backdropModel,
        action = {
            if (movie != null && showActions) {
                if (progress != null) {
                    ActionPill(stringResource(R.string.movies_continue), onClick = { onOpenPlayer(true) })
                    ActionPill(playFromStartStr, onClick = { onOpenPlayer(false) })
                } else {
                    ActionPill(stringResource(R.string.movies_play), onClick = { onOpenPlayer(false) })
                }
                ActionPill(if (isFavorite) stringResource(R.string.common_favorites) else stringResource(R.string.movies_add_favorite), selected = isFavorite, onClick = onToggleFavorite)
            }
        },
    )
}

@Composable
private fun MovieDetailPage(
    movie: Movie,
    provider: Provider?,
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
        provider = provider,
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

private fun specialCategory(providerId: String, id: String, name: String): Category =
    Category(
        id = id,
        providerId = providerId,
        type = CategoryType.Movies,
        remoteId = id,
        name = name,
        sortOrder = Int.MIN_VALUE,
        isHidden = false,
    )

@Composable
private fun emptyTitle(provider: Provider?, category: Category?): String =
    when {
        provider == null -> stringResource(R.string.common_no_playlists)
        category?.id == CONTINUE_CATEGORY_ID -> stringResource(R.string.movies_no_continue)
        category?.id == FAVORITES_CATEGORY_ID -> stringResource(R.string.movies_no_favorites)
        else -> stringResource(R.string.movies_none)
    }

@Composable
private fun emptyBody(provider: Provider?, category: Category?): String =
    when {
        provider == null -> stringResource(R.string.common_add_provider)
        category?.id == CONTINUE_CATEGORY_ID -> stringResource(R.string.movies_continue_body)
        category?.id == FAVORITES_CATEGORY_ID -> stringResource(R.string.movies_favorites_empty)
        category == null -> stringResource(R.string.movies_no_categories_body)
        else -> stringResource(R.string.movies_no_movies_body)
    }

@Composable
private fun Category.localizedDisplayName(): String =
    if (remoteId == "__UNCATEGORIZED__") stringResource(R.string.category_uncategorized) else name

private fun Movie.cardMeta(typeBadge: String): String =
    listOfNotNull(year, rating?.takeIf { it.isNotBlank() }?.let { "Rating $it" }).joinToString(" | ").ifBlank { typeBadge }

@Composable
private fun Movie.localizedHeroMeta(): String? = listOfNotNull(
    rating?.takeIf { it.isNotBlank() }?.let { "Rating $it" },
    genre?.takeIf { it.isNotBlank() },
    year?.takeIf { it.isNotBlank() },
    duration?.takeIf { it > 0L }?.let { stringResource(R.string.movies_duration_min, it / 60L) },
).joinToString(" | ").ifBlank { null }

private fun Movie.detailBody(
    progress: PlaybackProgress?,
    noDescFallback: String,
    labelDirector: String = "Regie",
    labelCast: String = "Besetzung",
    labelProgress: String = "Fortschritt",
): String =
    buildString {
        append(plot?.takeIf { it.isNotBlank() } ?: noDescFallback)
        val people = listOfNotNull(
            director?.takeIf { it.isNotBlank() }?.let { "$labelDirector: $it" },
            cast?.takeIf { it.isNotBlank() }?.let { "$labelCast: $it" },
        )
        if (people.isNotEmpty()) append("\n").append(people.joinToString(" | "))
        if (progress != null) append("\n$labelProgress: ${progress.progressPercent} %")
    }

private fun Movie.completedProgress(existing: PlaybackProgress?, now: Long): PlaybackProgress =
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

private fun openMovieTrailer(context: Context, movie: Movie): Boolean {
    val trailerUri = movie.trailerUrl
        ?.takeIf { it.isYouTubeUrl() }
        ?.let(Uri::parse)
        ?: Uri.parse("https://www.youtube.com/results?search_query=${URLEncoder.encode("${movie.name} Trailer", Charsets.UTF_8.name())}")
    return listOf("com.google.android.youtube.tv", "com.google.android.youtube").any { packageName ->
        val intent = Intent(Intent.ACTION_VIEW, trailerUri).setPackage(packageName)
        try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}

internal fun String.isYouTubeUrl(): Boolean {
    val host = runCatching { Uri.parse(this).host?.lowercase(Locale.ROOT) }.getOrNull()
    return host == "youtube.com" || host == "www.youtube.com" || host == "youtu.be"
}

internal fun moviePosterTag(movieId: String): String = "movie-poster-$movieId"

private const val FAVORITES_CATEGORY_ID = "__FAVORITES__"
private const val CONTINUE_CATEGORY_ID = "__CONTINUE__"
private val SPECIAL_CATEGORY_IDS = setOf(FAVORITES_CATEGORY_ID, CONTINUE_CATEGORY_ID)
private const val VOD_PAGE_SIZE = 80
