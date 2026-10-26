package com.idlix.mobile

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.idlix.mobile.player.PipController
import com.idlix.mobile.ui.navigation.MainScaffold
import com.idlix.mobile.ui.theme.IdlixMobileTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            IdlixMobileTheme {
                MainScaffold()
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        try {
            // Already floating: leave PiP playback alone.
            if (PipController.isInPipMode.value) return
            // Manual PiP toggle in flight: let the requested entry proceed.
            if (PipController.manualPipArmed) {
                PipController.manualPipArmed = false
                return
            }
            // Home/recents: pause instead of auto-entering PiP. Disable the API-31+
            // auto-enter params synchronously so the framework doesn't yank the task
            // into PiP after we pause. Resume via Continue Watching / detail Resume
            // (position keeps saving through the existing 5s + dispose paths).
            PipController.setAutoEnterEnabled(this, false)
            PipController.pausePlayback()
        } catch (_: Throwable) {
            // Home/recents must never crash: pausing is best-effort.
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        try {
            PipController.setInPipMode(isInPictureInPictureMode)
        } catch (_: Throwable) {
        }
    }
}
