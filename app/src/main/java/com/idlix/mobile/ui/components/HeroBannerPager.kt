package com.idlix.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.idlix.data.model.MovieItem
import com.idlix.mobile.R
import com.idlix.mobile.ui.theme.BackgroundDark
import com.idlix.mobile.ui.theme.IdlixRed
import com.idlix.mobile.ui.theme.TextMuted
import com.idlix.mobile.ui.theme.TextWhite

@Composable
fun HeroBannerPager(
    movies: List<MovieItem>,
    onItemClick: (slug: String) -> Unit,
    modifier: Modifier = Modifier,
    heroHeight: Dp = 520.dp,
    bookmarkedSlugs: Set<String> = emptySet(),
    onToggleBookmark: ((MovieItem) -> Unit)? = null
) {
    if (movies.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { movies.size.coerceAtMost(5) })

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val movie = movies[page]
            val imageUrl = movie.fullBackdropUrl.ifEmpty { movie.fullPosterUrl }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onItemClick(movie.slug) }
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = movie.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Top scrim keeps status-bar area legible.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(BackgroundDark.copy(alpha = 0.65f), Color.Transparent)
                            )
                        )
                )

                // Bottom gradient into the background.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.45f to Color.Transparent,
                                    0.75f to BackgroundDark.copy(alpha = 0.85f),
                                    1.0f to BackgroundDark
                                )
                            )
                        )
                )

                // Billboard info + CTAs.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = movie.title,
                        color = TextWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    val genreText = movie.genres.joinToString(" • ") { it.name }
                    if (genreText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = genreText,
                            color = TextMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    NetflixMetadataRow(
                        year = movie.releaseDate?.take(4) ?: "",
                        duration = movie.runtime?.let { "$it min" } ?: "",
                        centered = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onItemClick(movie.slug) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.play),
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.play), color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        val isBookmarked = movie.slug in bookmarkedSlugs
                        Button(
                            onClick = {
                                if (onToggleBookmark != null) onToggleBookmark(movie)
                                else onItemClick(movie.slug)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.18f),
                                contentColor = TextWhite
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.Check else Icons.Filled.Add,
                                contentDescription = stringResource(
                                    if (isBookmarked) R.string.remove_from_list else R.string.add_to_list
                                ),
                                tint = TextWhite
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(if (isBookmarked) R.string.in_my_list else R.string.my_list),
                                color = TextWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Pager indicators: white active pill, red reserved for brand accents elsewhere.
        if (pagerState.pageCount > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(pagerState.pageCount) { iteration ->
                    val active = pagerState.currentPage == iteration
                    val color = if (active) Color.White else Color.White.copy(alpha = 0.35f)
                    val width = if (active) 16.dp else 6.dp
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }

        // Red brand tick at the top edge of the billboard.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(IdlixRed.copy(alpha = 0.9f))
        )
    }
}
