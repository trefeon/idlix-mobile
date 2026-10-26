package com.idlix.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.idlix.data.model.MovieItem
import com.idlix.data.model.SeriesItem

/**
 * Genre swimlanes with generous Netflix rail spacing. [cardWidth] adapts to the
 * breakpoint (phone 120dp → tablet 160dp).
 */
@Composable
fun MovieSwimlane(
    title: String,
    movies: List<MovieItem>,
    onMovieClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 120.dp
) {
    if (movies.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        NetflixSectionHeader(title = title)

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(movies, key = { it.id.ifBlank { it.slug }.ifBlank { it.title } }) { movie ->
                MobileMovieCard(
                    title = movie.title,
                    posterUrl = movie.fullPosterUrl,
                    rating = movie.voteAverage?.toDoubleOrNull(),
                    quality = movie.quality,
                    width = cardWidth,
                    onClick = { if (movie.slug.isNotBlank()) onMovieClick(movie.slug) }
                )
            }
        }
    }
}

@Composable
fun SeriesSwimlane(
    title: String,
    seriesList: List<SeriesItem>,
    onSeriesClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 120.dp
) {
    if (seriesList.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        NetflixSectionHeader(title = title)

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(seriesList, key = { it.id.ifBlank { it.slug }.ifBlank { it.title } }) { series ->
                MobileMovieCard(
                    title = series.title,
                    posterUrl = series.fullPosterUrl,
                    rating = series.voteAverage?.toDoubleOrNull(),
                    width = cardWidth,
                    onClick = { if (series.slug.isNotBlank()) onSeriesClick(series.slug) }
                )
            }
        }
    }
}
