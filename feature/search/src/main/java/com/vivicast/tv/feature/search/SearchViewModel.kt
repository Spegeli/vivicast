package com.vivicast.tv.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivicast.tv.data.media.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val SEARCH_DEBOUNCE_MS = 300L
internal const val SEARCH_LIMIT_PER_TYPE = 20
internal const val MAX_SEARCH_HISTORY = 20

/**
 * Presentation-state holder for the search screen. Consumes [MediaRepository],
 * exposes an immutable [SearchUiState] as [StateFlow] and receives user events.
 * No Android Context/Resources, no Compose types, no navigation here.
 *
 * The [scope] parameter allows unit tests to inject a controlled scope; in
 * production it defaults to [viewModelScope]. [debounceMillis] is injectable so
 * tests can disable debouncing.
 */
class SearchViewModel(
    private val mediaRepository: MediaRepository,
    scope: CoroutineScope? = null,
    private val debounceMillis: Long = SEARCH_DEBOUNCE_MS,
    private val searchLimitPerType: Int = SEARCH_LIMIT_PER_TYPE,
    historyLimit: Int = MAX_SEARCH_HISTORY,
) : ViewModel() {

    private val coroutineScope: CoroutineScope = scope ?: viewModelScope

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        coroutineScope.launch {
            mediaRepository.observeSearchHistory(historyLimit).collect { history ->
                _uiState.update { it.copy(history = history) }
            }
        }
        scheduleSearch(_uiState.value.query)
    }

    fun onQueryChanged(value: String) {
        _uiState.update { it.copy(query = value) }
        scheduleSearch(value)
    }

    fun onHistorySelected(term: String) {
        _uiState.update { it.copy(query = term) }
        scheduleSearch(term)
    }

    fun onHistoryRemoved(term: String) {
        coroutineScope.launch { mediaRepository.deleteSearchHistory(term) }
    }

    fun onClearHistory() {
        coroutineScope.launch { mediaRepository.clearSearchHistory() }
    }

    private fun scheduleSearch(rawQuery: String) {
        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            if (debounceMillis > 0) {
                delay(debounceMillis)
            }
            val trimmed = rawQuery.trim()
            val results = mediaRepository.search(trimmed, searchLimitPerType)
            if (trimmed.length >= 2) {
                mediaRepository.addSearchHistory(trimmed)
            }
            _uiState.update { it.copy(debouncedQuery = trimmed, results = results) }
        }
    }
}
