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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idlix.data.model.MovieItem
import com.idlix.data.model.SeriesItem
import com.idlix.mobile.ui.theme.TextWhite

@Composable
fun MovieSwimlane(
    title: String,
    movies: List<MovieItem>,
    onMovieClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (movies.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = TextWhite,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(movies, key = { it.id }) { movie ->
                MobileMovieCard(
                    title = movie.title,
                    posterUrl = movie.fullPosterUrl,
                    rating = movie.voteAverage?.toDoubleOrNull(),
                    quality = movie.quality,
                    onClick = { onMovieClick(movie.slug) }
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
    modifier: Modifier = Modifier
) {
    if (seriesList.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = TextWhite,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(seriesList, key = { it.id }) { series ->
                MobileMovieCard(
                    title = series.title,
                    posterUrl = series.fullPosterUrl,
                    rating = series.voteAverage?.toDoubleOrNull(),
                    onClick = { onSeriesClick(series.slug) }
                )
            }
        }
    }
}
