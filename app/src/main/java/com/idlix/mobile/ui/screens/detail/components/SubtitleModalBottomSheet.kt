package com.idlix.mobile.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idlix.data.model.SubdlSubtitle
import com.idlix.data.model.SubtitleTrack
import com.idlix.mobile.R
import com.idlix.mobile.ui.theme.BackgroundElevated
import com.idlix.mobile.ui.theme.IdlixRed
import com.idlix.mobile.ui.theme.TextMuted
import com.idlix.mobile.ui.theme.TextSecondary
import com.idlix.mobile.ui.theme.TextWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleModalBottomSheet(
    isVisible: Boolean,
    availableSubtitles: List<SubtitleTrack>,
    selectedSubtitle: SubtitleTrack?,
    isSubtitleEnabled: Boolean,
    subdlSubtitles: List<SubdlSubtitle>,
    isSearchingSubdl: Boolean,
    onToggleSubtitle: () -> Unit,
    onSelectTrack: (SubtitleTrack?) -> Unit,
    onSelectSubdl: (SubdlSubtitle) -> Unit,
    onSearchSubdl: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BackgroundElevated,
        contentColor = TextWhite,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(2.dp)
            ) {
                Box(modifier = Modifier.size(width = 36.dp, height = 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ClosedCaption,
                        contentDescription = null,
                        tint = IdlixRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.audio_and_subtitles),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                }

                // Switch Subtitles On / Off
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isSubtitleEnabled) stringResource(R.string.subtitle_on) else stringResource(R.string.subtitle_off),
                        fontSize = 13.sp,
                        color = if (isSubtitleEnabled) IdlixRed else TextMuted
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isSubtitleEnabled,
                        onCheckedChange = { onToggleSubtitle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = IdlixRed,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = Color.White.copy(alpha = 0.1f)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Off option
                item {
                    val isOffSelected = !isSubtitleEnabled || selectedSubtitle == null
                    SubtitleOptionItem(
                        title = stringResource(R.string.subtitles_off),
                        subtitle = null,
                        isSelected = isOffSelected,
                        onClick = { onSelectTrack(null) }
                    )
                }

                // 2. Default Embedded Subtitles Header
                if (availableSubtitles.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.default_subtitle),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                        )
                    }

                    items(availableSubtitles) { track ->
                        val isSelected = isSubtitleEnabled && selectedSubtitle?.path == track.path
                        SubtitleOptionItem(
                            title = track.label.ifBlank {
                                if (track.lang == "id") "Indonesian" else track.lang.uppercase()
                            },
                            subtitle = if (track.lang == "id") "Bahasa Indonesia" else track.lang,
                            isSelected = isSelected,
                            onClick = { onSelectTrack(track) }
                        )
                    }
                }

                // 3. SubDL Subtitles Section
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.subdl_subtitles_header),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )

                        if (!isSearchingSubdl) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(onClick = onSearchSubdl)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = IdlixRed,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.search_subdl),
                                    fontSize = 11.sp,
                                    color = IdlixRed,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                if (isSearchingSubdl) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = IdlixRed,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.searching_subdl),
                                fontSize = 13.sp,
                                color = TextMuted
                            )
                        }
                    }
                } else if (subdlSubtitles.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_subdl_found),
                            fontSize = 13.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(subdlSubtitles) { subdl ->
                        val subRelease = subdl.releaseName?.ifBlank { subdl.name.orEmpty() } ?: subdl.name.orEmpty().ifBlank { "Subtitle" }
                        val isSelected = isSubtitleEnabled && selectedSubtitle?.label == subRelease
                        val subLang = subdl.language ?: subdl.lang ?: "ID"
                        val subAuthor = subdl.author?.ifBlank { "SubDL" } ?: "SubDL"

                        SubtitleOptionItem(
                            title = subRelease,
                            subtitle = "$subLang • $subAuthor",
                            isSelected = isSelected,
                            showDownloadIcon = !isSelected,
                            onClick = { onSelectSubdl(subdl) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleOptionItem(
    title: String,
    subtitle: String?,
    isSelected: Boolean,
    showDownloadIcon: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) IdlixRed.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) TextWhite else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(IdlixRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else if (showDownloadIcon) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
