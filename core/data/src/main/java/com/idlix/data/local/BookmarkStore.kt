package com.idlix.data.local

import android.content.Context
import android.content.SharedPreferences
import com.idlix.data.model.BookmarkItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistent My List store. Mirrors the [WatchHistoryStore] prefs pattern:
 * a JSON array under one prefs key plus an in-memory copy, fanned out as a
 * [StateFlow] so Hero, Detail and Library stay in sync.
 */
class BookmarkStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("idlix_mobile_bookmarks", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Volatile
    private var memoryBookmarks: MutableList<BookmarkItem>? = null

    private val _bookmarkFlow = MutableStateFlow<List<BookmarkItem>>(loadFromPrefs())
    val bookmarkFlow: StateFlow<List<BookmarkItem>> = _bookmarkFlow.asStateFlow()

    private fun loadFromPrefs(): List<BookmarkItem> {
        memoryBookmarks?.let { return it }
        val raw = prefs.getString("bookmark_items_json", null) ?: return emptyList()
        return try {
            val list = json.decodeFromString<List<BookmarkItem>>(raw).toMutableList()
            memoryBookmarks = list
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveToPrefs(items: List<BookmarkItem>) {
        memoryBookmarks = items.toMutableList()
        val raw = try {
            json.encodeToString(items)
        } catch (e: Exception) {
            null
        }
        prefs.edit().putString("bookmark_items_json", raw).apply()
        _bookmarkFlow.value = items
    }

    fun isBookmarked(contentId: String): Boolean =
        loadFromPrefs().any { it.contentId == contentId }

    /**
     * Toggles the bookmark for [item]. Returns true when the item is now bookmarked.
     */
    fun toggleBookmark(item: BookmarkItem): Boolean {
        val current = loadFromPrefs().toMutableList()
        val existing = current.indexOfFirst { it.contentId == item.contentId }
        return if (existing >= 0) {
            current.removeAt(existing)
            saveToPrefs(current)
            false
        } else {
            current.add(0, item.copy(savedAt = System.currentTimeMillis()))
            saveToPrefs(current.take(200))
            true
        }
    }

    fun removeBookmark(contentId: String) {
        val current = loadFromPrefs().toMutableList()
        current.removeAll { it.contentId == contentId }
        saveToPrefs(current)
    }

    fun restoreItem(item: BookmarkItem) {
        val current = loadFromPrefs().toMutableList()
        current.removeAll { it.contentId == item.contentId }
        current.add(0, item)
        saveToPrefs(current.take(200))
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        memoryBookmarks = mutableListOf()
        _bookmarkFlow.value = emptyList()
    }

    companion object {
        @Volatile
        private var INSTANCE: BookmarkStore? = null

        fun getInstance(context: Context): BookmarkStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BookmarkStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
