package com.vivicast.tv.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
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
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.StatusBadge
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.data.media.DemoCatalog

@Composable
fun SearchRoute() {
    var query by remember { mutableStateOf("dune") }
    val hasResults = query.equals("dune", ignoreCase = true)

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                FocusPanel(modifier = Modifier.weight(1f).height(76.dp), onClick = { query = "dune" }) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BodyText("Lokale Demo-Suche")
                        BasicText(
                            text = query,
                            style = TextStyle(
                                color = VivicastColors.TextPrimary,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
                ActionPill("Voice", modifier = Modifier.height(76.dp), onClick = { query = "dune" })
                ActionPill("Keine Treffer", modifier = Modifier.height(76.dp), onClick = { query = "xyz" })
            }

            if (hasResults) {
                SearchGroup("Kanaele", DemoCatalog.searchResults.channels.map { it to null })
                SearchGroup("Filme", DemoCatalog.searchResults.movies.map { it.title to it.rating })
                SearchGroup("Serien", DemoCatalog.searchResults.series.map { it.title to it.rating })
                SearchGroup("EPG", DemoCatalog.searchResults.epg.map { it to null })
            } else {
                InfoPanel(
                    title = "Keine Treffer",
                    body = "Fuer '$query' existieren in den lokalen Demo-Daten keine Ergebnisse.",
                    badge = "Empty State",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusBadge("Keine Alle-anzeigen-Aktion")
                StatusBadge("Horizontal scrollbar")
                StatusBadge("Bewertung bei VOD")
            }
        }
    }
}

@Composable
private fun SearchGroup(title: String, rows: List<Pair<String, String?>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            items(rows) { (label, rating) ->
                SearchResultCard(label = label, rating = rating)
            }
        }
    }
}

@Composable
private fun SearchResultCard(label: String, rating: String?) {
    FocusPanel(modifier = Modifier.height(78.dp), onClick = {}) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            BasicText(
                text = label,
                style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            )
            if (rating != null) {
                StatusBadge("Rating $rating")
            } else {
                BodyText("Demo-Ergebnis")
            }
        }
    }
}
