package com.idlix.mobile.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.model.SearchResultItem
import com.idlix.data.repository.IdlixRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<SearchResultItem> = emptyList(),
    val error: String? = null
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val repository: IdlixRepository = IdlixRepository()
) : ViewModel() {

    private val _queryFlow = MutableStateFlow("")
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        _queryFlow
            .debounce(400)
            .distinctUntilChanged()
            .onEach { query ->
                if (query.trim().length >= 2) {
                    performSearch(query.trim())
                } else {
                    searchJob?.cancel()
                    _uiState.value = _uiState.value.copy(isSearching = false, results = emptyList(), error = null)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(newQuery: String) {
        _queryFlow.value = newQuery
        _uiState.value = if (newQuery.trim().length >= 2) {
            // Instant feedback while the debounce window is still open; previous
            // results stay visible underneath until the new ones arrive.
            _uiState.value.copy(query = newQuery, isSearching = true, error = null)
        } else {
            // Clear stale results immediately instead of flashing them for 400ms
            // until the debounced blank branch fires.
            searchJob?.cancel()
            _uiState.value.copy(query = newQuery, isSearching = false, results = emptyList(), error = null)
        }
    }

    fun clearQuery() {
        onQueryChange("")
    }

    fun retry() {
        val query = _uiState.value.query
        if (query.trim().length >= 2) {
            performSearch(query.trim())
        }
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, error = null)
            try {
                val results = repository.search(query)
                // A newer keystroke may have started another search while this one
                // was in flight; drop stale responses so slow requests can't
                // overwrite fresh results.
                if (_uiState.value.query.trim() != query) return@launch
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    results = results,
                    error = null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_uiState.value.query.trim() != query) return@launch
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = e.localizedMessage ?: "Search failed"
                )
            }
        }
    }
}
