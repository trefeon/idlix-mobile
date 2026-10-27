package com.idlix.mobile.ui.screens.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import com.idlix.mobile.ui.theme.TextWhite
import com.idlix.mobile.update.UpdateState

/**
 * Self-update row for the settings screen, row treatment (no card wrapper).
 * Pure view: every transition is driven by [state] from the owning
 * ViewModel, clicks delegate outward.
 *
 * Button rank: only Download/Install are filled red. Check/Retry are
 * outlined; progress states show no button.
 */
@Composable
fun UpdateSettingsRow(
    state: UpdateState,
    onCheckClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onInstallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        is UpdateState.Idle -> {
            SettingsRow(
                title = stringResource(R.string.update_title),
                onClick = onCheckClick,
                modifier = modifier,
                trailing = {
                    OutlinedUpdateButton(
                        label = stringResource(R.string.update_check),
                        onClick = onCheckClick
                    )
                }
            )
        }
        is UpdateState.Checking -> {
            SettingsRow(
                title = stringResource(R.string.update_title),
                supporting = stringResource(R.string.update_checking),
                onClick = null,
                modifier = modifier,
                trailing = {
                    CircularProgressIndicator(color = IdlixRed)
                }
            )
        }
        is UpdateState.UpToDate -> {
            SettingsRow(
                title = stringResource(R.string.update_title),
                supporting = stringResource(
                    R.string.update_up_to_date,
                    state.currentVersion
                ),
                onClick = onCheckClick,
                modifier = modifier,
                trailing = {
                    OutlinedUpdateButton(
                        label = stringResource(R.string.update_check),
                        onClick = onCheckClick
                    )
                }
            )
        }
        is UpdateState.Available -> {
            SettingsRow(
                title = stringResource(
                    R.string.update_available_title,
                    state.version
                ),
                supporting = buildString {
                    if (state.notes.isNotBlank()) {
                        append(state.notes.trim().take(600))
                        append(" · ")
                    }
                    append("Size: ${formatSize(state.sizeBytes)}")
                },
                onClick = onDownloadClick,
                modifier = modifier,
                trailing = {
                    FilledUpdateButton(
                        label = stringResource(R.string.update_download),
                        onClick = onDownloadClick
                    )
                }
            )
        }
        is UpdateState.Downloading -> {
            val percent = (state.progress.coerceIn(0f, 1f) * 100).toInt()
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.update_downloading, percent),
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = IdlixRed
                )
            }
        }
        is UpdateState.Downloaded -> {
            SettingsRow(
                title = stringResource(R.string.update_downloaded),
                onClick = onInstallClick,
                modifier = modifier,
                trailing = {
                    FilledUpdateButton(
                        label = stringResource(R.string.update_install),
                        onClick = onInstallClick
                    )
                }
            )
        }
        is UpdateState.Failed -> {
            SettingsRow(
                title = stringResource(R.string.update_title),
                supporting = stringResource(R.string.update_failed, state.reason),
                onClick = onCheckClick,
                modifier = modifier,
                trailing = {
                    OutlinedUpdateButton(
                        label = stringResource(R.string.update_retry),
                        onClick = onCheckClick
                    )
                }
            )
        }
    }
}

@Composable
private fun FilledUpdateButton(
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = IdlixRed)
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OutlinedUpdateButton(
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        Text(
            text = label,
            color = TextWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
