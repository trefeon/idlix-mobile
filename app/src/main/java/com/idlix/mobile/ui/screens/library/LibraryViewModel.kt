package com.idlix.mobile.ui.screens.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.local.BookmarkStore
import com.idlix.data.local.WatchHistoryStore
import com.idlix.data.model.BookmarkItem
import com.idlix.data.model.WatchHistoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryUiState(
    val historyItems: List<WatchHistoryItem> = emptyList(),
    val isLoading: Boolean = false
)

class LibraryViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val historyStore = WatchHistoryStore.getInstance(application.applicationContext)
    private val bookmarkStore = BookmarkStore.getInstance(application.applicationContext)

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    val bookmarks: StateFlow<List<BookmarkItem>> = bookmarkStore.bookmarkFlow

    private var pendingUndo: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            historyStore.historyFlow.collect { items ->
                _uiState.value = _uiState.value.copy(historyItems = items)
            }
        }
    }

    fun removeItem(contentId: String) {
        val item = _uiState.value.historyItems.firstOrNull { it.contentId == contentId }
        if (item != null) {
            pendingUndo = { historyStore.restoreItem(item) }
        }
        historyStore.removeItem(contentId)
    }

    fun removeBookmark(contentId: String) {
        val item = bookmarks.value.firstOrNull { it.contentId == contentId }
        if (item != null) {
            pendingUndo = { bookmarkStore.restoreItem(item) }
        }
        bookmarkStore.removeBookmark(contentId)
    }

    fun consumeUndo() {
        pendingUndo?.invoke()
        pendingUndo = null
    }

    fun clearAll() {
        pendingUndo = null
        historyStore.clearAll()
    }
}
