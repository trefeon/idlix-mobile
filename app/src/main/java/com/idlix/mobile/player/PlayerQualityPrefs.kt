package com.idlix.mobile.player

import android.content.Context
import android.content.SharedPreferences

/**
 * Small prefs helper for mobile playback quality / speed / data-saver.
 *
 * Quality is stored as a string key ("auto" default); speed as float (1.0f
 * default); data-saver as boolean (false default). When Data-Saver is ON the
 * [effectiveQuality] caps any selection above 720p down to 720p.
 */
object PlayerQualityPrefs {

    private const val PREFS_NAME = "idlix_mobile_player"
    private const val KEY_QUALITY = "player_quality"
    private const val KEY_SPEED = "player_speed"
    private const val KEY_DATA_SAVER = "player_data_saver"
    private const val KEY_VIDEO_ZOOM = "player_video_zoom"
    const val QUALITY_AUTO = "auto"
    const val QUALITY_1080 = "1080p"
    const val QUALITY_720 = "720p"
    const val QUALITY_480 = "480p"

    /** Bitrate caps per tier (bits/sec). Auto clears constraints instead. */
    const val BITRATE_1080 = 5_000_000
    const val BITRATE_720 = 2_500_000
    const val BITRATE_480 = 1_000_000

    val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val KNOWN_QUALITIES = setOf(QUALITY_AUTO, QUALITY_1080, QUALITY_720, QUALITY_480)

    fun getQuality(context: Context): String {
        val stored = prefs(context).getString(KEY_QUALITY, QUALITY_AUTO) ?: QUALITY_AUTO
        // Corrupt/legacy values fall back to Auto (unconstrained) instead of
        // silently pinning a stale cap.
        return if (stored in KNOWN_QUALITIES) stored else QUALITY_AUTO
    }

    fun setQuality(context: Context, quality: String) {
        val safe = if (quality in KNOWN_QUALITIES) quality else QUALITY_AUTO
        prefs(context).edit().putString(KEY_QUALITY, safe).apply()
    }

    fun getSpeed(context: Context): Float {
        val stored = prefs(context).getFloat(KEY_SPEED, 1.0f)
        // Corrupt values (0, negative, NaN, absurd) would freeze or blast
        // playback via setPlaybackSpeed — fall back to 1x.
        return if (stored.isFinite() && stored in 0.25f..4.0f) stored else 1.0f
    }

    fun setSpeed(context: Context, speed: Float) {
        val safe = if (speed.isFinite() && speed in 0.25f..4.0f) speed else 1.0f
        prefs(context).edit().putFloat(KEY_SPEED, safe).apply()
    }

    fun isDataSaver(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DATA_SAVER, false)

    fun setDataSaver(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DATA_SAVER, enabled).apply()
    }

    /**
     * Opt-in Zoom-fill aspect. FIT (false, default) shows the entire picture
     * without cropping; Zoom (true) fills the screen, cropping the long
     * edges. Applied via PlayerView resizeMode live (no player rebuild).
     * NEVER RESIZE_MODE_FILL (would stretch).
     */
    fun isVideoZoom(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VIDEO_ZOOM, false)

    fun setVideoZoom(context: Context, zoom: Boolean) {
        prefs(context).edit().putBoolean(KEY_VIDEO_ZOOM, zoom).apply()
    }

    /**
     * Effective tier after the Data-Saver cap: Data-Saver ON forces any
     * selection above 720p (Auto / 1080p) down to 720p.
     */
    fun effectiveQuality(requested: String, dataSaver: Boolean): String {
        if (!dataSaver) return requested
        return when (requested) {
            QUALITY_1080, QUALITY_AUTO -> QUALITY_720
            else -> requested
        }
    }

    fun labelFor(quality: String): String = when (quality) {
        QUALITY_1080 -> "1080p"
        QUALITY_720 -> "720p"
        QUALITY_480 -> "480p"
        else -> "Auto"
    }
}
