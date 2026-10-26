package com.idlix.mobile.ui.screens.detail

import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.idlix.data.local.BookmarkStore
import com.idlix.data.model.BookmarkItem
import com.idlix.data.model.CastMember
import com.idlix.data.model.EpisodeItem
import com.idlix.data.net.IdlixDomainResolver
import com.idlix.mobile.R
import com.idlix.mobile.ui.components.NetflixMetadataRow
import com.idlix.mobile.ui.components.matchPercentForVote
import com.idlix.mobile.ui.screens.detail.components.MobilePlayerView
import com.idlix.mobile.ui.screens.detail.components.AudioTrackOption
import com.idlix.mobile.ui.screens.detail.components.SubtitleModalBottomSheet
import com.idlix.mobile.ui.screens.detail.components.findActivity
import com.idlix.mobile.ui.theme.BackgroundDark
import com.idlix.mobile.ui.theme.BackgroundElevated
import com.idlix.mobile.ui.theme.IdlixRed
import com.idlix.mobile.ui.theme.TextMuted
import com.idlix.mobile.ui.theme.TextSecondary
import com.idlix.mobile.ui.theme.TextWhite

@Composable
fun DetailScreen(
    contentType: String,
    slug: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val bookmarkStore = remember(context) { BookmarkStore.getInstance(context) }
    val uiState by viewModel.uiState.collectAsState()
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var isOverviewExpanded by remember { mutableStateOf(false) }
    val bookmarks by bookmarkStore.bookmarkFlow.collectAsState()
    var audioOptions by remember { mutableStateOf<List<AudioTrackOption>>(emptyList()) }
    var selectedAudioLang by remember { mutableStateOf<String?>(null) }
    var audioSelectHandler by remember { mutableStateOf<((AudioTrackOption) -> Unit)?>(null) }

    val activity = remember(context) { context.findActivity() }

    // Load media detail
    LaunchedEffect(contentType, slug) {
        viewModel.loadDetail(contentType, slug)
    }

    // Handle orientation & system bars for Fullscreen Mode
    LaunchedEffect(isFullscreen) {
        activity?.let { act ->
            val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
            if (isFullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                windowInsetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Cleanup orientation and system bars on exit
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Intercept Back Press: If fullscreen, exit fullscreen; if inline, pop back to previous screen
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    val title = uiState.movie?.title ?: uiState.series?.title ?: ""
    val overview = uiState.movie?.overview ?: uiState.series?.overview ?: ""
    val rawVoteAverage = uiState.movie?.voteAverage ?: uiState.series?.voteAverage
    val year = (uiState.movie?.releaseDate ?: uiState.series?.firstAirDate)?.take(4) ?: ""
    val durationText = uiState.movie?.runtime?.let { stringResource(R.string.runtime_minutes, it) }
        ?: uiState.series?.numberOfSeasons?.let { "$it Seasons" }
        ?: ""
    val genres = (uiState.movie?.genres ?: uiState.series?.genres ?: emptyList()).map { it.name }
    val castList = uiState.movie?.cast ?: uiState.series?.cast ?: emptyList()

    val currentSubtitle = if (uiState.series != null) {
        val playingEp = uiState.playingEpisode
        val playingSNum = uiState.playingSeasonNumber
        if (playingEp != null) {
            "S${playingSNum ?: (uiState.selectedSeason?.seasonNumber ?: 1)}E${playingEp.episodeNumber}: ${playingEp.name}"
        } else if (uiState.selectedEpisode != null) {
            val sNum = uiState.selectedSeason?.seasonNumber ?: 1
            "S${sNum}E${uiState.selectedEpisode!!.episodeNumber}: ${uiState.selectedEpisode!!.name}"
        } else {
            null
        }
    } else {
        null
    }

    val previewImageUrl = if (uiState.series != null) {
        uiState.selectedEpisode?.fullStillUrl?.takeIf { it.isNotBlank() }
            ?: uiState.series?.fullBackdropUrl
            ?: uiState.series?.fullPosterUrl
    } else {
        uiState.movie?.fullBackdropUrl ?: uiState.movie?.fullPosterUrl
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Player Box (Inline 16:9 or Fullscreen Landscape)
            Box(
                modifier = if (isFullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                }
            ) {
                MobilePlayerView(
                    title = title,
                    subtitle = currentSubtitle,
                    resolvedStream = uiState.resolvedStream,
                    isStreamLoading = uiState.isStreamLoading,
                    countdownSeconds = uiState.countdownSeconds,
                    streamError = uiState.streamError,
                    selectedSubtitle = uiState.selectedSubtitle,
                    isSubtitleEnabled = uiState.isSubtitleEnabled,
                    initialPositionMs = uiState.initialPositionMs,
                    isFullscreen = isFullscreen,
                    previewImageUrl = previewImageUrl,
                    onPlayClick = { viewModel.playCurrentMedia() },
                    nextEpisode = uiState.nextEpisode,
                    isNextEpisodeDismissed = uiState.isNextEpisodeDismissed,
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    onOpenSubtitlePicker = { viewModel.openSubtitlePicker() },
                    onBackClick = {
                        if (isFullscreen) {
                            isFullscreen = false
                        } else {
                            onBackClick()
                        }
                    },
                    onPlayNextEpisode = { viewModel.playNextEpisode() },
                    onDismissNextEpisode = { viewModel.dismissNextEpisode() },
                    onRetryPlayback = { viewModel.retryPlayback() },
                    onSaveProgress = { pos, dur -> viewModel.saveProgress(pos, dur) },
                    onAudioOptionsChanged = { opts, sel ->
                        audioOptions = opts
                        selectedAudioLang = sel
                    },
                    onRegisterAudioSelect = { handler -> audioSelectHandler = handler },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Scrollable Content (hidden when in fullscreen)
            if (!isFullscreen) {
                if (uiState.isLoadingDetail) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = IdlixRed, strokeWidth = 2.dp)
                    }
                } else if (uiState.detailError != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = uiState.detailError ?: stringResource(R.string.loading_error),
                                color = TextWhite,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.loadDetail(contentType, slug) },
                                colors = ButtonDefaults.buttonColors(containerColor = IdlixRed)
                            ) {
                                Text(text = stringResource(R.string.try_again))
                            }
                        }
                    }
                } else {
                    // Master-detail on Expanded (>=840dp): info master left,
                    // episodes/cast detail right. Compact keeps the one-column rail.
                    val playButtonText = if (uiState.isStreamLoading) {
                        stringResource(R.string.connecting_to_media)
                    } else if (uiState.series != null) {
                        val playingEp = uiState.playingEpisode
                        val selectedEp = uiState.selectedEpisode ?: uiState.episodes.firstOrNull()
                        if (uiState.resolvedStream != null && playingEp != null) {
                            stringResource(
                                R.string.now_playing_episode,
                                uiState.playingSeasonNumber ?: (uiState.selectedSeason?.seasonNumber ?: 1),
                                playingEp.episodeNumber
                            )
                        } else if (selectedEp != null) {
                            stringResource(
                                R.string.play_season_episode,
                                uiState.selectedSeason?.seasonNumber ?: 1,
                                selectedEp.episodeNumber,
                                selectedEp.name
                            )
                        } else {
                            stringResource(R.string.play_now)
                        }
                    } else {
                        if (uiState.resolvedStream != null) {
                            stringResource(R.string.now_playing)
                        } else {
                            stringResource(R.string.play_now)
                        }
                    }
                    val detailBookmarkId = uiState.movie?.id?.ifBlank { slug }
                        ?: uiState.series?.id?.ifBlank { slug } ?: slug
                    val isBookmarked = bookmarks.any { it.contentId == detailBookmarkId }
                    val onBookmarkToggle: () -> Unit = {
                        bookmarkStore.toggleBookmark(
                            BookmarkItem(
                                contentId = detailBookmarkId,
                                contentType = if (uiState.movie != null) "movie" else "series",
                                title = title,
                                posterUrl = uiState.movie?.fullPosterUrl ?: uiState.series?.fullPosterUrl,
                                backdropUrl = uiState.movie?.fullBackdropUrl ?: uiState.series?.fullBackdropUrl,
                                slug = slug
                            )
                        )
                    }
                    val cert = uiState.movie?.certification ?: uiState.series?.certification
                    val onShareClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Watch $title on IDLIX: ${IdlixDomainResolver.currentBaseUrl().trimEnd('/')}/$contentType/$slug")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share)))
                    }
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .navigationBarsPadding()
                    ) {
                        val isExpandedPane = maxWidth >= 840.dp
                        if (isExpandedPane) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier
                                        .weight(1.05f)
                                        .fillMaxHeight()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    DetailInfoColumn(
                                        title = title,
                                        matchPercent = matchPercentForVote(rawVoteAverage?.toDoubleOrNull()),
                                        year = year,
                                        ageRating = cert,
                                        durationText = durationText,
                                        playButtonText = playButtonText,
                                        onPlayClick = { viewModel.playCurrentMedia() },
                                        isBookmarked = isBookmarked,
                                        onBookmarkToggle = onBookmarkToggle,
                                        onShareClick = onShareClick,
                                        genres = genres,
                                        overview = overview,
                                        isOverviewExpanded = isOverviewExpanded,
                                        onToggleOverview = { isOverviewExpanded = !isOverviewExpanded }
                                    )
                                }
                                VerticalDivider(color = Color.White.copy(alpha = 0.1f))
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(0.95f)
                                        .fillMaxHeight(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    item {
                                        DetailEpisodesCastColumn(
                                            uiState = uiState,
                                            viewModel = viewModel,
                                            castList = castList
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                item {
                                    DetailInfoColumn(
                                        title = title,
                                        matchPercent = matchPercentForVote(rawVoteAverage?.toDoubleOrNull()),
                                        year = year,
                                        ageRating = cert,
                                        durationText = durationText,
                                        playButtonText = playButtonText,
                                        onPlayClick = { viewModel.playCurrentMedia() },
                                        isBookmarked = isBookmarked,
                                        onBookmarkToggle = onBookmarkToggle,
                                        onShareClick = onShareClick,
                                        genres = genres,
                                        overview = overview,
                                        isOverviewExpanded = isOverviewExpanded,
                                        onToggleOverview = { isOverviewExpanded = !isOverviewExpanded }
                                    )
                                }
                                item {
                                    DetailEpisodesCastColumn(
                                        uiState = uiState,
                                        viewModel = viewModel,
                                        castList = castList
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Subtitle Picker Modal Bottom Sheet
        SubtitleModalBottomSheet(
            isVisible = uiState.isSubtitlePickerVisible,
            availableSubtitles = uiState.resolvedStream?.subtitles ?: emptyList(),
            selectedSubtitle = uiState.selectedSubtitle,
            isSubtitleEnabled = uiState.isSubtitleEnabled,
            subdlSubtitles = uiState.subdlSubtitles,
            isSearchingSubdl = uiState.isSearchingSubdl,
            onToggleSubtitle = { viewModel.toggleSubtitle() },
            onSelectTrack = { viewModel.selectSubtitleTrack(it) },
            onSelectSubdl = { viewModel.selectSubdlSubtitle(it) {} },
            onSearchSubdl = { viewModel.searchSubdlSubtitles() },
            onDismiss = { viewModel.closeSubtitlePicker() },
            audioTracks = audioOptions,
            selectedAudioLanguage = selectedAudioLang,
            onSelectAudio = { audioSelectHandler?.invoke(it) }
        )
    }
}

@Composable
private fun EpisodeCardItem(
    episode: EpisodeItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) IdlixRed.copy(alpha = 0.15f) else BackgroundElevated,
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) BorderStroke(1.5.dp, IdlixRed) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Still Image
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                AsyncImage(
                    model = episode.fullStillUrl,
                    contentDescription = episode.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.play),
                            tint = IdlixRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${episode.episodeNumber}. ${episode.name}",
                    color = if (isSelected) IdlixRed else TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val epOverview = episode.overview
                if (!epOverview.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = epOverview,
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val epRuntime = episode.runtime
                if (epRuntime != null && epRuntime > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.runtime_minutes, epRuntime),
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CastItem(cast: CastMember) {
    Column(
        modifier = Modifier.width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = cast.fullProfileUrl,
            contentDescription = cast.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = cast.name,
            color = TextWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val castChar = cast.character
        if (!castChar.isNullOrBlank()) {
            Text(
                text = castChar,
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Netflix info pane shared by both detail layouts: title, match metadata, Play
 * CTA, My-List/Share actions, genres, overview. Rendered inside a LazyColumn
 * item on phones and inside a scrollable Column (master pane) on Expanded.
 */
@Composable
private fun DetailInfoColumn(
    title: String,
    matchPercent: Int?,
    year: String,
    ageRating: String? = null,
    durationText: String,
    playButtonText: String,
    onPlayClick: () -> Unit,
    isBookmarked: Boolean,
    onBookmarkToggle: () -> Unit,
    onShareClick: () -> Unit,
    genres: List<String>,
    overview: String,
    isOverviewExpanded: Boolean,
    onToggleOverview: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = TextWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        NetflixMetadataRow(
            matchPercent = matchPercent,
            year = year,
            ageRating = ageRating,
            duration = durationText
        )
        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onPlayClick,
            colors = ButtonDefaults.buttonColors(containerColor = IdlixRed),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.play),
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = playButtonText,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(14.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBookmarkToggle)
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = stringResource(
                            if (isBookmarked) R.string.remove_from_list else R.string.add_to_list
                        ),
                        tint = if (isBookmarked) IdlixRed else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(if (isBookmarked) R.string.in_my_list else R.string.my_list),
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Surface(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onShareClick)
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.share),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.share),
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        if (genres.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                genres.forEach { genre ->
                    Surface(
                        color = Color.White.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = genre,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (overview.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .clickable(onClick = onToggleOverview)
            ) {
                Text(
                    text = overview,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    maxLines = if (isOverviewExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(if (isOverviewExpanded) R.string.show_less else R.string.show_more),
                    color = IdlixRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Episodes + cast detail content shared by both layouts. Plain Column so it
 * renders inside the compact LazyColumn (as one item) and inside the
 * right-hand detail pane on Expanded.
 */
@Composable
private fun DetailEpisodesCastColumn(
    uiState: DetailUiState,
    viewModel: DetailViewModel,
    castList: List<CastMember>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (uiState.series != null) {
            val seasons = uiState.series!!.seasons
            if (seasons.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = Color.White.copy(alpha = 0.1f)
                )

                Text(
                    text = stringResource(R.string.episodes),
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    seasons.forEach { season ->
                        val isSelected = uiState.selectedSeason?.seasonNumber == season.seasonNumber
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectSeason(season) },
                            label = {
                                Text(
                                    text = season.name.ifBlank {
                                        stringResource(R.string.season, season.seasonNumber)
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IdlixRed,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White.copy(alpha = 0.08f),
                                labelColor = TextSecondary
                            ),
                            border = null
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            uiState.episodes.forEach { episode ->
                val isSelected = (uiState.playingEpisode?.id ?: uiState.selectedEpisode?.id) == episode.id
                EpisodeCardItem(
                    episode = episode,
                    isSelected = isSelected,
                    onClick = { viewModel.selectEpisode(episode) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (castList.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color.White.copy(alpha = 0.1f)
            )

            Text(
                text = stringResource(R.string.cast),
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(castList) { cast ->
                    CastItem(cast = cast)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
