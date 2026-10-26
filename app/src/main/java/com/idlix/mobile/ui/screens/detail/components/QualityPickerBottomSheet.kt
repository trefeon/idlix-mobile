package com.idlix.mobile.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idlix.mobile.R
import com.idlix.mobile.player.PlayerQualityPrefs
import com.idlix.mobile.ui.theme.IdlixRed
import com.idlix.mobile.ui.theme.BackgroundElevated
import com.idlix.mobile.ui.theme.TextMuted
import com.idlix.mobile.ui.theme.TextWhite

private data class QualityOption(val key: String, val title: String, val subtitle: String)

private val QUALITY_OPTIONS = listOf(
    QualityOption(PlayerQualityPrefs.QUALITY_AUTO, "Auto", "Best available for your connection"),
    QualityOption(PlayerQualityPrefs.QUALITY_1080, "1080p", "Full HD · ~5 Mbps"),
    QualityOption(PlayerQualityPrefs.QUALITY_720, "720p", "HD · ~2.5 Mbps"),
    QualityOption(PlayerQualityPrefs.QUALITY_480, "480p", "SD · ~1 Mbps"),
)

fun speedLabel(speed: Float): String = when (speed) {
    1.0f -> "1x"
    0.5f -> "0.5x"
    0.75f -> "0.75x"
    1.25f -> "1.25x"
    1.5f -> "1.5x"
    2.0f -> "2x"
    else -> "${speed}x"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityPickerBottomSheet(
    isVisible: Boolean,
    selectedQuality: String,
    effectiveQuality: String,
    isDataSaver: Boolean,
    isZoom: Boolean = false,
    selectedSpeed: Float,
    onSelectQuality: (String) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectAspect: (Boolean) -> Unit = {},
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
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxHeight < 500.dp
            val bottomPad = if (compact) 16.dp else 32.dp
            val gapLarge = if (compact) 5.dp else 10.dp
            val gapSmall = if (compact) 4.dp else 8.dp
            val dividerPad = if (compact) 7.dp else 14.dp
            val listMax = if (compact) 180.dp else 300.dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = bottomPad)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = null,
                        tint = IdlixRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Quality",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                }

                if (isDataSaver) {
                    Spacer(modifier = Modifier.size(gapSmall))
                    Text(
                        text = "Data Saver ON — capped at 720p",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = IdlixRed
                    )
                }

                Spacer(modifier = Modifier.size(gapLarge))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = listMax),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(QUALITY_OPTIONS) { option ->
                        val isSelected = selectedQuality == option.key
                        val isEffective = effectiveQuality == option.key
                        QualityOptionItem(
                            title = option.title + if (isEffective && selectedQuality != effectiveQuality) " · active" else "",
                            subtitle = option.subtitle,
                            isSelected = isSelected,
                            onClick = { onSelectQuality(option.key) }
                        )
                    }
                }

                Spacer(modifier = Modifier.size(gapLarge))

                Text(
                    text = "Picture size",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )

                Spacer(modifier = Modifier.size(gapSmall))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isZoom,
                        onClick = { onSelectAspect(false) },
                        label = {
                            Text(
                                text = "Fit · Full picture",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IdlixRed,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.08f),
                            labelColor = TextMuted
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isZoom,
                        onClick = { onSelectAspect(true) },
                        label = {
                            Text(
                                text = "Zoom · Fill screen",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IdlixRed,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.08f),
                            labelColor = TextMuted
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = dividerPad),
                    color = Color.White.copy(alpha = 0.1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = IdlixRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Playback speed",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                }

                Spacer(modifier = Modifier.size(gapLarge))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerQualityPrefs.SPEED_OPTIONS.chunked(3).forEach { rowSpeeds ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowSpeeds.forEach { speed ->
                                FilterChip(
                                    selected = selectedSpeed == speed,
                                    onClick = { onSelectSpeed(speed) },
                                    label = {
                                        Text(
                                            text = speedLabel(speed),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = IdlixRed,
                                        selectedLabelColor = Color.White,
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        labelColor = TextMuted
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityOptionItem(
    title: String,
    subtitle: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) IdlixRed.copy(alpha = 0.15f)
                else Color.White.copy(alpha = 0.05f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isSelected) IdlixRed else TextWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.cd_selected),
                tint = IdlixRed,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
