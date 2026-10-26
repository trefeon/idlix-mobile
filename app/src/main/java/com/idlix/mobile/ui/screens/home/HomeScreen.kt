package com.idlix.mobile.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.idlix.mobile.R
import com.idlix.mobile.ui.components.ContinueWatchingSection
import com.idlix.mobile.ui.components.HeroBannerPager
import com.idlix.mobile.ui.components.OfflineState
import com.idlix.mobile.ui.components.MovieSwimlane
import com.idlix.mobile.ui.components.SeriesSwimlane
import com.idlix.mobile.ui.components.Top10Row
import com.idlix.mobile.ui.theme.BackgroundDark
import com.idlix.mobile.ui.theme.IdlixRed
import com.idlix.mobile.ui.theme.SurfaceDark
import com.idlix.mobile.ui.theme.TextMuted
import com.idlix.mobile.ui.theme.TextWhite

@Composable
fun HomeScreen(
    onNavigateToDetail: (contentType: String, slug: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val bookmarkedSlugs by viewModel.bookmarkedSlugs.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removedLabel = stringResource(R.string.removed)
    val undoLabel = stringResource(R.string.undo)

    fun removeWithUndo(contentId: String) {
        val item = uiState.continueWatching.firstOrNull { it.contentId == contentId } ?: return
        viewModel.removeHistoryItem(item)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = removedLabel,
                actionLabel = undoLabel
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreLastRemoved()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        if (uiState.isLoading && !uiState.isRefreshing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = IdlixRed)
            }
        } else if (uiState.error != null && uiState.bannerMovies.isEmpty() && uiState.continueWatching.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                OfflineState(onRetry = { viewModel.loadData() })
            }
        } else {
            // Single codebase adaptive: width breakpoints only, no separate layouts.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isExpanded = maxWidth >= 840.dp
                val isMedium = maxWidth >= 600.dp
                val heroHeight = when {
                    isExpanded -> 620.dp
                    isMedium -> 560.dp
                    else -> 520.dp
                }
                val cardWidth = when {
                    isExpanded -> 160.dp
                    isMedium -> 140.dp
                    else -> 120.dp
                }
                val continueWidth = when {
                    isExpanded -> 260.dp
                    isMedium -> 220.dp
                    else -> 180.dp
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Netflix top bar: brand left, settings right.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.idlix_logo),
                            contentDescription = stringResource(R.string.app_logo),
                            modifier = Modifier.height(28.dp)
                        )

                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.settings),
                                tint = TextWhite
                            )
                        }
                    }

                    // 1. Hero billboard
                    if (uiState.bannerMovies.isNotEmpty()) {
                        HeroBannerPager(
                            movies = uiState.bannerMovies,
                            onItemClick = { slug -> onNavigateToDetail("movie", slug) },
                            heroHeight = heroHeight,
                            bookmarkedSlugs = bookmarkedSlugs,
                            onToggleBookmark = { movie -> viewModel.toggleBookmark(movie) }
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // 2. Continue Watching
                    if (uiState.continueWatching.isNotEmpty()) {
                        ContinueWatchingSection(
                            items = uiState.continueWatching,
                            onItemClick = { contentType, slug -> onNavigateToDetail(contentType, slug) },
                            cardWidth = continueWidth,
                            onRemoveItem = { item -> removeWithUndo(item.contentId) }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Single offline pattern when the catalog failed but history exists.
                    if (uiState.bannerMovies.isEmpty() && uiState.popularMovies.isEmpty()) {
                        OfflineState(
                            onRetry = { viewModel.loadData() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 3. Trending Now
                    if (uiState.trending.isNotEmpty()) {
                        MovieSwimlane(
                            title = "Trending Now",
                            movies = uiState.trending,
                            onMovieClick = { slug -> onNavigateToDetail("movie", slug) },
                            cardWidth = cardWidth
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // 4. Top 10 (oversized red numerals)
                    if (uiState.trending.isNotEmpty()) {
                        Top10Row(
                            movies = uiState.trending,
                            onMovieClick = { slug -> onNavigateToDetail("movie", slug) }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // 5. Popular TV Series
                    if (uiState.popularSeries.isNotEmpty()) {
                        SeriesSwimlane(
                            title = "Popular TV Series",
                            seriesList = uiState.popularSeries,
                            onSeriesClick = { slug -> onNavigateToDetail("series", slug) },
                            cardWidth = cardWidth
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // 6. Popular Movies
                    if (uiState.popularMovies.isNotEmpty()) {
                        MovieSwimlane(
                            title = "Popular Movies",
                            movies = uiState.popularMovies,
                            onMovieClick = { slug -> onNavigateToDetail("movie", slug) },
                            cardWidth = cardWidth
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // 7. Action Movies
                    if (uiState.actionMovies.isNotEmpty()) {
                        MovieSwimlane(
                            title = "Adrenaline Action & Adventure",
                            movies = uiState.actionMovies,
                            onMovieClick = { slug -> onNavigateToDetail("movie", slug) },
                            cardWidth = cardWidth
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // 8. Comedy Movies
                    if (uiState.comedyMovies.isNotEmpty()) {
                        MovieSwimlane(
                            title = "Top Comedy Movies",
                            movies = uiState.comedyMovies,
                            onMovieClick = { slug -> onNavigateToDetail("movie", slug) },
                            cardWidth = cardWidth
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
    }
}
