package com.vivicast.tv.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.R
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.FocusPanel
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.VivicastContentRow
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.core.designsystem.VivicastSearchResultCard
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.core.designsystem.VivicastTypography
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgProgram
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.SearchResults
import com.vivicast.tv.domain.model.Series
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SEARCH_DEBOUNCE_MS = 300L
private const val SEARCH_LIMIT_PER_TYPE = 20
private const val MAX_SEARCH_HISTORY = 20

@Composable
fun SearchRoute(
    mediaRepository: MediaRepository? = null,
    autoFocusField: Boolean = true,
    onOpenChannel: (Channel) -> Unit = {},
    onOpenMovie: (Movie) -> Unit = {},
    onOpenSeries: (Series) -> Unit = {},
    onOpenEpgProgram: (EpgProgram) -> Unit = {},
) {
    if (mediaRepository == null) {
        SearchUnavailableState()
    } else {
        RoomSearchRoute(
            mediaRepository = mediaRepository,
            autoFocusField = autoFocusField,
            onOpenChannel = onOpenChannel,
            onOpenMovie = onOpenMovie,
            onOpenSeries = onOpenSeries,
            onOpenEpgProgram = onOpenEpgProgram,
        )
    }
}

@Composable
private fun RoomSearchRoute(
    mediaRepository: MediaRepository,
    autoFocusField: Boolean,
    onOpenChannel: (Channel) -> Unit,
    onOpenMovie: (Movie) -> Unit,
    onOpenSeries: (Series) -> Unit,
    onOpenEpgProgram: (EpgProgram) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(SearchResults(emptyList(), emptyList(), emptyList(), emptyList())) }
    var debouncedQuery by remember { mutableStateOf("") }
    val history by mediaRepository.observeSearchHistory(MAX_SEARCH_HISTORY).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        delay(SEARCH_DEBOUNCE_MS)
        val trimmed = query.trim()
        debouncedQuery = trimmed
        results = mediaRepository.search(trimmed, SEARCH_LIMIT_PER_TYPE)
        if (trimmed.length >= 2) {
            mediaRepository.addSearchHistory(trimmed)
        }
    }

    val strChannels = stringResource(R.string.search_channels)
    val strMovies = stringResource(R.string.search_movies)
    val strSeries = stringResource(R.string.search_series)
    val strEpg = stringResource(R.string.search_epg)
    val strMovieType = stringResource(R.string.movies_type_badge)
    val strSeriesType = stringResource(R.string.series_type_badge)
    val strLiveTv = stringResource(R.string.search_subtitle_livetv)
    val strEpgHit = stringResource(R.string.search_subtitle_epg_hit)
    val strChannelPrefix = stringResource(R.string.search_subtitle_channel)
    SearchContent(
        query = query,
        onQueryChanged = { query = it },
        history = history,
        results = results.toSearchGroups(
            titleChannels = strChannels,
            titleMovies = strMovies,
            titleSeries = strSeries,
            titleEpg = strEpg,
            subtitleMovieType = strMovieType,
            subtitleSeriesType = strSeriesType,
            subtitleLiveTv = strLiveTv,
            subtitleEpgHit = strEpgHit,
            subtitleChannelPrefix = strChannelPrefix,
            onOpenChannel = onOpenChannel,
            onOpenMovie = onOpenMovie,
            onOpenSeries = onOpenSeries,
            onOpenEpgProgram = onOpenEpgProgram,
        ),
        debouncedQuery = debouncedQuery,
        autoFocusField = autoFocusField,
        onHistorySelected = { query = it },
        onHistoryDeleted = { term ->
            scope.launch { mediaRepository.deleteSearchHistory(term) }
        },
        onClearHistory = {
            scope.launch { mediaRepository.clearSearchHistory() }
        },
        onVoiceClick = {},
    )
}

@Composable
private fun SearchUnavailableState() {
    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        InfoPanel(
            title = stringResource(R.string.search_unavailable),
            body = stringResource(R.string.search_unavailable_body),
            badge = stringResource(R.string.search_local_ready),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SearchContent(
    query: String,
    onQueryChanged: (String) -> Unit,
    history: List<String>,
    results: List<SearchGroupData>,
    debouncedQuery: String,
    autoFocusField: Boolean,
    onHistorySelected: (String) -> Unit,
    onHistoryDeleted: (String) -> Unit,
    onClearHistory: () -> Unit,
    onVoiceClick: () -> Unit,
) {
    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4), modifier = Modifier.fillMaxWidth()) {
                SearchField(
                    query = query,
                    onQueryChanged = onQueryChanged,
                    autoFocus = autoFocusField,
                    modifier = Modifier.weight(1f).height(72.dp),
                )
                ActionPill(stringResource(R.string.search_microphone), modifier = Modifier.width(120.dp).testTag(searchVoiceTag()), onClick = onVoiceClick)
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
                    title = stringResource(R.string.search_local_title),
                    body = stringResource(R.string.search_local_body),
                    badge = stringResource(R.string.search_local_ready),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (results.any { it.rows.isNotEmpty() }) {
                results.filter { it.rows.isNotEmpty() }.forEach { group ->
                    SearchGroup(group.title, group.rows)
                }
            } else {
                InfoPanel(
                    title = stringResource(R.string.search_no_results),
                    body = stringResource(R.string.search_no_results_body),
                    badge = stringResource(R.string.common_empty_badge),
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
    autoFocus: Boolean,
    modifier: Modifier = Modifier,
) {
    val textFocusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            textFocusRequester.requestFocus()
        }
    }
    FocusPanel(modifier = modifier.testTag(searchFieldPanelTag()), onClick = { textFocusRequester.requestFocus() }, contentPadding = VivicastSpacing.Space3) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1)) {
            BodyText(stringResource(R.string.search_field_label))
            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth().focusRequester(textFocusRequester).testTag(searchInputTag()),
                singleLine = true,
                cursorBrush = SolidColor(VivicastColors.FocusRing),
                textStyle = VivicastTypography.TitleLarge.copy(color = VivicastColors.TextPrimary),
                decorationBox = { innerTextField ->
                    if (query.isBlank()) {
                        BasicText(
                            text = stringResource(R.string.search_placeholder),
                            style = VivicastTypography.TitleLarge.copy(color = VivicastColors.TextSecondary),
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
    VivicastContentRow(title = stringResource(R.string.search_recent), horizontalGap = VivicastSpacing.Space3) {
        items(history, key = { it.lowercase() }) { term ->
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.width(260.dp)) {
                FocusPanel(
                    contentPadding = VivicastSpacing.Space3,
                    modifier = Modifier.weight(1f).height(62.dp).testTag(searchHistoryTermTag(term)),
                    onClick = { onHistorySelected(term) },
                ) {
                    BasicText(
                        text = term,
                        style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextPrimary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ActionPill("x", modifier = Modifier.width(62.dp).testTag(searchHistoryDeleteTag(term)), onClick = { onHistoryDeleted(term) })
            }
        }
        item(key = "__clear_history__") {
            ActionPill(stringResource(R.string.search_history_clear), modifier = Modifier.width(130.dp).testTag(searchClearHistoryTag()), onClick = onClearHistory)
        }
    }
}

@Composable
private fun SearchGroup(title: String, rows: List<SearchItem>) {
    VivicastContentRow(title = title, modifier = Modifier.testTag(searchGroupTag(title)), horizontalGap = VivicastSpacing.Space4) {
        items(rows) { item ->
            VivicastSearchResultCard(
                title = item.title,
                subtitle = item.subtitle,
                rating = item.rating,
                posterLike = item.posterLike,
                imageResId = item.imageResId,
                modifier = Modifier.testTag(searchResultTag(title, item.title)),
                onClick = item.onClick,
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
    val onClick: () -> Unit = {},
)

private fun SearchResults.toSearchGroups(
    titleChannels: String,
    titleMovies: String,
    titleSeries: String,
    titleEpg: String,
    subtitleMovieType: String,
    subtitleSeriesType: String,
    subtitleLiveTv: String,
    subtitleEpgHit: String,
    subtitleChannelPrefix: String,
    onOpenChannel: (Channel) -> Unit,
    onOpenMovie: (Movie) -> Unit,
    onOpenSeries: (Series) -> Unit,
    onOpenEpgProgram: (EpgProgram) -> Unit,
): List<SearchGroupData> =
    listOf(
        SearchGroupData(
            title = titleChannels,
            rows = channels.map { channel ->
                SearchItem(
                    title = channel.name,
                    subtitle = channel.channelNumber?.let { subtitleChannelPrefix.format(it) } ?: subtitleLiveTv,
                    rating = null,
                    posterLike = false,
                    onClick = { onOpenChannel(channel) },
                )
            },
        ),
        SearchGroupData(
            title = titleMovies,
            rows = movies.map { movie ->
                SearchItem(
                    title = movie.name,
                    subtitle = movie.year?.let { "$subtitleMovieType $it" } ?: subtitleMovieType,
                    rating = movie.rating,
                    posterLike = true,
                    onClick = { onOpenMovie(movie) },
                )
            },
        ),
        SearchGroupData(
            title = titleSeries,
            rows = series.map { series ->
                SearchItem(
                    title = series.name,
                    subtitle = series.year?.let { "$subtitleSeriesType $it" } ?: subtitleSeriesType,
                    rating = series.rating,
                    posterLike = true,
                    onClick = { onOpenSeries(series) },
                )
            },
        ),
        SearchGroupData(
            title = titleEpg,
            rows = epgPrograms.map { program ->
                SearchItem(
                    title = program.title,
                    subtitle = program.subtitle ?: subtitleEpgHit,
                    rating = null,
                    posterLike = false,
                    onClick = { onOpenEpgProgram(program) },
                )
            },
        ),
    )

internal fun searchFieldPanelTag(): String = "search-field-panel"
internal fun searchInputTag(): String = "search-input"
internal fun searchVoiceTag(): String = "search-voice"
internal fun searchHistoryTermTag(term: String): String = "search-history-${term.searchTagToken()}"
internal fun searchHistoryDeleteTag(term: String): String = "search-history-delete-${term.searchTagToken()}"
internal fun searchClearHistoryTag(): String = "search-history-clear"
internal fun searchGroupTag(groupTitle: String): String = "search-group-${groupTitle.searchTagToken()}"
internal fun searchResultTag(groupTitle: String, title: String): String =
    "search-result-${groupTitle.searchTagToken()}-${title.searchTagToken()}"

private fun String.searchTagToken(): String =
    trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
