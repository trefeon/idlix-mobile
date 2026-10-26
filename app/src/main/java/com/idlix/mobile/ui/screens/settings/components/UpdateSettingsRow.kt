package com.idlix.mobile.ui.screens.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idlix.mobile.R
import com.idlix.mobile.ui.theme.IdlixRed
import com.idlix.mobile.ui.theme.SurfaceDark
import com.idlix.mobile.ui.theme.TextMuted
import com.idlix.mobile.ui.theme.TextWhite
import com.idlix.mobile.update.UpdateState

/**
 * Self-update row for the settings screen. Pure view: every transition is
 * driven by [state] from the owning ViewModel, clicks delegate outward.
 */
@Composable
fun UpdateSettingsRow(
    state: UpdateState,
    onCheckClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onInstallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.update_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )
            Spacer(modifier = Modifier.height(8.dp))

            when (state) {
                is UpdateState.Idle -> {
                    UpdateButton(
                        label = stringResource(R.string.update_check),
                        onClick = onCheckClick
                    )
                }
                is UpdateState.Checking -> {
                    CircularProgressIndicator(color = IdlixRed)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.update_checking),
                        fontSize = 14.sp,
                        color = TextMuted
                    )
                }
                is UpdateState.UpToDate -> {
                    Text(
                        text = stringResource(
                            R.string.update_up_to_date,
                            state.currentVersion
                        ),
                        fontSize = 14.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    UpdateButton(
                        label = stringResource(R.string.update_check),
                        onClick = onCheckClick,
                        primary = false
                    )
                }
                is UpdateState.Available -> {
                    Text(
                        text = stringResource(
                            R.string.update_available_title,
                            state.version
                        ),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextWhite
                    )
                    if (state.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.notes.trim().take(600),
                            fontSize = 13.sp,
                            color = TextMuted,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.update_size,
                            formatSize(state.sizeBytes)
                        ),
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    UpdateButton(
                        label = stringResource(R.string.update_download),
                        onClick = onDownloadClick
                    )
                }
                is UpdateState.Downloading -> {
                    val percent = (state.progress.coerceIn(0f, 1f) * 100).toInt()
                    Text(
                        text = stringResource(R.string.update_downloading, percent),
                        fontSize = 14.sp,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = IdlixRed
                    )
                }
                is UpdateState.Downloaded -> {
                    Text(
                        text = stringResource(R.string.update_downloaded),
                        fontSize = 14.sp,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    UpdateButton(
                        label = stringResource(R.string.update_install),
                        onClick = onInstallClick
                    )
                }
                is UpdateState.Failed -> {
                    Text(
                        text = stringResource(R.string.update_failed, state.reason),
                        fontSize = 14.sp,
                        color = TextMuted,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    UpdateButton(
                        label = stringResource(R.string.update_retry),
                        onClick = onCheckClick
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean = true
) {
    if (primary) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = IdlixRed)
        ) {
            Text(text = label)
        }
    } else {
        androidx.compose.material3.OutlinedButton(onClick = onClick) {
            Text(text = label, color = TextWhite)
        }
    }
}

private fun formatSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "—"
    val kb = sizeBytes / 1024.0
    if (kb < 1024) return "%d KB".format(kb.toInt())
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
