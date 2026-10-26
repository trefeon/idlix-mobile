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

    fun getQuality(context: Context): String =
        prefs(context).getString(KEY_QUALITY, QUALITY_AUTO) ?: QUALITY_AUTO

    fun setQuality(context: Context, quality: String) {
        prefs(context).edit().putString(KEY_QUALITY, quality).apply()
    }

    fun getSpeed(context: Context): Float =
        prefs(context).getFloat(KEY_SPEED, 1.0f)

    fun setSpeed(context: Context, speed: Float) {
        prefs(context).edit().putFloat(KEY_SPEED, speed).apply()
    }

    fun isDataSaver(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DATA_SAVER, false)

    fun setDataSaver(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DATA_SAVER, enabled).apply()
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
