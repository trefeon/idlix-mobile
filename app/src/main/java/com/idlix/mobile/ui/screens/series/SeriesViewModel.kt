package com.idlix.mobile.ui.screens.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.model.SeriesItem
import com.idlix.data.repository.IdlixRepository
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

    init {
        loadSeries(reset = true)
    }

    fun selectGenre(genre: String) {
        if (_uiState.value.selectedGenre != genre) {
            _uiState.value = _uiState.value.copy(selectedGenre = genre, currentPage = 1)
            loadSeries(reset = true)
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true, currentPage = 1)
        loadSeries(reset = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMorePages) return
        _uiState.value = state.copy(isLoadingMore = true)
        loadSeries(reset = false)
    }

    private fun loadSeries(reset: Boolean) {
        viewModelScope.launch {
            val state = _uiState.value
            val targetPage = if (reset) 1 else state.currentPage + 1
            val genreParam = if (state.selectedGenre == "all") null else state.selectedGenre

            try {
                val response = repository.getSeries(page = targetPage, genre = genreParam)
                val newItems = response.data
                val updatedList = if (reset) newItems else state.seriesList + newItems

                _uiState.value = state.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    seriesList = updatedList,
                    currentPage = targetPage,
                    hasMorePages = newItems.isNotEmpty() && targetPage < (response.pagination?.totalPages ?: targetPage),
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = state.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    error = e.localizedMessage ?: "Failed to load series"
                )
            }
        }
    }
}
