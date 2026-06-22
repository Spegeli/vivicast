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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vivicast.tv.core.datastore.UserPreferences
import com.vivicast.tv.core.datastore.UserPreferencesStore
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.FocusPanel
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.VivicastContentRow
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.core.designsystem.VivicastSearchResultCard
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.data.media.DemoCatalog
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.media.R as MediaR
import com.vivicast.tv.domain.model.SearchResults
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SEARCH_DEBOUNCE_MS = 300L
private const val SEARCH_LIMIT_PER_TYPE = 20
private const val MAX_SEARCH_HISTORY = 20

@Composable
fun SearchRoute(
    mediaRepository: MediaRepository? = null,
    userPreferencesStore: UserPreferencesStore? = null,
) {
    if (mediaRepository == null || userPreferencesStore == null) {
        DemoSearchRoute()
    } else {
        RoomSearchRoute(
            mediaRepository = mediaRepository,
            userPreferencesStore = userPreferencesStore,
        )
    }
}

@Composable
private fun RoomSearchRoute(
    mediaRepository: MediaRepository,
    userPreferencesStore: UserPreferencesStore,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(SearchResults(emptyList(), emptyList(), emptyList(), emptyList())) }
    var debouncedQuery by remember { mutableStateOf("") }
    val preferences by userPreferencesStore.values.collectAsState(initial = UserPreferences())
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        delay(SEARCH_DEBOUNCE_MS)
        val trimmed = query.trim()
        debouncedQuery = trimmed
        results = mediaRepository.search(trimmed, SEARCH_LIMIT_PER_TYPE)
        if (trimmed.length >= 2) {
            userPreferencesStore.updateSearchHistory((listOf(trimmed) + preferences.searchHistory).cleanHistory())
        }
    }

    SearchContent(
        query = query,
        onQueryChanged = { query = it },
        history = preferences.searchHistory,
        results = results.toSearchGroups(),
        debouncedQuery = debouncedQuery,
        onHistorySelected = { query = it },
        onHistoryDeleted = { term ->
            scope.launch { userPreferencesStore.updateSearchHistory(preferences.searchHistory.filterNot { it.equals(term, ignoreCase = true) }) }
        },
        onClearHistory = {
            scope.launch { userPreferencesStore.updateSearchHistory(emptyList()) }
        },
        onVoiceClick = {},
    )
}

@Composable
private fun DemoSearchRoute() {
    var query by remember { mutableStateOf("dune") }
    val hasResults = query.equals("dune", ignoreCase = true)
    val results = if (hasResults) {
        listOf(
            SearchGroupData("Kanaele", DemoCatalog.searchResults.channels.map { SearchItem(it, "HD", null, false) }),
            SearchGroupData("Filme", DemoCatalog.searchResults.movies.map { SearchItem(it.title, "Film", it.rating, true, demoImageFor(it.title)) }),
            SearchGroupData("Serien", DemoCatalog.searchResults.series.map { SearchItem(it.title, "Serie", it.rating, true, demoImageFor(it.title)) }),
            SearchGroupData("EPG", DemoCatalog.searchResults.epg.map { SearchItem(it, "Programmtreffer", null, false) }),
        )
    } else {
        emptyList()
    }

    SearchContent(
        query = query,
        onQueryChanged = { query = it },
        history = listOf("Dune", "ARD", "Tatort"),
        results = results,
        debouncedQuery = query,
        onHistorySelected = { query = it },
        onHistoryDeleted = {},
        onClearHistory = {},
        onVoiceClick = { query = "dune" },
    )
}

@Composable
private fun SearchContent(
    query: String,
    onQueryChanged: (String) -> Unit,
    history: List<String>,
    results: List<SearchGroupData>,
    debouncedQuery: String,
    onHistorySelected: (String) -> Unit,
    onHistoryDeleted: (String) -> Unit,
    onClearHistory: () -> Unit,
    onVoiceClick: () -> Unit,
) {
    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4), modifier = Modifier.fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
                SearchField(
                    query = query,
                    onQueryChanged = onQueryChanged,
                    modifier = Modifier.weight(1f).height(96.dp),
                )
                ActionPill("Voice", modifier = Modifier.width(118.dp).height(96.dp), onClick = onVoiceClick)
            }

            if (history.isNotEmpty()) {
                SearchHistoryRow(
                    history = history,
                    onHistorySelected = onHistorySelected,
                    onHistoryDeleted = onHistoryDeleted,
                    onClearHistory = onClearHistory,
                )
            }

            if (debouncedQuery.isBlank()) {
                InfoPanel(
                    title = "Lokale Suche",
                    body = "Live-TV, Filme, Serien und EPG werden lokal durchsucht.",
                    badge = "Bereit",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (results.any { it.rows.isNotEmpty() }) {
                results.filter { it.rows.isNotEmpty() }.forEach { group ->
                    SearchGroup(group.title, group.rows)
                }
            } else {
                InfoPanel(
                    title = "Keine Treffer",
                    body = "Versuche eine andere Schreibweise, einen kuerzeren Suchbegriff oder Teil des Titels.",
                    badge = "Leer",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textFocusRequester = remember { FocusRequester() }
    FocusPanel(modifier = modifier, onClick = { textFocusRequester.requestFocus() }, contentPadding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BodyText("Suche / lokale Ergebnisse")
            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth().focusRequester(textFocusRequester),
                singleLine = true,
                cursorBrush = SolidColor(VivicastColors.FocusRing),
                textStyle = TextStyle(
                    color = VivicastColors.TextPrimary,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                decorationBox = { innerTextField ->
                    if (query.isBlank()) {
                        BasicText(
                            text = "Suche...",
                            style = TextStyle(
                                color = VivicastColors.TextSecondary,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                    innerTextField()
                },
            )
        }
    }
}

@Composable
private fun SearchHistoryRow(
    history: List<String>,
    onHistorySelected: (String) -> Unit,
    onHistoryDeleted: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    VivicastContentRow(title = "Suchverlauf", horizontalGap = VivicastSpacing.Space3) {
        items(history, key = { it.lowercase() }) { term ->
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.width(260.dp)) {
                FocusPanel(
                    contentPadding = VivicastSpacing.Space3,
                    modifier = Modifier.weight(1f).height(62.dp),
                    onClick = { onHistorySelected(term) },
                ) {
                    BasicText(
                        text = term,
                        style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ActionPill("x", modifier = Modifier.width(62.dp), onClick = { onHistoryDeleted(term) })
            }
        }
        item(key = "__clear_history__") {
            ActionPill("Leeren", modifier = Modifier.width(130.dp), onClick = onClearHistory)
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

private data class SearchGroupData(
    val title: String,
    val rows: List<SearchItem>,
)

private data class SearchItem(
    val title: String,
    val subtitle: String,
    val rating: String?,
    val posterLike: Boolean,
    val imageResId: Int? = null,
)

private fun SearchResults.toSearchGroups(): List<SearchGroupData> =
    listOf(
        SearchGroupData(
            title = "Kanaele",
            rows = channels.map { channel ->
                SearchItem(
                    title = channel.name,
                    subtitle = channel.channelNumber?.let { "Kanal $it" } ?: "Live-TV",
                    rating = null,
                    posterLike = false,
                )
            },
        ),
        SearchGroupData(
            title = "Filme",
            rows = movies.map { movie ->
                SearchItem(
                    title = movie.name,
                    subtitle = movie.year?.let { "Film $it" } ?: "Film",
                    rating = movie.rating,
                    posterLike = true,
                )
            },
        ),
        SearchGroupData(
            title = "Serien",
            rows = series.map { series ->
                SearchItem(
                    title = series.name,
                    subtitle = series.year?.let { "Serie $it" } ?: "Serie",
                    rating = series.rating,
                    posterLike = true,
                )
            },
        ),
        SearchGroupData(
            title = "EPG",
            rows = epgPrograms.map { program ->
                SearchItem(
                    title = program.title,
                    subtitle = program.subtitle ?: "Programmtreffer",
                    rating = null,
                    posterLike = false,
                )
            },
        ),
    )

private fun List<String>.cleanHistory(): List<String> =
    asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .take(MAX_SEARCH_HISTORY)
        .toList()

private fun demoImageFor(title: String): Int? = when (title) {
    "Dune", "Dune: Part Two" -> MediaR.drawable.demo_poster_dune
    "Dune: Prophecy", "Dune: The Sisterhood" -> MediaR.drawable.demo_poster_prophecy
    else -> null
}
