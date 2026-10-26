package com.idlix.mobile.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class MobileScreen(val route: String) {
    object Home : MobileScreen("home")
    object Movies : MobileScreen("movies")
    object Series : MobileScreen("series")
    object Search : MobileScreen("search")
    object Library : MobileScreen("library")
    object Settings : MobileScreen("settings")

    object Detail : MobileScreen("detail/{contentType}/{slug}") {
        fun createRoute(contentType: String, slug: String): String {
            val encodedSlug = URLEncoder.encode(slug, StandardCharsets.UTF_8.toString())
            return "detail/$contentType/$encodedSlug"
        }
    }
}

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Home : BottomNavItem(MobileScreen.Home.route, "Home", Icons.Filled.Home)
    object Movies : BottomNavItem(MobileScreen.Movies.route, "Movies", Icons.Filled.Movie)
    object Series : BottomNavItem(MobileScreen.Series.route, "Series", Icons.Filled.Tv)
    object Search : BottomNavItem(MobileScreen.Search.route, "Search", Icons.Filled.Search)
    object Library : BottomNavItem(MobileScreen.Library.route, "Library", Icons.Filled.VideoLibrary)

    companion object {
        val items = listOf(Home, Movies, Series, Search, Library)
    }
}
