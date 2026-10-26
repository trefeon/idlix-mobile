package com.idlix.mobile.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.idlix.mobile.BuildConfig
import com.idlix.mobile.R
import com.idlix.mobile.ui.screens.settings.components.UpdateSettingsRow
import com.idlix.mobile.ui.theme.BackgroundDark
import com.idlix.mobile.update.UpdateState
import com.idlix.mobile.ui.theme.IdlixAccentCyan
import com.idlix.mobile.ui.theme.IdlixRed
import com.idlix.mobile.ui.theme.SurfaceDark
import com.idlix.mobile.ui.theme.SurfaceVariantDark
import com.idlix.mobile.ui.theme.TextDim
import com.idlix.mobile.ui.theme.TextMuted
import com.idlix.mobile.ui.theme.TextWhite

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDataSaver by viewModel.isDataSaver.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    var isSubdlKeyVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val subdlSavedMessage = stringResource(R.string.settings_subdl_saved)

    LaunchedEffect(uiState.subdlSaved) {
        if (uiState.subdlSaved) {
            snackbarHostState.showSnackbar(subdlSavedMessage)
            viewModel.consumeSavedMessage()
        }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // SubDL API Key Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.subdl_api_header),
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.subdl_api_desc),
                        color = TextMuted,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = uiState.subdlKey,
                        onValueChange = { viewModel.updateSubdlKey(it) },
                        label = { Text(stringResource(R.string.settings_api_key_label), color = TextDim) },
                        singleLine = true,
                        visualTransformation = if (isSubdlKeyVisible) VisualTransformation.None else PasswordVisualTransformation('*'),
                        trailingIcon = {
                            IconButton(onClick = { isSubdlKeyVisible = !isSubdlKeyVisible }) {
                                Icon(
                                    imageVector = if (isSubdlKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isSubdlKeyVisible) stringResource(R.string.settings_hide_key) else stringResource(R.string.settings_show_key),
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
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (uiState.subdlTestResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.subdlTestResult ?: "",
                            color = if (uiState.subdlTestSuccess == true) Color(0xFF4CAF50) else IdlixRed,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.testSubdl() },
                            enabled = !uiState.isTestingSubdl,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            if (uiState.isTestingSubdl) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = IdlixRed)
                            } else {
                                Text(stringResource(R.string.test_subdl_key), color = TextWhite)
                            }
                        }

                        Button(
                            onClick = { viewModel.saveSubdlKey() },
                            colors = ButtonDefaults.buttonColors(containerColor = IdlixRed),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.settings_save_key))
                        }
                    }
                }
            }

            // Playback: Data Saver (user-safe section)
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_data_saver_title),
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.settings_data_saver_desc),
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Switch(
                            checked = isDataSaver,
                            onCheckedChange = { viewModel.setDataSaver(it) }
                        )
                    }
                }
            }
            // Storage & Cache
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_storage_title),
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_storage_desc),
                        color = TextMuted,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { viewModel.clearImageCache() },
                        enabled = !uiState.cacheCleared,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        if (uiState.cacheCleared) {
                            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.cd_done), tint = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_cache_cleared), color = Color(0xFF4CAF50))
                        } else {
                            Text(stringResource(R.string.settings_clear_cache), color = TextWhite)
                        }
                    }
                    if (uiState.cacheError) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_cache_error),
                            color = IdlixRed,
                            fontSize = 12.sp
                        )
                    }
                }
            }
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

            // About Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_about_title),
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.settings_about_body,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE
                        ),
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
