package com.vivicast.tv.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.FocusPanel
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.VivicastContentRow
import com.vivicast.tv.core.designsystem.VivicastSearchResultCard
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.data.media.DemoCatalog
import com.vivicast.tv.data.media.R as MediaR

@Composable
fun SearchRoute() {
    var query by remember { mutableStateOf("dune") }
    val hasResults = query.equals("dune", ignoreCase = true)

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4), modifier = Modifier.fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
                FocusPanel(modifier = Modifier.weight(1f).height(96.dp), onClick = { query = "dune" }, contentPadding = 18.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BodyText("Suche  /  lokale Ergebnisse")
                        BasicText(
                            text = "⌕  $query",
                            style = TextStyle(
                                color = VivicastColors.TextPrimary,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
                ActionPill("Voice", modifier = Modifier.width(118.dp).height(96.dp), onClick = { query = "dune" })
            }

            if (hasResults) {
                SearchGroup("Kanäle", DemoCatalog.searchResults.channels.map { SearchItem(it, "HD", null, false) })
                SearchGroup("Filme", DemoCatalog.searchResults.movies.map { SearchItem(it.title, "Film", it.rating, true, demoImageFor(it.title)) })
                SearchGroup("Serien", DemoCatalog.searchResults.series.map { SearchItem(it.title, "Serie", it.rating, true, demoImageFor(it.title)) })
                SearchGroup("EPG", DemoCatalog.searchResults.epg.map { SearchItem(it, "Programmtreffer", null, false) })
            } else {
                InfoPanel(
                    title = "Keine Treffer",
                    body = "Für '$query' existieren lokal keine Ergebnisse.",
                    badge = "Empty State",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SearchGroup(title: String, rows: List<SearchItem>) {
    VivicastContentRow(title = title, horizontalGap = VivicastSpacing.Space4) {
        items(rows) { item ->
            VivicastSearchResultCard(
                title = item.title,
                subtitle = item.subtitle,
                rating = item.rating,
                posterLike = item.posterLike,
                imageResId = item.imageResId,
            )
        }
    }
}

private data class SearchItem(
    val title: String,
    val subtitle: String,
    val rating: String?,
    val posterLike: Boolean,
    val imageResId: Int? = null,
)

private fun demoImageFor(title: String): Int? = when (title) {
    "Dune", "Dune: Part Two" -> MediaR.drawable.demo_poster_dune
    "Dune: Prophecy", "Dune: The Sisterhood" -> MediaR.drawable.demo_poster_prophecy
    else -> null
}
