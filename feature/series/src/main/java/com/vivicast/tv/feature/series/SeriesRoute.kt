package com.vivicast.tv.feature.series

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
import com.vivicast.tv.data.media.DemoSeriesItem

@Composable
fun SeriesRoute(onOpenPlayer: () -> Unit = {}) {
    var selectedSeries by remember { mutableStateOf(DemoCatalog.series.first()) }
    val categories = listOf("Fortsetzen", "Alle Serien", "Sci-Fi", "Ohne Poster")

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3), modifier = Modifier.fillMaxSize()) {
            SeriesHero(selectedSeries, onOpenPlayer)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
                items(categories) { category ->
                    ActionPill(label = category)
                }
            }
            VivicastContentRow(title = "Serien", horizontalGap = VivicastSpacing.Space5) {
                items(DemoCatalog.series) { series ->
                    PosterCard(
                        title = series.title,
                        rating = series.rating,
                        meta = "${series.seasons} Staffeln | ${series.episodes} Episoden",
                        hasPoster = series.posterState == AssetState.Available,
                        progressPercent = if (series.progressLabel.contains("fortsetzen", ignoreCase = true)) 36 else 0,
                        favorite = false,
                        seen = series.progressLabel.contains("gesehen", ignoreCase = true),
                        imageResId = series.posterResId,
                        onClick = { selectedSeries = series },
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesHero(series: DemoSeriesItem, onOpenPlayer: () -> Unit) {
    HeroPanel(
        title = series.title,
        body = series.description,
        meta = "Rating ${series.rating} | ${series.seasons} Staffeln | ${series.episodes} Episoden",
        modifier = Modifier.fillMaxWidth(),
        backdropResId = series.backdropResId,
        action = {
            StatusBadge(series.progressLabel)
        },
    )
}
