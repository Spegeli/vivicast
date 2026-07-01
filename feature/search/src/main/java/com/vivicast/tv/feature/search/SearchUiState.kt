package com.vivicast.tv.feature.search

import com.vivicast.tv.domain.model.SearchResults

/**
 * Immutable presentation state for the search screen. Holds only renderable data;
 * localized strings, navigation and focus handling stay in the composable layer.
 */
data class SearchUiState(
    val query: String = "",
    val debouncedQuery: String = "",
    val history: List<String> = emptyList(),
    val results: SearchResults = EMPTY_SEARCH_RESULTS,
)

private val EMPTY_SEARCH_RESULTS = SearchResults(emptyList(), emptyList(), emptyList(), emptyList())
