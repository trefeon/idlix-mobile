package com.idlix.mobile.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.idlix.mobile.R
import com.idlix.data.model.BookmarkItem
import com.idlix.mobile.ui.components.ContinueWatchingCard
import com.idlix.mobile.ui.components.MobileMovieCard
import com.idlix.mobile.ui.components.netflixGridColumns
import com.idlix.mobile.ui.theme.BackgroundDark
import com.idlix.mobile.ui.theme.IdlixRed
import com.idlix.mobile.ui.theme.SurfaceDark
import com.idlix.mobile.ui.theme.TextMuted
import com.idlix.mobile.ui.theme.TextWhite
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    onNavigateToDetail: (contentType: String, slug: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removedLabel = stringResource(R.string.removed)
    val undoLabel = stringResource(R.string.undo)
    val historyTabLabel = stringResource(R.string.history)
    val myListTabLabel = stringResource(R.string.my_list)

    fun removeHistoryWithUndo(contentId: String) {
        // Single-level undo: pendingUndo holds one item, so dismiss any
        // queued snackbar first — only the latest removal stays restorable.
        snackbarHostState.currentSnackbarData?.dismiss()
        viewModel.removeItem(contentId)
        scope.launch {
            if (snackbarHostState.showSnackbar(removedLabel, undoLabel) == SnackbarResult.ActionPerformed) {
                viewModel.consumeUndo()
            }
        }
    }

    fun removeBookmarkWithUndo(contentId: String) {
        // Same single-level undo contract as history: latest removal wins.
        snackbarHostState.currentSnackbarData?.dismiss()
        viewModel.removeBookmark(contentId)
        scope.launch {
            if (snackbarHostState.showSnackbar(removedLabel, undoLabel) == SnackbarResult.ActionPerformed) {
                viewModel.consumeUndo()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.library),
                    color = TextWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Row {
                    if (selectedTab == 0 && uiState.historyItems.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.clear_all),
                                tint = TextMuted
                            )
                        }
                    }

                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint = TextWhite
                        )
                    }
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = BackgroundDark,
                contentColor = TextWhite
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(historyTabLabel, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(myListTabLabel, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                )
            }

            if (selectedTab == 0) {
                if (uiState.historyItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.no_history_title),
                                color = TextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.no_history_desc),
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    // 16:9 history cards — single codebase adaptive: 2 / 3 / 4 columns.
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val columns = netflixGridColumns(maxWidth.value.toInt(), wideCards = true)
                        val spacing = 12
                        val cardWidthDp = ((maxWidth.value.toInt() - 32 - spacing * (columns - 1)) / columns)
                            .coerceAtLeast(140).dp

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.historyItems, key = { it.contentId }) { item ->
                                val slug = item.slug ?: item.contentId
                                ContinueWatchingCard(
                                    item = item,
                                    cardWidth = cardWidthDp,
                                    onClick = { onNavigateToDetail(item.contentType, slug) },
                                    onRemove = { removeHistoryWithUndo(item.contentId) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            } else {
                if (bookmarks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.no_bookmarks_title),
                                color = TextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.no_bookmarks_desc),
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val columns = netflixGridColumns(maxWidth.value.toInt())
                        val spacing = 10
                        val cardWidthDp = ((maxWidth.value.toInt() - 32 - spacing * (columns - 1)) / columns)
                            .coerceAtLeast(100).dp

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(bookmarks, key = { it.contentId }) { item ->
                                BookmarkCard(
                                    item = item,
                                    cardWidthDp = cardWidthDp,
                                    onClick = {
                                        val slug = item.slug ?: item.contentId
                                        onNavigateToDetail(item.contentType, slug)
                                    },
                                    onRemove = { removeBookmarkWithUndo(item.contentId) }
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_history_title)) },
            text = { Text(stringResource(R.string.clear_history_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearDialog = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.clear_all), color = IdlixRed)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.cancel), color = TextWhite)
                }
            },
            containerColor = SurfaceDark,
            titleContentColor = TextWhite,
            textContentColor = TextMuted
        )
    }
}

@Composable
private fun BookmarkCard(
    item: BookmarkItem,
    cardWidthDp: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Box(modifier = androidx.compose.ui.Modifier.width(cardWidthDp)) {
        MobileMovieCard(
            title = item.title,
            posterUrl = item.posterUrl,
            rating = null,
            width = cardWidthDp,
            onClick = onClick
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.remove),
                tint = Color.White,
                modifier = Modifier
                    .size(22.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .padding(2.dp)
            )
        }
    }
}
