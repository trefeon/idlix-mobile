package com.idlix.mobile.ui.screens.detail.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.media.AudioManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.idlix.data.model.ResolvedPlayback
import com.idlix.data.model.SubtitleTrack
import com.idlix.mobile.R
import com.idlix.mobile.player.MediaSourceHelper
import com.idlix.mobile.player.PipController
import com.idlix.mobile.player.PlayerQualityPrefs
import com.idlix.mobile.ui.screens.detail.NextEpisodeInfo
import androidx.media3.common.Tracks
import com.idlix.mobile.ui.theme.BackgroundElevated
import com.idlix.mobile.ui.theme.IdlixRed
import com.idlix.mobile.ui.theme.TextMuted
import com.idlix.mobile.ui.theme.TextWhite
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun letterboxBottomPadding(viewW: Float, viewH: Float, videoW: Float, videoH: Float): Float {
    if (viewW <= 0f || viewH <= 0f || videoW <= 0f || videoH <= 0f) return 0.08f
    val videoAspect = videoW / videoH
    if (videoAspect <= 0f) return 0.08f
    val displayedH = viewW / videoAspect
    val barFraction = ((viewH - displayedH) / 2f / viewH).coerceAtLeast(0f)
    if (barFraction <= 0f) return 0.08f
    return (barFraction + 0.04f).coerceIn(0.08f, 0.50f)
}

/**
 * Player-consumable audio rendition surfaced from the HLS master (ExoPlayer
 * TRACK_TYPE_AUDIO groups). Wired to setPreferredAudioLanguage on select.
 */
data class AudioTrackOption(
    val language: String? = null,
    val label: String = ""
)

fun refreshPipHint(view: android.view.View) {
    try {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        PipController.setSourceRectHint(Rect(loc[0], loc[1], loc[0] + view.width, loc[1] + view.height))
    } catch (_: Exception) {}
}
fun applyPlayerQuality(player: ExoPlayer, effective: String) {
    val current = player.trackSelectionParameters
    val updated = current.buildUpon()
    when (effective) {
        PlayerQualityPrefs.QUALITY_1080 -> updated
            .setMaxVideoSize(1920, 1080)
            .setMaxVideoBitrate(PlayerQualityPrefs.BITRATE_1080)
        PlayerQualityPrefs.QUALITY_720 -> updated
            .setMaxVideoSize(1280, 720)
            .setMaxVideoBitrate(PlayerQualityPrefs.BITRATE_720)
        PlayerQualityPrefs.QUALITY_480 -> updated
            .setMaxVideoSize(854, 480)
            .setMaxVideoBitrate(PlayerQualityPrefs.BITRATE_480)
        else -> updated
            .clearVideoSizeConstraints()
            .setMaxVideoBitrate(Int.MAX_VALUE)
    }
    player.trackSelectionParameters = updated.build()
}

/**
 * Applies the aspect live on an existing PlayerView (no ExoPlayer rebuild).
 * Zoom-aware subtitle rule lives with the caller: Zoom has no letterbox
 * bars, so the subtitle bottom padding falls back to the 0.08 default.
 */
fun applyVideoZoom(playerView: PlayerView, zoom: Boolean) {
    playerView.resizeMode =
        if (zoom) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
}

@OptIn(UnstableApi::class)
@Composable
fun MobilePlayerView(
    title: String,
    subtitle: String?,
    resolvedStream: ResolvedPlayback?,
    isStreamLoading: Boolean,
    countdownSeconds: Int,
    streamError: String?,
    selectedSubtitle: SubtitleTrack?,
    isSubtitleEnabled: Boolean,
    initialPositionMs: Long,
    isFullscreen: Boolean,
    nextEpisode: NextEpisodeInfo?,
    isNextEpisodeDismissed: Boolean,
    previewImageUrl: String? = null,
    onPlayClick: () -> Unit = {},
    onToggleFullscreen: () -> Unit,
    onOpenSubtitlePicker: () -> Unit,
    onBackClick: () -> Unit,
    onPlayNextEpisode: () -> Unit,
    onDismissNextEpisode: () -> Unit,
    onRetryPlayback: () -> Unit,
    onSaveProgress: (currentPosMs: Long, durationMs: Long) -> Unit,
    onAudioOptionsChanged: (List<AudioTrackOption>, String?) -> Unit = { _, _ -> },
    onRegisterAudioSelect: (((AudioTrackOption) -> Unit) -> Unit) = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isInPipMode by PipController.isInPipMode.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var playbackEnded by remember { mutableStateOf(false) }
    var upNextCountdown by remember { mutableStateOf<Int?>(null) }
    var audioOptions by remember { mutableStateOf<List<AudioTrackOption>>(emptyList()) }
    var selectedAudioLang by remember { mutableStateOf<String?>(null) }
    var playerViewRef by remember { mutableStateOf<android.view.View?>(null) }
    var brightnessIndicator by remember { mutableStateOf<Float?>(null) }
    var volumeIndicator by remember { mutableStateOf<Float?>(null) }
    var doubleTapSeekFeedback by remember { mutableStateOf<Int?>(null) }

    // Quality / speed / lock state (persisted in player prefs; default Auto / 1x)
    var requestedQuality by remember { mutableStateOf(PlayerQualityPrefs.getQuality(context)) }
    var playbackSpeed by remember { mutableStateOf(PlayerQualityPrefs.getSpeed(context)) }
    var isDataSaver by remember { mutableStateOf(PlayerQualityPrefs.isDataSaver(context)) }
    // Zoom-fill aspect holder as a stable State object so the PlayerView
    // factory's padding listeners always read the live value.
    val zoomState = remember { mutableStateOf(PlayerQualityPrefs.isVideoZoom(context)) }
    var isZoom by zoomState
    // Transient pinch-toggle feedback chip ("Zoom · Fill" / "Fit · Full picture").
    var zoomFeedback by remember { mutableStateOf<Boolean?>(null) }
    var isLocked by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    val effectiveQuality = PlayerQualityPrefs.effectiveQuality(requestedQuality, isDataSaver)

    // Pick up Data-Saver toggles from Settings while the player is alive.
    DisposableEffect(context) {
        val sp = context.applicationContext.getSharedPreferences(
            "idlix_mobile_player", Context.MODE_PRIVATE
        )
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            requestedQuality = PlayerQualityPrefs.getQuality(context)
            playbackSpeed = PlayerQualityPrefs.getSpeed(context)
            isDataSaver = PlayerQualityPrefs.isDataSaver(context)
            isZoom = PlayerQualityPrefs.isVideoZoom(context)
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var userInteractionCount by remember { mutableIntStateOf(0) }
    var activePlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    // Auto-hide controls after 5 seconds of inactivity.
    // Lock suppresses auto-hide: locked chrome is already minimal (unlock affordance only).
    LaunchedEffect(showControls, isPlaying, userInteractionCount, isLocked) {
        if (showControls && isPlaying && !isLocked) {
            delay(5000)
            showControls = false
        }
    }

    // Keep PipController in sync with playback status
    LaunchedEffect(isPlaying, resolvedStream) {
        PipController.isPlaybackActive = (resolvedStream != null && isPlaying)
    }
    // API-12 auto-enter: swipe-home enters PiP while actively playing.
    LaunchedEffect(isPlaying, resolvedStream) {
        if (resolvedStream != null) {
            context.findActivity()?.let {
                PipController.setAutoEnterEnabled(it, isPlaying)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isStreamLoading) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = IdlixRed,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(42.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                if (countdownSeconds > 0) {
                    Text(
                        text = stringResource(R.string.loading_secure_stream),
                        color = TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.starting_in_seconds, countdownSeconds),
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.connecting_to_media),
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else if (streamError != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = streamError,
                    color = TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onRetryPlayback,
                        colors = ButtonDefaults.buttonColors(containerColor = IdlixRed)
                    ) {
                        Text(text = stringResource(R.string.try_again))
                    }
                    Button(
                        onClick = onBackClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                    ) {
                        Text(text = stringResource(R.string.back))
                    }
                }
            }
        } else if (resolvedStream != null) {
            val tokenQuery = remember(resolvedStream.hlsMasterUrl) {
                try {
                    Uri.parse(resolvedStream.hlsMasterUrl).query.orEmpty()
                } catch (_: Exception) {
                    ""
                }
            }

            val httpDataSourceFactory = remember(tokenQuery) {
                MediaSourceHelper.createHttpDataSourceFactory(tokenQuery)
            }

            val exoPlayer = remember(context, resolvedStream.hlsMasterUrl) {
                val renderersFactory = MediaSourceHelper.createRenderersFactory(context)
                // P0 streaming profile: lean buffers for faster start on mobile networks.
                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(10000, 30000, 1500, 3000)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
                // Start the adaptive track selector at a mid-tier estimate instead of
                // the bundled default so the first segments are watchable on cell.
                val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
                    .setInitialBitrateEstimate(1_500_000L)
                    .build()

                ExoPlayer.Builder(context, renderersFactory)
                    .setLoadControl(loadControl)
                    .setBandwidthMeter(bandwidthMeter)
                    .build().apply {
                        val activeSub = if (isSubtitleEnabled) selectedSubtitle else null
                        val mediaSource = MediaSourceHelper.buildMediaSource(
                            context = context,
                            masterUrl = resolvedStream.hlsMasterUrl,
                            sub = activeSub,
                            httpDataSourceFactory = httpDataSourceFactory
                        )
                        setMediaSource(mediaSource)

                        trackSelectionParameters = trackSelectionParameters.buildUpon()
                            .setPreferredTextLanguage("id")
                            .setSelectUndeterminedTextLanguage(true)
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !isSubtitleEnabled)
                            .build()
                        // Data-Saver / saved tier applies from the first segment.
                        applyPlayerQuality(
                            this,
                            PlayerQualityPrefs.effectiveQuality(
                                PlayerQualityPrefs.getQuality(context),
                                PlayerQualityPrefs.isDataSaver(context)
                            )
                        )
                        setPlaybackSpeed(PlayerQualityPrefs.getSpeed(context))

                        prepare()
                        if (initialPositionMs > 0L) {
                            seekTo(initialPositionMs)
                        }
                        playWhenReady = true
                    }
            }

            // Quality / Data-Saver / speed updates without recreating ExoPlayer.
            LaunchedEffect(effectiveQuality) {
                try {
                    applyPlayerQuality(exoPlayer, effectiveQuality)
                } catch (_: Exception) {}
            }
            LaunchedEffect(playbackSpeed) {
                try {
                    exoPlayer.setPlaybackSpeed(playbackSpeed)
                } catch (_: Exception) {}
            }

            // Subtitle track updates without recreating ExoPlayer instance
            LaunchedEffect(selectedSubtitle, isSubtitleEnabled) {
                try {
                    val currentTrack = if (isSubtitleEnabled) selectedSubtitle else null
                    val wasPlaying = exoPlayer.isPlaying
                    val currentPos = exoPlayer.currentPosition
                    val newSource = MediaSourceHelper.buildMediaSource(
                        context = context,
                        masterUrl = resolvedStream.hlsMasterUrl,
                        sub = currentTrack,
                        httpDataSourceFactory = httpDataSourceFactory
                    )
                    exoPlayer.setMediaSource(newSource, currentPos)
                    val selector = exoPlayer.trackSelectionParameters.buildUpon()
                    if (currentTrack != null) {
                        selector
                            .setPreferredTextLanguage(currentTrack.lang)
                            .setSelectUndeterminedTextLanguage(true)
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    } else {
                        selector.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    }
                    exoPlayer.trackSelectionParameters = selector.build()
                    if (wasPlaying) {
                        exoPlayer.play()
                    }
                } catch (_: Exception) {}
            }

            LaunchedEffect(exoPlayer) {
                activePlayer = exoPlayer
            }
            // Fan audio renditions out to the subtitle sheet (owned by DetailScreen).
            LaunchedEffect(audioOptions, selectedAudioLang) {
                onAudioOptionsChanged(audioOptions, selectedAudioLang)
            }
            // Registers the live audio switcher with DetailScreen (no rebuild).
            LaunchedEffect(exoPlayer) {
                onRegisterAudioSelect { option ->
                    selectedAudioLang = option.language
                    try {
                        val params = exoPlayer.trackSelectionParameters.buildUpon()
                        params.setPreferredAudioLanguage(option.language)
                        exoPlayer.trackSelectionParameters = params.build()
                    } catch (_: Exception) {}
                }
            }

            // Sync current position and periodically record progress
            LaunchedEffect(exoPlayer) {
                var lastSavedPos = 0L
                while (true) {
                    currentPosMs = exoPlayer.currentPosition
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    bufferedMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                    isPlaying = exoPlayer.isPlaying
                    if (Math.abs(currentPosMs - lastSavedPos) >= 5000L && durationMs > 0) {
                        onSaveProgress(currentPosMs, durationMs)
                        lastSavedPos = currentPosMs
                    }
                    delay(500)
                }
            }

            // Fresh stream resets the up-next state machine.
            LaunchedEffect(resolvedStream.hlsMasterUrl) {
                playbackEnded = false
                upNextCountdown = null
            }

            // Netflix-style up-next: the 90s card holds until the final stretch,
            // then runs a 10s countdown (Play Now + Dismiss) and auto-plays at 0
            // unless dismissed. STATE_ENDED feeds the same path when reached.
            val upNextActive = nextEpisode != null && !isNextEpisodeDismissed &&
                (playbackEnded || (durationMs > 60_000L && (durationMs - currentPosMs) in 0L..90_000L))
            LaunchedEffect(upNextActive, nextEpisode?.episode?.id) {
                if (!upNextActive) {
                    upNextCountdown = null
                    return@LaunchedEffect
                }
                while (!playbackEnded && durationMs > 0L && (durationMs - currentPosMs) > 20_000L) {
                    delay(500)
                }
                upNextCountdown = 10
                while ((upNextCountdown ?: 0) > 0) {
                    delay(1000)
                    upNextCountdown = (upNextCountdown ?: 1) - 1
                }
                onPlayNextEpisode()
            }

            DisposableEffect(exoPlayer) {
                // Reads TRACK_TYPE_AUDIO groups into picker options.
                fun refreshAudioOptions() {
                    try {
                        val tracks = exoPlayer.currentTracks
                        val options = mutableListOf<AudioTrackOption>()
                        var selected: String? = null
                        for (group in tracks.groups) {
                            if (group.type != C.TRACK_TYPE_AUDIO) continue
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                if (!group.isTrackSupported(i)) continue
                                val lang = format.language
                                val label = format.label
                                    ?: lang?.let { code ->
                                        try {
                                            java.util.Locale(code).displayLanguage
                                                .replaceFirstChar { it.uppercase() }
                                        } catch (_: Exception) { code.uppercase() }
                                    } ?: "Audio ${options.size + 1}"
                                options.add(AudioTrackOption(language = lang, label = label))
                                if (selected == null && group.isTrackSelected(i)) {
                                    selected = lang
                                }
                            }
                        }
                        audioOptions = options
                        if (selected != null) {
                            selectedAudioLang = selected
                        }
                    } catch (_: Exception) {}
                }
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            playbackEnded = true
                        }
                    }
                    override fun onTracksChanged(tracks: Tracks) {
                        refreshAudioOptions()
                    }
                }
                exoPlayer.addListener(listener)
                // Lets MainActivity.onUserLeaveHint pause playback on Home/recents.
                // isPlaying/progress keep flowing through the existing listener + poller.
                PipController.onPauseRequested = { exoPlayer.pause() }

                onDispose {
                    try {
                        exoPlayer.removeListener(listener)
                        val finalPos = exoPlayer.currentPosition
                        val finalDur = exoPlayer.duration
                        if (finalDur > 0) {
                            onSaveProgress(finalPos, finalDur)
                        }
                    } catch (_: Exception) {}
                    PipController.isPlaybackActive = false
                    PipController.onPauseRequested = null
                    activePlayer = null
                    exoPlayer.release()
                }
            }

            // ExoPlayer AndroidView
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        keepScreenOn = true
                        playerViewRef = this
                        post { refreshPipHint(this) }
                        subtitleView?.let { subs ->
                            subs.setApplyEmbeddedStyles(false)
                            subs.setStyle(
                                CaptionStyleCompat(
                                    android.graphics.Color.WHITE,
                                    android.graphics.Color.TRANSPARENT,
                                    android.graphics.Color.TRANSPARENT,
                                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                    android.graphics.Color.BLACK,
                                    null
                                )
                            )
                            subs.setUserDefaultTextSize()
                            subs.setBottomPaddingFraction(0.08f)
                            fun refreshPadding(videoSize: VideoSize = exoPlayer.videoSize) {
                                // Zoom fills the frame (no letterbox bars), so
                                // subtitles fall back to the 0.08 default;
                                // FIT keeps the letterbox-derived lift.
                                subs.setBottomPaddingFraction(
                                    if (zoomState.value) 0.08f
                                    else letterboxBottomPadding(
                                        width.toFloat(),
                                        height.toFloat(),
                                        videoSize.width.toFloat(),
                                        videoSize.height.toFloat()
                                    )
                                )
                            }
                            val subtitleSizeListener = object : Player.Listener {
                                override fun onVideoSizeChanged(videoSize: VideoSize) {
                                    refreshPadding(videoSize)
                                }
                            }
                            exoPlayer.addListener(subtitleSizeListener)
                            // Stashed for onRelease so the listener is removed with the view.
                            setTag(subtitleSizeListener)
                            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refreshPadding() }
                        }
                    }
                },
                // Live aspect switch (pinch / sheet row): padding refreshes on every
                // update so zoom/FIT changes apply even when resizeMode matches.
                update = { view ->
                    applyVideoZoom(view, isZoom)
                    refreshPipHint(view)
                    view.subtitleView?.setBottomPaddingFraction(
                        if (isZoom) 0.08f
                        else letterboxBottomPadding(
                            view.width.toFloat(),
                            view.height.toFloat(),
                            exoPlayer.videoSize.width.toFloat(),
                            exoPlayer.videoSize.height.toFloat()
                        )
                    )
                },
                onRelease = { view ->
                    try {
                        (view.getTag() as? Player.Listener)?.let { exoPlayer.removeListener(it) }
                    } catch (_: Exception) {}
                    playerViewRef = null
                },
            )

            // Gesture layer + overlay. Lock hides the gesture layer entirely;
            // the overlay renders an unlock affordance instead.
            if (!isInPipMode) {
                if (!isLocked) {
                var dragStartX by remember { mutableFloatStateOf(0f) }
                var hideHudJob by remember { mutableStateOf<Job?>(null) }
                // Volume-drag quantization: accumulate travel, one step per 48px.
                var volumeAccumPx by remember { mutableFloatStateOf(0f) }
                // Brightness smoothing: accumulate deltas, flush at most every ~100ms.
                var brightPending by remember { mutableFloatStateOf(0f) }
                var lastBrightApplyMs by remember { mutableLongStateOf(0L) }

                fun pokeHud() {
                    hideHudJob?.cancel()
                    hideHudJob = scope.launch {
                        delay(1500)
                        volumeIndicator = null
                        brightnessIndicator = null
                    }
                }
                fun applyBrightness(delta: Float) {
                    if (delta == 0f) return
                    val activity = context.findActivity()
                    val window = activity?.window ?: return
                    val lp = window.attributes
                    val current = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                    val updated = (current + delta).coerceIn(0.01f, 1f)
                    lp.screenBrightness = updated
                    window.attributes = lp
                    brightnessIndicator = updated
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    showControls = !showControls
                                    userInteractionCount++
                                },
                                onDoubleTap = { offset ->
                                    val isRight = offset.x >= size.width / 2
                                    val delta = if (isRight) 10_000L else -10_000L
                                    val targetPos = (exoPlayer.currentPosition + delta).coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
                                    exoPlayer.seekTo(targetPos)
                                    doubleTapSeekFeedback = if (isRight) 10 else -10
                                    userInteractionCount++
                                    scope.launch {
                                        delay(800)
                                        doubleTapSeekFeedback = null
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = { offset ->
                                    dragStartX = offset.x
                                    volumeAccumPx = 0f
                                    brightPending = 0f
                                    userInteractionCount++
                                },
                                onDragEnd = {
                                    // Flush any throttled brightness remainder.
                                    if (brightPending != 0f) {
                                        applyBrightness(brightPending)
                                        brightPending = 0f
                                        lastBrightApplyMs = android.os.SystemClock.uptimeMillis()
                                    }
                                    pokeHud()
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    val isRight = dragStartX >= size.width / 2

                                    if (isRight && audioManager != null) {
                                        // Quantized volume: one step per 48px of travel.
                                        volumeAccumPx += -dragAmount
                                        val steps = (volumeAccumPx / 48f).toInt()
                                        if (steps != 0) {
                                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                            val newVol = (curVol + steps).coerceIn(0, maxVol)
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                            volumeIndicator = newVol.toFloat() / maxVol.toFloat()
                                            volumeAccumPx -= steps * 48f
                                        }
                                    } else {
                                        // Throttled brightness: accumulate, apply every ~100ms.
                                        brightPending += -dragAmount / 350f
                                        val now = android.os.SystemClock.uptimeMillis()
                                        if (now - lastBrightApplyMs >= 100L) {
                                            applyBrightness(brightPending)
                                            brightPending = 0f
                                            lastBrightApplyMs = now
                                        }
                                    }

                                    pokeHud()
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            // Pinch-to-zoom (two-pointer only): toggles Fit<->Zoom
                            // live. Single-pointer tap / double-tap /
                            // vertical-drag detectors above are untouched —
                            // this loop never consumes until a second finger
                            // lands, and its accumulator resets per gesture.
                            // Consuming during a real pinch makes the
                            // vertical-drag detector bow out (no fighting).
                            awaitEachGesture {
                                awaitFirstDown()
                                var accum = 1f
                                var pinching = false
                                var done = false
                                while (!done) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.size >= 2) {
                                        pinching = true
                                        event.changes.forEach { it.consume() }
                                        accum *= event.calculateZoom()
                                        if (accum >= 1.25f || accum <= 0.8f) {
                                            val zoomed = !zoomState.value
                                            zoomState.value = zoomed
                                            PlayerQualityPrefs.setVideoZoom(context, zoomed)
                                            zoomFeedback = zoomed
                                            userInteractionCount++
                                            scope.launch {
                                                delay(1200)
                                                if (zoomFeedback == zoomed) zoomFeedback = null
                                            }
                                            done = true
                                        }
                                    } else if (pinching || pressed.isEmpty()) {
                                        done = true
                                    }
                                }
                            }
                        }
                )
                }

                // Overlay Controls & HUDs
                MobilePlayerOverlay(
                    showControls = showControls,
                    title = title,
                    subtitle = subtitle,
                    isPlaying = isPlaying,
                    currentPositionMs = currentPosMs,
                    durationMs = durationMs,
                    bufferedPositionMs = bufferedMs,
                    upNextCountdownSec = upNextCountdown,
                    isFullscreen = isFullscreen,
                    isSubtitleEnabled = isSubtitleEnabled,
                    nextEpisode = nextEpisode,
                    isNextEpisodeDismissed = isNextEpisodeDismissed,
                    brightnessIndicator = brightnessIndicator,
                    volumeIndicator = volumeIndicator,
                    doubleTapSeekFeedback = doubleTapSeekFeedback,
                    playbackEnded = playbackEnded,
                    onTogglePlayPause = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        userInteractionCount++
                    },
                    onSeekBy = { deltaMs ->
                        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
                        exoPlayer.seekTo(target)
                        userInteractionCount++
                    },
                    onSeekTo = { targetMs ->
                        exoPlayer.seekTo(targetMs)
                        userInteractionCount++
                    },
                    onToggleFullscreen = onToggleFullscreen,
                    onOpenSubtitlePicker = onOpenSubtitlePicker,
                    onBackClick = onBackClick,
                    onPlayNextEpisode = onPlayNextEpisode,
                    onDismissNextEpisode = onDismissNextEpisode,
                    onUserInteraction = { userInteractionCount++ },
                    onToggleControls = {
                        showControls = !showControls
                        userInteractionCount++
                    },
                    isLocked = isLocked,
                    onToggleLock = {
                        isLocked = !isLocked
                        userInteractionCount++
                    },
                    onEnterPip = {
                        context.findActivity()?.let { PipController.enterPip(it) }
                    },
                    qualityLabel = PlayerQualityPrefs.labelFor(effectiveQuality),
                    onOpenQualityPicker = {
                        showQualitySheet = true
                        userInteractionCount++
                    },
                    playbackSpeed = playbackSpeed
                )

                // Pinch-toggle feedback chip (auto-hides after 1.2s).
                zoomFeedback?.let { zoomed ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 64.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = if (zoomed) "Zoom · Fill screen" else "Fit · Full picture",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                // Quality + speed picker (Auto/1080p/720p/480p + 0.5x-2x)
                QualityPickerBottomSheet(
                    isVisible = showQualitySheet,
                    selectedQuality = requestedQuality,
                    effectiveQuality = effectiveQuality,
                    isDataSaver = isDataSaver,
                    isZoom = isZoom,
                    selectedSpeed = playbackSpeed,
                    onSelectQuality = { quality ->
                        requestedQuality = quality
                        PlayerQualityPrefs.setQuality(context, quality)
                        userInteractionCount++
                    },
                    onSelectSpeed = { speed ->
                        playbackSpeed = speed
                        PlayerQualityPrefs.setSpeed(context, speed)
                        userInteractionCount++
                    },
                    onSelectAspect = { zoom ->
                        isZoom = zoom
                        PlayerQualityPrefs.setVideoZoom(context, zoom)
                        userInteractionCount++
                    },
                    onDismiss = { showQualitySheet = false }
                )
            } else {
                // PiP mode: system owns the window; offer tap-to-restore plus a
                // pause-and-restore close affordance (both >= 48dp).
                val pipActivity = remember(context) { context.findActivity() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = {
                            pipActivity?.let { PipController.restoreToFullscreen(it) }
                        }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.tap_to_restore),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    IconButton(
                        onClick = {
                            try {
                                activePlayer?.pause()
                            } catch (_: Exception) {}
                            pipActivity?.let { PipController.restoreToFullscreen(it) }
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = Color.White
                        )
                    }
                }
            }
        } else {
            // Idle state: Video not playing automatically, wait for manual user trigger
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onPlayClick)
            ) {
                if (!previewImageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = previewImageUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BackgroundElevated)
                    )
                }

                // Dark gradient scrim overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.55f),
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                )

                // Top bar with Back Button
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Center Play Button
                Surface(
                    color = IdlixRed,
                    shape = CircleShape,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.play),
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Bottom Left Title / Subtitle preview badge
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = IdlixRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
