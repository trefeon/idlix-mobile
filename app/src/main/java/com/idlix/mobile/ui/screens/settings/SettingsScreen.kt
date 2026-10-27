package com.idlix.mobile.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.idlix.mobile.BuildConfig
import com.idlix.mobile.R
import com.idlix.mobile.ui.screens.settings.components.SettingsGroupDivider
import com.idlix.mobile.ui.screens.settings.components.SettingsGroupHeader
import com.idlix.mobile.ui.screens.settings.components.SettingsRow
import com.idlix.mobile.ui.screens.settings.components.SettingsSwitchRow
import com.idlix.mobile.ui.screens.settings.components.UpdateSettingsRow
import com.idlix.mobile.ui.theme.BackgroundDark
import com.idlix.mobile.ui.theme.IdlixRed
import com.idlix.mobile.ui.theme.SurfaceVariantDark
import com.idlix.mobile.ui.theme.TextDim
import com.idlix.mobile.ui.theme.TextWhite
import com.idlix.mobile.update.UpdateState

/** Trailing action min-height, shared with the update rows. */
private val SettingsActionMinHeight = 48.dp

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDataSaver by viewModel.isDataSaver.collectAsState()
    val isAudioStabilizer by viewModel.isAudioStabilizer.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    var isSubdlKeyVisible by remember { mutableStateOf(false) }
    var showSubdlDialog by remember { mutableStateOf(false) }
    var subdlField by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val subdlSavedMessage = stringResource(R.string.settings_subdl_saved)

    LaunchedEffect(uiState.subdlSaved) {
        if (uiState.subdlSaved) {
            snackbarHostState.showSnackbar(subdlSavedMessage)
            viewModel.consumeSavedMessage()
        }
    }

    if (showSubdlDialog) {
        SubdlKeyDialog(
            field = subdlField,
            onFieldChange = { subdlField = it },
            keyVisible = isSubdlKeyVisible,
            onToggleVisibility = { isSubdlKeyVisible = !isSubdlKeyVisible },
            isTesting = uiState.isTestingSubdl,
            testResult = uiState.subdlTestResult,
            testSuccess = uiState.subdlTestSuccess,
            onTest = {
                viewModel.updateSubdlKey(subdlField)
                viewModel.testSubdl()
            },
            onSave = {
                viewModel.updateSubdlKey(subdlField)
                viewModel.saveSubdlKey()
                showSubdlDialog = false
            },
            onDismiss = { showSubdlDialog = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = TextWhite
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings),
                color = TextWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Subtitles group: heading + status row opens the key dialog.
            SettingsGroupHeader(text = "Subtitles")
            SettingsRow(
                title = stringResource(R.string.subdl_api_header),
                supporting = if (uiState.subdlKey.isBlank()) "Not set" else "Configured",
                onClick = {
                    subdlField = uiState.subdlKey
                    showSubdlDialog = true
                },
                trailing = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = TextDim
                    )
                }
            )

            SettingsGroupDivider()

            // Playback group.
            SettingsGroupHeader(text = "Playback")
            SettingsSwitchRow(
                title = stringResource(R.string.settings_data_saver_title),
                supporting = stringResource(R.string.settings_data_saver_desc),
                checked = isDataSaver,
                onToggle = { viewModel.setDataSaver(it) }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_audio_stabilizer_title),
                supporting = stringResource(R.string.settings_audio_stabilizer_desc),
                checked = isAudioStabilizer,
                onToggle = { viewModel.setAudioStabilizer(it) }
            )

            SettingsGroupDivider()

            // Storage group: row-tap clears, trailing button is never red.
            SettingsGroupHeader(text = "Storage")
            SettingsRow(
                title = stringResource(R.string.settings_storage_title),
                supporting = if (uiState.cacheError) {
                    stringResource(R.string.settings_cache_error)
                } else {
                    stringResource(R.string.settings_storage_desc)
                },
                onClick = if (!uiState.cacheCleared) {
                    { viewModel.clearImageCache() }
                } else {
                    null
                },
                enabled = !uiState.cacheCleared,
                trailing = {
                    if (uiState.cacheCleared) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = stringResource(R.string.cd_done),
                                tint = TextWhite
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.settings_cache_cleared),
                                color = TextWhite
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.clearImageCache() },
                            modifier = Modifier.heightIn(min = SettingsActionMinHeight)
                        ) {
                            Text(
                                stringResource(R.string.settings_clear_cache),
                                color = TextWhite
                            )
                        }
                    }
                }
            )

            SettingsGroupDivider()

            // Updates group, row treatment.
            SettingsGroupHeader(text = "Updates")
            UpdateSettingsRow(
                state = updateState,
                onCheckClick = { viewModel.checkForUpdates() },
                onDownloadClick = {
                    (updateState as? UpdateState.Available)?.let {
                        viewModel.downloadUpdate(it.assetUrl)
                    }
                },
                onInstallClick = {
                    (updateState as? UpdateState.Downloaded)?.let {
                        viewModel.installUpdate(it.fileUri)
                    }
                }
            )

            SettingsGroupDivider()

            // About footer.
            Text(
                text = stringResource(
                    R.string.settings_about_body,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                ),
                color = TextDim,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }
    }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun SubdlKeyDialog(
    field: String,
    onFieldChange: (String) -> Unit,
    keyVisible: Boolean,
    onToggleVisibility: () -> Unit,
    isTesting: Boolean,
    testResult: String?,
    testSuccess: Boolean?,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.subdl_api_header),
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.subdl_api_desc),
                    color = TextDim,
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = field,
                    onValueChange = onFieldChange,
                    label = { Text(stringResource(R.string.settings_api_key_label), color = TextDim) },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation('*'),
                    trailingIcon = {
                        IconButton(onClick = onToggleVisibility) {
                            Icon(
                                imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (keyVisible) stringResource(R.string.settings_hide_key) else stringResource(R.string.settings_show_key),
                                tint = TextDim
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceVariantDark,
                        unfocusedContainerColor = SurfaceVariantDark,
                        focusedBorderColor = IdlixRed,
                        unfocusedBorderColor = SurfaceVariantDark,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )

                if (testResult != null) {
                    Text(
                        text = testResult,
                        color = if (testSuccess == true) TextWhite else IdlixRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .size(16.dp),
                        color = IdlixRed
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = IdlixRed),
                modifier = Modifier.heightIn(min = SettingsActionMinHeight)
            ) {
                Text(stringResource(R.string.settings_save_key))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onTest,
                enabled = !isTesting,
                modifier = Modifier.heightIn(min = SettingsActionMinHeight)
            ) {
                Text(
                    stringResource(R.string.test_subdl_key),
                    color = TextWhite
                )
            }
        }
    )
}
