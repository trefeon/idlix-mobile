package com.idlix.mobile.ui.screens.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.model.SeriesItem
import com.idlix.data.repository.IdlixRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SeriesUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val seriesList: List<SeriesItem> = emptyList(),
    val selectedGenre: String = "all",
    val currentPage: Int = 1,
    val hasMorePages: Boolean = true,
    val error: String? = null
)

class SeriesViewModel(
    private val repository: IdlixRepository = IdlixRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SeriesUiState())
    val uiState: StateFlow<SeriesUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var requestSeq = 0

    init {
        loadSeries(reset = true)
    }

    fun selectGenre(genre: String) {
        if (_uiState.value.selectedGenre != genre) {
            _uiState.value = _uiState.value.copy(
                selectedGenre = genre,
                currentPage = 1,
                isLoading = true,
                error = null,
                seriesList = emptyList()
            )
            loadSeries(reset = true)
        }
    }

    fun refresh() {
        loadJob?.cancel()
        _uiState.value = _uiState.value.copy(isRefreshing = true, currentPage = 1, error = null)
        loadSeries(reset = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || state.isLoadingMore || !state.hasMorePages) return
        _uiState.value = state.copy(isLoadingMore = true)
        loadSeries(reset = false)
    }

    private fun loadSeries(reset: Boolean) {
        if (reset) loadJob?.cancel()
        // Snapshot request params synchronously: a genre switch followed by a
        // scroll-triggered loadMore must not read each other's state mid-flight.
        val snap = _uiState.value
        val targetPage = if (reset) 1 else snap.currentPage + 1
        val genreParam = if (snap.selectedGenre == "all") null else snap.selectedGenre
        val baseList = if (reset) emptyList() else snap.seriesList
        val mySeq = ++requestSeq

        loadJob = viewModelScope.launch {
            try {
                val response = repository.getSeries(page = targetPage, genre = genreParam)
                if (mySeq != requestSeq) return@launch
                val newItems = response.data
                val updatedList = (if (reset) newItems else baseList + newItems).distinctBy { it.id }
                val totalPages = response.pagination?.totalPages
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    seriesList = updatedList,
                    currentPage = targetPage,
                    hasMorePages = if (totalPages != null) targetPage < totalPages else newItems.isNotEmpty(),
                    error = null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (mySeq != requestSeq) return@launch
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    error = e.localizedMessage ?: "Failed to load series"
                )
            }
        }
    }
}
