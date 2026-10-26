package com.idlix.mobile.ui.screens.settings

// Update wiring mirrors the TV SettingsViewModel pattern:
// check delegates to UpdateChecker, download collects UpdateDownloader,
// install routes through UpdateInstaller.
import android.app.Application

import com.idlix.mobile.BuildConfig
import com.idlix.mobile.update.UpdateChecker
import com.idlix.mobile.update.UpdateDownloader
import com.idlix.mobile.update.UpdateInstaller
import com.idlix.mobile.update.UpdateState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.repository.SubdlRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val subdlKey: String = "",
    val isTestingSubdl: Boolean = false,
    val subdlTestResult: String? = null,
    val cacheCleared: Boolean = false
)

class SettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            subdlKey = SubdlRepository.getApiKey()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun updateSubdlKey(key: String) {
        _uiState.value = _uiState.value.copy(subdlKey = key, subdlTestResult = null)
    }

    fun saveSubdlKey() {
        SubdlRepository.setApiKey(_uiState.value.subdlKey)
    }

    fun testSubdl() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingSubdl = true, subdlTestResult = null)
            try {
                val result = SubdlRepository.searchAllSubtitles(title = "Avatar", type = "movie")
                _uiState.value = _uiState.value.copy(
                    isTestingSubdl = false,
                    subdlTestResult = "SubDL API key is valid (${result.size} subtitles found)"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTestingSubdl = false,
                    subdlTestResult = "Failed: ${e.localizedMessage}"
                )
            }
        }
    }

    fun clearImageCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                val cacheDir = context.cacheDir.resolve("image_cache")
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                }
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(cacheCleared = true)
                }
            } catch (_: Exception) {}
        }
    }

    // -------------------------------------------------------------- update
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    fun checkForUpdates() {
        val cur = _updateState.value
        if (cur is UpdateState.Checking || cur is UpdateState.Downloading) return
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            _updateState.value = try {
                UpdateChecker.checkForUpdate(BuildConfig.VERSION_CODE.toLong())
            } catch (e: Exception) {
                UpdateState.Failed(e.localizedMessage ?: "Check failed")
            }
        }
    }

    fun downloadUpdate(assetUrl: String) {
        if (_updateState.value !is UpdateState.Available) return
        viewModelScope.launch {
            try {
                UpdateDownloader.download(getApplication(), assetUrl).collect { state ->
                    _updateState.value = state
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Failed(e.localizedMessage ?: "Download failed")
            }
        }
    }

    fun installUpdate(fileUri: String) {
        try {
            val ctx = getApplication<Application>()
            if (!UpdateInstaller.canInstallUnknownApps(ctx)) {
                ctx.startActivity(UpdateInstaller.unknownAppSourcesIntent(ctx))
                return
            }
            UpdateInstaller.installApk(ctx, fileUri)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Failed(e.localizedMessage ?: "Install failed")
        }
    }
}
