package com.vivicast.tv.feature.movies

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryType
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.PlaybackProgress
import com.vivicast.tv.domain.model.Provider
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
    onOpenDetail: (providerStableKey: String, movieStableKey: String) -> Unit = { _, _ -> },
) {
    if (providerRepository == null || mediaRepository == null || favoritesRepository == null || playbackRepository == null) {
        MoviesUnavailableState()
    } else {
        RoomMoviesRoute(
            providerRepository = providerRepository,
            mediaRepository = mediaRepository,
            favoritesRepository = favoritesRepository,
            playbackRepository = playbackRepository,
            resolveMoviePosterModel = resolveMoviePosterModel,
            resolveMovieBackdropModel = resolveMovieBackdropModel,
            onOpenDetail = onOpenDetail,
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
    onOpenDetail: (providerStableKey: String, movieStableKey: String) -> Unit,
) {
    val viewModel: MoviesViewModel = viewModel(
        factory = MoviesViewModelFactory(
            providerRepository = providerRepository,
            mediaRepository = mediaRepository,
            favoritesRepository = favoritesRepository,
            playbackRepository = playbackRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val strFavorites = stringResource(R.string.common_favorites)
    val strContinue = stringResource(R.string.movies_continue)
    val strMovieTypeBadge = stringResource(R.string.movies_type_badge)

    // Pure-visual hero highlight kept in the composable layer.
    var selectedMovieId by remember { mutableStateOf<String?>(null) }

    val categoriesWithSpecials = remember(uiState.selectedProviderId, uiState.categories, uiState.hasContinueMovies, strFavorites, strContinue) {
        val providerId = uiState.selectedProviderId
        if (providerId != null) {
            buildList {
                add(specialCategory(providerId, FAVORITES_CATEGORY_ID, strFavorites))
                if (uiState.hasContinueMovies) {
                    add(specialCategory(providerId, CONTINUE_CATEGORY_ID, strContinue))
                }
                addAll(uiState.categories)
            }
        } else {
            uiState.categories
        }
    }

    val movies = uiState.movies
    val favoriteMovieIds = uiState.favoriteMovieIds
    val continueMovieProgress = uiState.continueProgressByMovieId

    LaunchedEffect(movies) {
        if (selectedMovieId == null || movies.none { it.id == selectedMovieId }) {
            selectedMovieId = movies.firstOrNull()?.id
        }
    }

    val selectedMovie = movies.firstOrNull { it.id == selectedMovieId }
    val selectedProvider = uiState.selectedProvider
    val selectedCategory = categoriesWithSpecials.firstOrNull { it.id == uiState.selectedCategoryId }
    val selectedProgress = selectedMovie?.let { continueMovieProgress[it.id] }
    val backdropModel by produceState<Any?>(initialValue = null, selectedMovie?.id, selectedMovie?.backdropUrl) {
        value = selectedMovie?.let { resolveMovieBackdropModel(it) }
    }
    val canLoadMoreMovies = uiState.canLoadMore

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4), modifier = Modifier.fillMaxSize()) {
                MovieCategoryColumn(
                    providers = uiState.providers,
                    selectedProviderId = uiState.selectedProviderId,
                    categories = categoriesWithSpecials,
                    selectedCategoryId = uiState.selectedCategoryId,
                    onProviderSelected = {
                        viewModel.onProviderSelected(it.id)
                        selectedMovieId = null
                    },
                    onCategorySelected = {
                        viewModel.onCategorySelected(it.id)
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
                        onOpenPlayer = { },
                        onToggleFavorite = { selectedMovie?.id?.let { viewModel.onToggleFavorite(it) } },
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
                                                selectedProvider?.stableKey?.let { onOpenDetail(it, movie.stableKey) }
                                                true
                                            }
                                        },
                                    onFocused = { selectedMovieId = movie.id },
                                    onClick = {
                                        selectedMovieId = movie.id
                                        selectedProvider?.stableKey?.let { onOpenDetail(it, movie.stableKey) }
                                    },
                                )
                            }
                            if (canLoadMoreMovies) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "load-more-movies") {
                                    ActionPill(
                                        stringResource(R.string.common_load_more),
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { viewModel.onLoadMore() },
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
internal fun MovieHero(
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

internal fun Movie.detailBody(
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

internal fun openMovieTrailer(context: Context, movie: Movie): Boolean {
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
