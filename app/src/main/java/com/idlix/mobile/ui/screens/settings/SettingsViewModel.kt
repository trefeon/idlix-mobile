package com.idlix.mobile.ui.screens.settings

// Update wiring mirrors the TV SettingsViewModel pattern:
// check delegates to UpdateChecker, download collects UpdateDownloader,
// install routes through UpdateInstaller.
import android.app.Application

import coil.Coil
import com.idlix.mobile.BuildConfig
import com.idlix.mobile.R
import com.idlix.mobile.update.UpdateChecker
import com.idlix.mobile.update.UpdateDownloader
import com.idlix.mobile.update.UpdateInstaller
import com.idlix.mobile.update.UpdateState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.repository.SubdlRepository
import com.idlix.mobile.player.PlayerQualityPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val subdlKey: String = "",
    val isTestingSubdl: Boolean = false,
    val subdlTestResult: String? = null,
    val subdlTestSuccess: Boolean? = null,
    val subdlSaved: Boolean = false,
    val cacheCleared: Boolean = false,
    val cacheError: Boolean = false
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
        _uiState.value = _uiState.value.copy(
            subdlKey = key,
            subdlTestResult = null,
            subdlTestSuccess = null
        )
    }

    fun saveSubdlKey() {
        SubdlRepository.setApiKey(_uiState.value.subdlKey)
        _uiState.value = _uiState.value.copy(subdlSaved = true)
    }

    fun consumeSavedMessage() {
        _uiState.value = _uiState.value.copy(subdlSaved = false)
    }

    // Playback: Data Saver caps streaming quality at 720p (user-safe section).
    private val _isDataSaver = MutableStateFlow(
        PlayerQualityPrefs.isDataSaver(getApplication())
    )
    val isDataSaver: StateFlow<Boolean> = _isDataSaver.asStateFlow()

    fun setDataSaver(enabled: Boolean) {
        PlayerQualityPrefs.setDataSaver(getApplication(), enabled)
        _isDataSaver.value = enabled
    }

    fun testSubdl() {
        if (_uiState.value.isTestingSubdl) return
        // Test the key currently in the field: the repository only knows the
        // last saved key, so testing before persisting would probe the wrong key.
        SubdlRepository.setApiKey(_uiState.value.subdlKey)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isTestingSubdl = true,
                subdlTestResult = null,
                subdlTestSuccess = null
            )
            val app = getApplication<Application>()
            try {
                val result = SubdlRepository.searchAllSubtitles(title = "Avatar", type = "movie")
                _uiState.value = if (result.isNotEmpty()) {
                    _uiState.value.copy(
                        isTestingSubdl = false,
                        subdlTestResult = app.getString(R.string.settings_subdl_valid_count, result.size),
                        subdlTestSuccess = true
                    )
                } else {
                    // searchAllSubtitles folds HTTP/status failures into an
                    // empty list, and "Avatar" always has subtitles — so empty
                    // is never "valid", it means bad key or exhausted quota.
                    _uiState.value.copy(
                        isTestingSubdl = false,
                        subdlTestResult = app.getString(R.string.settings_subdl_empty),
                        subdlTestSuccess = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTestingSubdl = false,
                    subdlTestResult = app.getString(
                        R.string.settings_subdl_failed,
                        e.localizedMessage?.take(120) ?: "—"
                    ),
                    subdlTestSuccess = false
                )
            }
        }
    }

    fun clearImageCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(cacheCleared = false, cacheError = false)
            }
            val cleared = try {
                context.cacheDir.resolve("image_cache")
                    .takeIf { it.exists() }
                    ?.deleteRecursively()
                // Disk delete alone leaves decoded bitmaps alive for the rest
                // of the process; drop the memory cache too (best-effort).
                try {
                    Coil.imageLoader(context).memoryCache?.clear()
                } catch (_: Exception) {
                }
                true
            } catch (_: Exception) {
                false
            }
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    cacheCleared = cleared,
                    cacheError = !cleared
                )
            }
            // Transient confirmation: re-arm the button shortly after so a
            // second clear later in the same session is possible. Cancellation
            // (screen gone) propagates — never swallowed into an error state.
            if (cleared) {
                delay(3000)
                _uiState.value = _uiState.value.copy(cacheCleared = false)
            }
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
                UpdateChecker.checkForUpdate(
                    getApplication(),
                    BuildConfig.VERSION_CODE.toLong()
                )
            } catch (e: Exception) {
                UpdateState.Failed(
                    getApplication<Application>().getString(
                        R.string.update_err_failed,
                        e.localizedMessage?.take(120) ?: "—"
                    )
                )
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
                _updateState.value = UpdateState.Failed(
                    getApplication<Application>().getString(
                        R.string.dl_err_query,
                        e.localizedMessage?.take(120) ?: "—"
                    )
                )
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
            _updateState.value = UpdateState.Failed(
                getApplication<Application>().getString(
                    R.string.update_err_install,
                    e.localizedMessage?.take(120) ?: "—"
                )
            )
        }
    }
}
