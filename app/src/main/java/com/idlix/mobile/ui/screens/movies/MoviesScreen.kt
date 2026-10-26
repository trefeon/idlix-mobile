package com.idlix.mobile.ui.screens.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.idlix.mobile.R
import com.idlix.mobile.ui.components.EmptyTitlesState
import com.idlix.mobile.ui.components.FilterChipRow
import com.idlix.mobile.ui.components.FilterItem
import com.idlix.mobile.ui.components.MobileMovieCard
import com.idlix.mobile.ui.components.OfflineState
import com.idlix.mobile.ui.components.netflixCardWidth
import com.idlix.mobile.ui.components.netflixGridColumns
import com.idlix.mobile.ui.theme.BackgroundDark
import com.idlix.mobile.ui.theme.IdlixRed
import com.idlix.mobile.ui.theme.TextMuted
import com.idlix.mobile.ui.theme.TextWhite

@Composable
fun MoviesScreen(
    onNavigateToDetail: (contentType: String, slug: String) -> Unit,
    viewModel: MoviesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()

    val genres = listOf(
        FilterItem("all", stringResource(R.string.genre_all)),
        FilterItem("action", stringResource(R.string.genre_action)),
        FilterItem("adventure", stringResource(R.string.genre_adventure)),
        FilterItem("animation", stringResource(R.string.genre_animation)),
        FilterItem("comedy", stringResource(R.string.genre_comedy)),
        FilterItem("crime", stringResource(R.string.genre_crime)),
        FilterItem("drama", stringResource(R.string.genre_drama)),
        FilterItem("horror", stringResource(R.string.genre_horror)),
        FilterItem("science-fiction", stringResource(R.string.genre_scifi)),
        FilterItem("thriller", stringResource(R.string.genre_thriller))
    )

    // Detect when scrolling near the end to trigger pagination
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItemsCount = gridState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 6
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    // A genre switch replaces the whole list: jump back to the top so the user
    // never sits mid-grid staring at unrelated stale scroll position.
    LaunchedEffect(uiState.selectedGenre) {
        gridState.scrollToItem(0)
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Single codebase adaptive: 3 cols phone, 4 cols medium, 6 cols expanded.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val maxWidthDp = maxWidth.value.toInt()
            val columns = netflixGridColumns(maxWidthDp)
            val cardWidth = netflixCardWidth(maxWidthDp, columns).dp

            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Text(
                    text = stringResource(R.string.movies),
                    color = TextWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )

                // Filter Chips
                FilterChipRow(
                    items = genres,
                    selectedId = uiState.selectedGenre,
                    onSelect = { viewModel.selectGenre(it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (uiState.isLoading && !uiState.isRefreshing) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = IdlixRed)
                    }
                } else if (uiState.error != null && uiState.movies.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        OfflineState(onRetry = { viewModel.refresh() })
                    }
                } else if (uiState.movies.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyTitlesState(onRetry = { viewModel.refresh() })
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.movies, key = { it.id }) { movie ->
                            MobileMovieCard(
                                title = movie.title,
                                posterUrl = movie.fullPosterUrl,
                                rating = movie.voteAverage?.toDoubleOrNull(),
                                quality = movie.quality,
                                width = cardWidth,
                                onClick = { onNavigateToDetail("movie", movie.slug) }
                            )
                        }

                        if (uiState.isLoadingMore) {
                            item(span = { GridItemSpan(columns) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = IdlixRed, modifier = Modifier.height(28.dp))
                                }
                            }
                        }

                        // Append-page failures previously left zero UI: the grid
                        // just stopped growing. Surface an inline retry instead.
                        if (!uiState.isLoadingMore && uiState.error != null) {
                            item(span = { GridItemSpan(columns) }) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = stringResource(R.string.offline_message),
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.loadMore() },
                                        colors = ButtonDefaults.buttonColors(containerColor = IdlixRed)
                                    ) {
                                        Text(text = stringResource(R.string.try_again))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
