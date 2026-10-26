package com.idlix.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.idlix.data.model.MovieItem
import com.idlix.mobile.ui.theme.AgeBadgeBorder
import com.idlix.mobile.ui.theme.IdlixRed
import com.idlix.mobile.ui.theme.MatchGreen
import com.idlix.mobile.ui.theme.SurfaceVariantDark
import com.idlix.mobile.ui.theme.TextMuted
import com.idlix.mobile.ui.theme.TextWhite
import kotlin.math.roundToInt

/**
 * Shared Netflix visual language + adaptive breakpoints.
 *
 * Breakpoints use BoxWithConstraints width (600/840dp) instead of
 * material3-window-sizeclass, so no new dependency is needed. Single codebase,
 * no separate layouts: callers branch only on width.
 */
object NetflixBreakpoints {
    const val MEDIUM = 600
    const val EXPANDED = 840
}

/** Poster-grid columns for a given max width in dp. Wide 16:9 cards use fewer columns. */
fun netflixGridColumns(maxWidthDp: Int, wideCards: Boolean = false): Int {
    if (wideCards) {
        return when {
            maxWidthDp >= NetflixBreakpoints.EXPANDED -> 4
            maxWidthDp >= NetflixBreakpoints.MEDIUM -> 3
            else -> 2
        }
    }
    return when {
        maxWidthDp >= NetflixBreakpoints.EXPANDED -> 6
        maxWidthDp >= NetflixBreakpoints.MEDIUM -> 4
        else -> 3
    }
}

/** Poster card width that exactly fills a [netflixGridColumns] grid. */
fun netflixCardWidth(maxWidthDp: Int, columns: Int, spacingDp: Int = 10, paddingDp: Int = 32): Int {
    if (columns <= 0) return 120
    return ((maxWidthDp - paddingDp - spacingDp * (columns - 1)) / columns).coerceAtLeast(96)
}

@Composable
fun NetflixSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        color = TextWhite,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = 16.dp)
    )
}

/**
 * Compact Netflix metadata row: match % (green) • year • age badge • duration • HD.
 * Direction-aware Row so ID locale RTL mirrors automatically.
 */
@Composable
fun NetflixMetadataRow(
    matchPercent: Int? = null,
    year: String = "",
    ageRating: String = "13+",
    duration: String = "",
    showHd: Boolean = true,
    centered: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            8.dp,
            if (centered) Alignment.CenterHorizontally else Alignment.Start
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        if (matchPercent != null) {
            Text(
                text = "$matchPercent% Match",
                color = MatchGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (year.isNotBlank()) {
            Text(text = year, color = TextMuted, fontSize = 13.sp)
        }
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(2.dp),
            border = BorderStroke(1.dp, AgeBadgeBorder)
        ) {
            Text(
                text = ageRating,
                color = TextMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        if (duration.isNotBlank()) {
            Text(
                text = duration,
                color = TextMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showHd) {
            Surface(
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "HD",
                    color = TextWhite,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/** Derives a Netflix-style match score from a 0..10 vote average. Null when unknown. */
fun matchPercentForVote(vote: Double?): Int? =
    vote?.takeIf { it > 0.0 }?.let { ((it / 10.0) * 100).roundToInt().coerceIn(50, 99) }

/** Top-10 rail with oversized red numerals (red used sparingly, per Netflix DNA). */
@Composable
fun Top10Row(
    movies: List<MovieItem>,
    onMovieClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (movies.isEmpty()) return
    val top10 = movies.take(10)

    Column(modifier = modifier.fillMaxWidth()) {
        NetflixSectionHeader(title = "Top 10 Right Now")
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(top10, key = { _, movie -> movie.id }) { index, movie ->
                Top10Card(
                    rank = index + 1,
                    title = movie.title,
                    posterUrl = movie.fullPosterUrl,
                    onClick = { onMovieClick(movie.slug) }
                )
            }
        }
    }
}

@Composable
private fun Top10Card(
    rank: Int,
    title: String,
    posterUrl: String?,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        val rankText = rank.toString()
        val numeralStyle = TextStyle(
            fontSize = 96.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 96.sp,
            letterSpacing = (-8).sp
        )
        // Outlined numeral: dark stroke layer under a red fill layer.
        Box(contentAlignment = Alignment.BottomCenter) {
            Text(
                text = rankText,
                style = numeralStyle.copy(
                    drawStyle = Stroke(width = 12f),
                    color = Color.Black.copy(alpha = 0.75f)
                )
            )
            Text(
                text = rankText,
                style = numeralStyle,
                color = IdlixRed
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Card(
            modifier = Modifier
                .width(110.dp)
                .aspectRatio(2f / 3f),
            shape = RoundedCornerShape(6.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.35f))
                )
            }
        }
    }
}
