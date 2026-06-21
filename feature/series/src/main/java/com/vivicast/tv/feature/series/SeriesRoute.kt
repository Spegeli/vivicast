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
import com.vivicast.tv.data.media.DemoSeriesItem

@Composable
fun SeriesRoute(onOpenPlayer: () -> Unit = {}) {
    var selectedSeries by remember { mutableStateOf(DemoCatalog.series.first()) }
    val categories = listOf("Fortsetzen", "Alle Serien", "Sci-Fi", "Ohne Poster")

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
            SeriesHero(selectedSeries, onOpenPlayer)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(categories) { category ->
                    ActionPill(label = category)
                }
            }
            SectionTitle("Serien")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
                items(DemoCatalog.series) { series ->
                    PosterCard(
                        title = series.title,
                        rating = series.rating,
                        meta = "${series.seasons} Staffel(n) | ${series.episodes} Episoden",
                        hasPoster = series.posterState == AssetState.Available,
                        progressPercent = if (series.progressLabel.contains("fortsetzen", ignoreCase = true)) 36 else 0,
                        favorite = false,
                        seen = series.progressLabel.contains("gesehen", ignoreCase = true),
                        onClick = { selectedSeries = series },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusBadge("Staffeln")
                StatusBadge("Episode gesehen")
                StatusBadge("Teilweise gesehen")
                StatusBadge("Naechste Episode")
            }
        }
    }
}

@Composable
private fun SeriesHero(series: DemoSeriesItem, onOpenPlayer: () -> Unit) {
    InfoPanel(
        title = series.title,
        body = series.description,
        badge = series.progressLabel,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionPill("Player Overlay", onClick = onOpenPlayer)
        StatusBadge("Rating ${series.rating}")
        StatusBadge("${series.seasons} Staffel(n)")
        StatusBadge("${series.episodes} Episoden")
    }
}
