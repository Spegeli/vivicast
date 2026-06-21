package com.vivicast.tv.feature.movies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.HeroPanel
import com.vivicast.tv.core.designsystem.PosterCard
import com.vivicast.tv.core.designsystem.StatusBadge
import com.vivicast.tv.core.designsystem.VivicastContentRow
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.data.media.AssetState
import com.vivicast.tv.data.media.DemoCatalog
import com.vivicast.tv.data.media.DemoVodItem

@Composable
fun MoviesRoute(onOpenPlayer: () -> Unit = {}) {
    var selectedCategory by remember { mutableStateOf("Fortsetzen") }
    var selectedMovie by remember { mutableStateOf(DemoCatalog.movies.first()) }
    val categories = listOf("Fortsetzen", "Sci-Fi", "Favoriten", "Gesehen", "Ohne Poster")

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxSize()) {
            MovieHero(selectedMovie, onOpenPlayer)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
                items(categories) { category ->
                    ActionPill(
                        label = category,
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                    )
                }
            }
            VivicastContentRow(title = "Filme", horizontalGap = VivicastSpacing.Space5) {
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
                        onClick = { selectedMovie = movie },
                    )
                }
            }
        }
    }
}

@Composable
private fun MovieHero(movie: DemoVodItem, onOpenPlayer: () -> Unit) {
    HeroPanel(
        title = movie.title,
        body = movie.description.ifBlank { "Keine Beschreibung vorhanden." },
        meta = "Rating ${movie.rating} | ${movie.year} | ${movie.runtime} | Science Fiction",
        modifier = Modifier.fillMaxWidth(),
        backdropResId = movie.backdropResId,
        action = {
            if (movie.favorite) StatusBadge("Favorit")
            if (movie.seen) StatusBadge("Gesehen")
        },
    )
}
