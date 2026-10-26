package com.idlix.mobile.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.idlix.mobile.R
import com.idlix.mobile.ui.components.MobileMovieCard
import com.idlix.mobile.ui.components.OfflineState
import com.idlix.mobile.ui.components.netflixCardWidth
import com.idlix.mobile.ui.components.netflixGridColumns
import com.idlix.mobile.ui.theme.BackgroundDark
import com.idlix.mobile.ui.theme.IdlixRed
import com.idlix.mobile.ui.theme.SurfaceDark
import com.idlix.mobile.ui.theme.SurfaceVariantDark
import com.idlix.mobile.ui.theme.TextDim
import com.idlix.mobile.ui.theme.TextMuted
import com.idlix.mobile.ui.theme.TextWhite

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onNavigateToDetail: (contentType: String, slug: String) -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    val trimmedQuery = uiState.query.trim()
    val isBlank = trimmedQuery.isEmpty()
    val isShortQuery = trimmedQuery.length < 2

    // First back press clears an active query (and keyboard); the second press
    // leaves the tab as usual. Without this, back exits mid-search and the
    // next visit reopens with a stale query and stale results.
    BackHandler(enabled = !isBlank) {
        viewModel.clearQuery()
        focusManager.clearFocus()
    }

    // Proper-noun titles stay as-is across locales; genre words are localized.
    val popularKeywords = listOf(
        "Avengers",
        "Batman",
        "Spider-Man",
        "Anime",
        stringResource(R.string.genre_action),
        stringResource(R.string.genre_horror),
        stringResource(R.string.genre_drama),
        stringResource(R.string.genre_comedy)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Search Input Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { viewModel.onQueryChange(it) },
                placeholder = { Text(stringResource(R.string.search_placeholder), color = TextDim, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = TextDim
                    )
                },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearQuery() }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.clear),
                                tint = TextDim
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        // Submit immediately instead of waiting out the debounce.
                        viewModel.retry()
                        focusManager.clearFocus()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark,
                    focusedBorderColor = IdlixRed,
                    unfocusedBorderColor = SurfaceVariantDark,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    cursorColor = IdlixRed
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        when {
            uiState.isSearching && uiState.results.isEmpty() && uiState.error == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = IdlixRed)
                }
            }

            uiState.error != null && !isBlank -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OfflineState(onRetry = { viewModel.retry() })
                    PopularSearches(
                        keywords = popularKeywords,
                        onPick = {
                            viewModel.onQueryChange(it)
                            focusManager.clearFocus()
                        }
                    )
                }
            }

            uiState.results.isEmpty() -> {
                when {
                    isBlank -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.search_hint),
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                            PopularSearches(
                                keywords = popularKeywords,
                                onPick = {
                                    viewModel.onQueryChange(it)
                                    focusManager.clearFocus()
                                }
                            )
                        }
                    }

                    isShortQuery -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.search_short_query),
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        }
                    }

                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.no_results_for, trimmedQuery),
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            else -> {
                // Results Grid — single codebase adaptive: 3 / 4 / 6 columns.
                // Previous results stay visible while a refined query loads; a
                // small footer spinner signals the in-flight search instead of
                // flashing back to a full-screen loader and losing scroll.
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val maxWidthDp = maxWidth.value.toInt()
                    val columns = netflixGridColumns(maxWidthDp)
                    val cardWidth = netflixCardWidth(maxWidthDp, columns).dp

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.results, key = { it.id }) { item ->
                            val type = if (item.contentType.contains("series", ignoreCase = true) || item.contentType.contains("tv", ignoreCase = true)) "series" else "movie"
                            MobileMovieCard(
                                title = item.title,
                                posterUrl = item.fullPosterUrl,
                                rating = item.voteAverage?.toDoubleOrNull(),
                                quality = item.quality,
                                width = cardWidth,
                                onClick = { onNavigateToDetail(type, item.slug) }
                            )
                        }

                        if (uiState.isSearching) {
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
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PopularSearches(
    keywords: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.popular_searches),
            color = TextWhite,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            keywords.forEach { keyword ->
                SuggestionChip(
                    onClick = { onPick(keyword) },
                    label = { Text(text = keyword, color = TextWhite, fontSize = 12.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = SurfaceDark
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = SurfaceVariantDark
                    )
                )
            }
        }
    }
}
