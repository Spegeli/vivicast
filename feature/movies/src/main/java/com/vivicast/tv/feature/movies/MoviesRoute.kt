package com.vivicast.tv.feature.movies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.ui.unit.dp
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.HeroPanel
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.PosterCard
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.StatusBadge
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.data.favorites.FavoritesRepository
import com.vivicast.tv.data.media.AssetState
import com.vivicast.tv.data.media.DemoCatalog
import com.vivicast.tv.data.media.DemoVodItem
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryType
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.Provider
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun MoviesRoute(
    providerRepository: ProviderRepository? = null,
    mediaRepository: MediaRepository? = null,
    favoritesRepository: FavoritesRepository? = null,
    resolveMoviePosterModel: suspend (Movie) -> Any? = { null },
    resolveMovieBackdropModel: suspend (Movie) -> Any? = { null },
    onOpenPlayer: (Movie) -> Unit = {},
) {
    if (providerRepository == null || mediaRepository == null || favoritesRepository == null) {
        DemoMoviesRoute()
    } else {
        RoomMoviesRoute(
            providerRepository = providerRepository,
            mediaRepository = mediaRepository,
            favoritesRepository = favoritesRepository,
            resolveMoviePosterModel = resolveMoviePosterModel,
            resolveMovieBackdropModel = resolveMovieBackdropModel,
            onOpenPlayer = onOpenPlayer,
        )
    }
}

@Composable
private fun RoomMoviesRoute(
    providerRepository: ProviderRepository,
    mediaRepository: MediaRepository,
    favoritesRepository: FavoritesRepository,
    resolveMoviePosterModel: suspend (Movie) -> Any?,
    resolveMovieBackdropModel: suspend (Movie) -> Any?,
    onOpenPlayer: (Movie) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val providers by providerRepository.observeProviders().collectAsState(initial = emptyList())
    var selectedProviderId by remember { mutableStateOf<String?>(null) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedMovieId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(providers) {
        val movieProviders = providers.filter { it.includeMovies }
        if (selectedProviderId == null || providers.none { it.id == selectedProviderId }) {
            selectedProviderId = movieProviders.firstOrNull { it.isActive }?.id
                ?: movieProviders.firstOrNull()?.id
                ?: providers.firstOrNull { it.isActive }?.id
                ?: providers.firstOrNull()?.id
        }
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
    val categoriesWithFavorites = remember(selectedProviderId, categories) {
        selectedProviderId?.let { listOf(favoriteCategory(it, CategoryType.Movies)) + categories } ?: categories
    }

    LaunchedEffect(selectedProviderId, categoriesWithFavorites, favoriteMovieIds) {
        if (selectedCategoryId == null || categoriesWithFavorites.none { it.id == selectedCategoryId }) {
            selectedCategoryId = when {
                favoriteMovieIds.isNotEmpty() -> FAVORITES_CATEGORY_ID
                else -> categories.firstOrNull()?.id ?: categoriesWithFavorites.firstOrNull()?.id
            }
        }
    }

    val moviesFlow = remember(selectedProviderId, selectedCategoryId) {
        selectedProviderId?.let { providerId ->
            mediaRepository.observeMovies(providerId, selectedCategoryId?.takeUnless { it == FAVORITES_CATEGORY_ID })
        } ?: flowOf(emptyList())
    }
    val observedMovies by moviesFlow.collectAsState(initial = emptyList())
    val movies = remember(observedMovies, selectedCategoryId, favoriteMovieIds, favoriteOrder) {
        if (selectedCategoryId == FAVORITES_CATEGORY_ID) {
            observedMovies
                .filter { it.id in favoriteMovieIds }
                .sortedWith(compareBy<Movie> { favoriteOrder[it.id] ?: Int.MAX_VALUE }.thenBy { it.name.lowercase(Locale.getDefault()) })
        } else {
            observedMovies
        }
    }

    LaunchedEffect(movies) {
        if (selectedMovieId == null || movies.none { it.id == selectedMovieId }) {
            selectedMovieId = movies.firstOrNull()?.id
        }
    }

    val selectedMovie = movies.firstOrNull { it.id == selectedMovieId }
    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }
    val selectedCategory = categoriesWithFavorites.firstOrNull { it.id == selectedCategoryId }
    val backdropModel by produceState<Any?>(initialValue = null, selectedMovie?.id, selectedMovie?.backdropUrl) {
        value = selectedMovie?.let { resolveMovieBackdropModel(it) }
    }

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxSize()) {
            MovieHero(
                movie = selectedMovie,
                provider = selectedProvider,
                backdropModel = backdropModel,
                isFavorite = selectedMovie?.id in favoriteMovieIds,
                onOpenPlayer = { selectedMovie?.let(onOpenPlayer) },
                onToggleFavorite = {
                    val providerId = selectedProviderId
                    val movieId = selectedMovie?.id
                    if (providerId != null && movieId != null) {
                        scope.launch { favoritesRepository.toggleFavorite(providerId, MediaType.Movie, movieId) }
                    }
                },
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
                items(categoriesWithFavorites, key = { it.id }) { category ->
                    ActionPill(
                        label = category.displayName,
                        selected = selectedCategoryId == category.id,
                        onClick = {
                            selectedCategoryId = category.id
                            selectedMovieId = null
                        },
                    )
                }
            }
            SectionTitle("Filme")
            if (movies.isEmpty()) {
                InfoPanel(
                    title = emptyTitle(selectedProvider, selectedCategory),
                    body = emptyBody(selectedProvider, selectedCategory),
                    badge = "Leer",
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
                            meta = movie.cardMeta,
                            hasPoster = !movie.posterUrl.isNullOrBlank() || posterModel != null,
                            progressPercent = 0,
                            favorite = movie.id in favoriteMovieIds,
                            seen = false,
                            imageModel = posterModel,
                            onFocused = { selectedMovieId = movie.id },
                            onClick = { selectedMovieId = movie.id },
                        )
                    }
                }
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
    onOpenPlayer: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    HeroPanel(
        title = movie?.name ?: "Keine Filme",
        body = movie?.plot?.takeIf { it.isNotBlank() } ?: "Waehle einen Provider und eine Filmkategorie aus.",
        meta = movie?.heroMeta ?: provider?.name,
        modifier = Modifier.fillMaxWidth(),
        backdropModel = backdropModel,
        action = {
            if (movie != null) {
                ActionPill("Abspielen", onClick = onOpenPlayer)
                ActionPill(if (isFavorite) "Favorit" else "Merken", selected = isFavorite, onClick = onToggleFavorite)
            }
        },
    )
}

@Composable
private fun DemoMoviesRoute(onOpenPlayer: () -> Unit = {}) {
    var selectedCategory by remember { mutableStateOf("Fortsetzen") }
    var selectedMovie by remember { mutableStateOf(DemoCatalog.movies.first()) }
    val categories = listOf("Fortsetzen", "Sci-Fi", "Favoriten", "Gesehen", "Ohne Poster")

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxSize()) {
            DemoMovieHero(selectedMovie, onOpenPlayer)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
                items(categories) { category ->
                    ActionPill(
                        label = category,
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                    )
                }
            }
            SectionTitle("Filme")
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space5),
                verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(DemoCatalog.movies) { movie ->
                    PosterCard(
                        title = movie.title,
                        rating = movie.rating,
                        meta = "${movie.year} | ${movie.runtime}",
                        hasPoster = movie.posterState == AssetState.Available,
                        progressPercent = movie.progressPercent,
                        favorite = movie.favorite,
                        seen = movie.seen,
                        imageResId = movie.posterResId,
                        onFocused = { selectedMovie = movie },
                        onClick = { selectedMovie = movie },
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoMovieHero(movie: DemoVodItem, onOpenPlayer: () -> Unit) {
    HeroPanel(
        title = movie.title,
        body = movie.description.ifBlank { "Keine Beschreibung vorhanden." },
        meta = "Rating ${movie.rating} | ${movie.year} | ${movie.runtime} | Science Fiction",
        modifier = Modifier.fillMaxWidth(),
        backdropResId = movie.backdropResId,
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                ActionPill("Abspielen", onClick = onOpenPlayer)
                if (movie.favorite) StatusBadge("Favorit")
                if (movie.seen) StatusBadge("Gesehen")
            }
        },
    )
}

private fun favoriteCategory(providerId: String, type: CategoryType): Category =
    Category(
        id = FAVORITES_CATEGORY_ID,
        providerId = providerId,
        type = type,
        remoteId = FAVORITES_CATEGORY_ID,
        name = "Favoriten",
        sortOrder = Int.MIN_VALUE,
        isHidden = false,
    )

private fun emptyTitle(provider: Provider?, category: Category?): String =
    when {
        provider == null -> "Keine Wiedergabelisten"
        category?.id == FAVORITES_CATEGORY_ID -> "Keine Film-Favoriten"
        else -> "Keine Filme"
    }

private fun emptyBody(provider: Provider?, category: Category?): String =
    when {
        provider == null -> "Lege in den Einstellungen zuerst einen Provider an."
        category?.id == FAVORITES_CATEGORY_ID -> "Fuege Filme ueber die Merken-Aktion zu deinen Favoriten hinzu."
        category == null -> "Dieser Provider enthaelt keine importierten Filmkategorien."
        else -> "Diese Kategorie enthaelt keine importierten Filme."
    }

private val Category.displayName: String
    get() = if (remoteId == "__UNCATEGORIZED__") "Nicht kategorisiert" else name

private val Movie.cardMeta: String
    get() = listOfNotNull(year, rating?.takeIf { it.isNotBlank() }?.let { "Rating $it" }).joinToString(" | ").ifBlank { "Film" }

private val Movie.heroMeta: String?
    get() = listOfNotNull(
        rating?.takeIf { it.isNotBlank() }?.let { "Rating $it" },
        genre?.takeIf { it.isNotBlank() },
        year?.takeIf { it.isNotBlank() },
        duration.minutesLabel(),
    ).joinToString(" | ").ifBlank { null }

private fun Long?.minutesLabel(): String? {
    val seconds = this ?: return null
    if (seconds <= 0L) return null
    return "${seconds / 60L} Min"
}

private const val FAVORITES_CATEGORY_ID = "__FAVORITES__"
