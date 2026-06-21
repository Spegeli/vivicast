package com.vivicast.tv.feature.movies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.PosterCard
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.StatusBadge
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.data.media.AssetState
import com.vivicast.tv.data.media.DemoCatalog
import com.vivicast.tv.data.media.DemoVodItem

@Composable
fun MoviesRoute(onOpenPlayer: () -> Unit = {}) {
    var selectedCategory by remember { mutableStateOf("Fortsetzen") }
    var selectedMovie by remember { mutableStateOf(DemoCatalog.movies.first()) }
    val categories = listOf("Fortsetzen", "Sci-Fi", "Favoriten", "Gesehen", "Ohne Poster")

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
            MovieHero(selectedMovie, onOpenPlayer)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(categories) { category ->
                    ActionPill(
                        label = category,
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                    )
                }
            }
            SectionTitle("Filme")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
                items(DemoCatalog.movies) { movie ->
                    PosterCard(
                        title = movie.title,
                        rating = movie.rating,
                        meta = "${movie.year} | ${movie.runtime}",
                        hasPoster = movie.posterState == AssetState.Available,
                        progressPercent = movie.progressPercent,
                        favorite = movie.favorite,
                        seen = movie.seen,
                        onClick = { selectedMovie = movie },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusBadge("Bewertung auf Card")
                StatusBadge("Titel unter Poster")
                StatusBadge("Fallback sichtbar")
                StatusBadge("Fortsetzen-Fortschritt")
            }
        }
    }
}

@Composable
private fun MovieHero(movie: DemoVodItem, onOpenPlayer: () -> Unit) {
    InfoPanel(
        title = movie.title,
        body = movie.description.ifBlank { "Fallback: keine Beschreibung vorhanden." },
        badge = if (movie.backdropState == AssetState.Available) "Backdrop Demo" else "Backdrop Fallback",
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionPill("Player Overlay", onClick = onOpenPlayer)
        StatusBadge("Rating ${movie.rating}")
        if (movie.favorite) StatusBadge("Favorit")
        if (movie.seen) StatusBadge("Gesehen")
    }
}
