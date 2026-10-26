package com.idlix.mobile.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.local.BookmarkStore
import com.idlix.data.local.WatchHistoryStore
import com.idlix.data.model.BookmarkItem
import com.idlix.data.model.MovieItem
import com.idlix.data.model.SeriesItem
import com.idlix.data.model.WatchHistoryItem
import com.idlix.data.repository.IdlixRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    private val bookmarkStore = BookmarkStore.getInstance(application.applicationContext)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val bookmarkedSlugs: StateFlow<Set<String>> = bookmarkStore.bookmarkFlow
        .map { list -> list.mapNotNull { it.slug }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private var loadJob: Job? = null

    private val removedHistory = ArrayDeque<WatchHistoryItem>()

    init {
        // Collect unfinished watch history
        viewModelScope.launch {
            historyStore.historyFlow.collect { items ->
                val unfinished = items.filter { it.isUnfinished }
                _uiState.update { it.copy(continueWatching = unfinished) }
            }
        }
        loadData()
    }

    fun refresh() {
        if (_uiState.value.bannerMovies.isEmpty()) {
            // Nothing cached: do a full load so the full-screen loader shows
            // instead of stacking isLoading + isRefreshing at once.
            loadData()
            return
        }
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        loadData()
    }

    fun loadData() {
        if (_uiState.value.bannerMovies.isEmpty() && !_uiState.value.isRefreshing) {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        }
        // Cancel any in-flight load so a rapid pull-refresh + retry pair can't
        // clear isLoading/isRefreshing out from under the newer fetch.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                var popular = repository.getPopularMovies()
                var series = repository.getPopularSeries()
                var trending = repository.getTrendingMovies()

                // If all returned empty, wait 1s and retry once (network socket warmup on startup)
                if (popular.isEmpty() && trending.isEmpty() && series.isEmpty()) {
                    delay(1000)
                    popular = repository.getPopularMovies()
                    series = repository.getPopularSeries()
                    trending = repository.getTrendingMovies()
                }

                if (popular.isEmpty() && trending.isEmpty() && series.isEmpty()) {
                    // Keep cached rails on a failed refresh; only surface
                    // offline when there is nothing to show at all.
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = if (hasCachedContent(it)) null else "offline"
                        )
                    }
                    return@launch
                }

                val action = repository.getMoviesByGenre("action")
                val comedy = repository.getMoviesByGenre("comedy")

                _uiState.update {
                    it.copy(
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
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = if (hasCachedContent(it)) null else "offline"
                    )
                }
            }
        }
    }

    private fun hasCachedContent(state: HomeUiState): Boolean =
        state.bannerMovies.isNotEmpty() || state.popularMovies.isNotEmpty()

    fun toggleBookmark(movie: MovieItem) {
        if (movie.id.isBlank() && movie.slug.isBlank()) return
        bookmarkStore.toggleBookmark(
            BookmarkItem(
                contentId = movie.id.ifBlank { movie.slug },
                contentType = "movie",
                title = movie.title,
                posterUrl = movie.fullPosterUrl,
                backdropUrl = movie.fullBackdropUrl,
                slug = movie.slug
            )
        )
    }

    fun removeHistoryItem(item: WatchHistoryItem) {
        removedHistory.removeAll { it.contentId == item.contentId }
        removedHistory.addLast(item)
        if (removedHistory.size > 10) removedHistory.removeFirst()
        historyStore.removeItem(item.contentId)
    }

    fun restoreHistoryItem(item: WatchHistoryItem) {
        // Item-scoped: each snackbar's Undo restores its own item, even when
        // several removes queue up before any Undo is tapped.
        if (removedHistory.remove(item)) {
            historyStore.restoreItem(item)
        }
    }
}
