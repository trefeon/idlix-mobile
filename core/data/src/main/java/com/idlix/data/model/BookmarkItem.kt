package com.idlix.data.model

import kotlinx.serialization.Serializable

/**
 * A title the user explicitly saved to My List. Persisted locally via
 * [com.idlix.data.local.BookmarkStore]; never synced to any backend.
 */
@Serializable
data class BookmarkItem(
    val contentId: String,
    val contentType: String = "movie", // "movie" or "series"
    val title: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val slug: String? = null,
    val savedAt: Long = System.currentTimeMillis()
)
