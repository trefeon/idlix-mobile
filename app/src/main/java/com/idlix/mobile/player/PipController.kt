package com.idlix.mobile.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PipController {
    var isPlaybackActive: Boolean = false

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    @Volatile
    private var sourceRectHint: Rect? = null

    fun setInPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    fun setSourceRectHint(rect: Rect?) {
        sourceRectHint = rect
    }

    fun enterPip(activity: Activity, rect: Rect? = sourceRectHint): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return try {
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                if (rect != null) {
                    builder.setSourceRectHint(rect)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(true)
                    builder.setSeamlessResizeEnabled(true)
                }
                activity.enterPictureInPictureMode(builder.build())
            } catch (e: Exception) {
                false
            }
        }
        return false
    }

    /**
     * Enables swipe-home auto-enter on API 31+. Called while playback is
     * active; disabled when playback stops so Home doesn't yank video away.
     */
    fun setAutoEnterEnabled(activity: Activity, enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                activity.setPictureInPictureParams(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .setAutoEnterEnabled(enabled)
                        .build()
                )
            } catch (e: Exception) {
                // Auto-enter is best-effort; manual enter still works.
            }
        }
    }

    /** Brings the task to front, which exits PiP back to fullscreen playback. */
    fun restoreToFullscreen(activity: Activity) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(activity.packageName)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            // Last resort: toggling PiP off is handled by the system UI.
        }
    }
}
