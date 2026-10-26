package com.idlix.mobile.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.local.WatchHistoryStore
import com.idlix.data.model.MovieItem
import com.idlix.data.model.SeriesItem
import com.idlix.data.model.WatchHistoryItem
import com.idlix.data.repository.IdlixRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val bannerMovies: List<MovieItem> = emptyList(),
    val continueWatching: List<WatchHistoryItem> = emptyList(),
    val popularMovies: List<MovieItem> = emptyList(),
    val popularSeries: List<SeriesItem> = emptyList(),
    val trending: List<MovieItem> = emptyList(),
    val actionMovies: List<MovieItem> = emptyList(),
    val comedyMovies: List<MovieItem> = emptyList(),
    val error: String? = null
)

class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = IdlixRepository()
    private val historyStore = WatchHistoryStore.getInstance(application.applicationContext)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Collect unfinished watch history
        viewModelScope.launch {
            historyStore.historyFlow.collect { items ->
                val unfinished = items.filter { it.isUnfinished }
                _uiState.value = _uiState.value.copy(continueWatching = unfinished)
            }
        }
        loadData()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            if (_uiState.value.bannerMovies.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            }
            try {
                var popular = repository.getPopularMovies()
                var series = repository.getPopularSeries()
                var trending = repository.getTrendingMovies()

                // If all returned empty, wait 1s and retry once (network socket warmup on startup)
                if (popular.isEmpty() && trending.isEmpty() && series.isEmpty()) {
                    kotlinx.coroutines.delay(1000)
                    popular = repository.getPopularMovies()
                    series = repository.getPopularSeries()
                    trending = repository.getTrendingMovies()
                }

                if (popular.isEmpty() && trending.isEmpty() && series.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = "Gagal memuat rekomendasi film & serial. Silakan periksa koneksi internet."
                    )
                    return@launch
                }

                val action = repository.getMoviesByGenre("action")
                val comedy = repository.getMoviesByGenre("comedy")

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    bannerMovies = trending.ifEmpty { popular }.take(5),
                    popularMovies = popular,
                    popularSeries = series,
                    trending = trending,
                    actionMovies = action,
                    comedyMovies = comedy,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = e.localizedMessage ?: "Failed to load data"
                )
            }
        }
    }
}
