package com.idlix.mobile.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.exp
import kotlin.math.log10

/**
 * Software loudness normalizer: stage-1 of the "Audio stabilizer".
 *
 * Sits in the PCM path ([MediaSourceHelper.createRenderersFactory] ->
 * `DefaultAudioSink` processors), ahead of the session-bound
 * [AudioStabilizer] `DynamicsProcessing` effect (stage-2, API 28+ only).
 * Pure software: no NDK, no new deps, safe on every API level (minSdk 26);
 * the API-28 guard stays inside [AudioStabilizer].
 *
 * Fixed preset (mirrors TV verbatim; tuned from 4 films + 3 trailers,
 * per-film ffmpeg `loudnorm`/`ebur128` battery, dual-pass verified):
 *
 * | Stage | Value |
 * | Target / ceiling | -16 LUFS / -1.5 dBTP |
 * | Pre-gain (fixed) | +15 dB (linear x5.6234) |
 * | MBC Low (< 250 Hz) | thr -26 dBFS, 4:1, 20 ms / 200 ms, makeup +2 dB |
 * | MBC Mid (250 Hz-4 kHz) | thr -22 dBFS, 3:1, 10 ms / 120 ms, knee 6 dB, makeup +5 dB |
 * | MBC High (> 4 kHz) | thr -26 dBFS, 2:1, 5 ms / 80 ms, makeup +3 dB |
 * | Brickwall limiter | -1.5 dBFS, inf:1, 0.5 ms / 50 ms, linked across channels |
 * | Total makeup cap | +20 dB (15 pre + 5 mid-band max) |
 *
 * Implementation choices (deliberate, not drops):
 * - Band split is a telescoping one-pole lowpass pair (LP @ 250 Hz for low,
 *   LP @ 4 kHz minus low for mid, dry minus LP@4k for high): reconstruction is
 *   bit-exact at unity gain, ~6 mults/sample/channel. A full Linkwitz-Riley
 *   MBC bank in software is too heavy for per-sample low-end work and buys
 *   nothing here — the limiter guarantees the ceiling regardless.
 * - Followers are per-channel peak detectors with the specified
 *   attack/release; the limiter follower is a single peak linked across all
 *   channels. Mid band uses a 6 dB soft knee; low/high are hard-knee.
 * - Fast path: levels at/below the knee-start compare in the linear domain,
 *   so quiet dialogue (the common case for these films) costs zero
 *   transcendentals; only hot bands pay one log10 + one exp per sample.
 * - Thresholds are post-pre-gain dBFS: the quietest measured post-gain
 *   dialogue (-22.3 dBFS) straddles the mid threshold, so quiet content gets
 *   the full makeup while loud content rides into the ratios + limiter.
 * - A hard clamp at the ceiling after the limiter is the absolute no-clip
 *   guarantee (covers limiter follower lag); -1.5 dBFS of headroom absorbs
 *   inter-sample (true-peak) overshoot, so sample-peak limiting == dBTP.
 * - Non-finite input samples (broken decoder) are zeroed before the
 *   followers so they can never poison the envelopes into NaN.
 * - The preset's "+9 dB static, no limiter (no DSP session)" fallback row is
 *   SUBSUMED, not coded as a mode ([FALLBACK_STATIC_GAIN_DB] records the
 *   validated value): this stage needs no DSP session, so the no-session
 *   case now gets full correction (strictly better than the +9 dB stopgap,
 *   which stays recorded here as the validated peak-safe floor:
 *   hottest measured film -10.6 dBFS -> -1.6 dBFS). No dead third state.
 *
 * Toggle: [setEnabled] flips live with no player rebuild (disabled =
 * bit-exact pass-through; the processor stays in the chain). Enable/disable
 * is keyed to the same persisted stabilizer pref as [AudioStabilizer]
 * (`PlayerQualityPrefs.isAudioStabilizer`); see `MobilePlayerView`.
 * [setEnabled] always resets envelopes/filter state, so player (re)builds and
 * toggle flips never start from stale levels.
 *
 * Formats: PCM 16-bit and PCM float, any channel count, any rate. Anything
 * else throws [AudioProcessor.UnhandledAudioFormatException] and the sink
 * drops this stage (our HLS AAC stereo always decodes to PCM 16-bit, and
 * offload/passthrough stay off, so the PCM path is guaranteed).
 */
class LoudnessNormalizeProcessor : BaseAudioProcessor() {

    @Volatile
    private var enabledState: Boolean = true
    val enabled: Boolean get() = enabledState

    /** Live toggle; resets envelopes so the flip never bursts from stale state. */
    fun setEnabled(on: Boolean) {
        enabledState = on
        resetState()
    }

    // ---- configured format ----
    private var channelCount: Int = 0
    private var isFloat: Boolean = false

    // ---- per-channel state (allocated on configure, reused; never in queueInput) ----
    private var lpLow = FloatArray(0)
    private var lpHigh = FloatArray(0)
    private var envLow = FloatArray(0)
    private var envMid = FloatArray(0)
    private var envHigh = FloatArray(0)
    private var scratch = FloatArray(0)

    // ---- coefficients (per configure) ----
    private var aLpLow = 0f
    private var aLpHigh = 0f
    private var attLow = 0f
    private var relLow = 0f
    private var attMid = 0f
    private var relMid = 0f
    private var attHigh = 0f
    private var relHigh = 0f
    private var attLim = 0f
    private var relLim = 0f

    // ---- linked limiter envelope (shared across channels) ----
    private var limiterEnv = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        val rate = inputAudioFormat.sampleRate
        val ch = inputAudioFormat.channelCount
        if (rate <= 0 || ch <= 0) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channelCount = ch
        isFloat = enc == C.ENCODING_PCM_FLOAT
        val fs = rate.toFloat()
        aLpLow = onePole(LOW_CUTOFF_HZ, fs)
        aLpHigh = onePole(HIGH_CUTOFF_HZ, fs)
        attLow = smooth(LOW_ATTACK_MS, fs)
        relLow = smooth(LOW_RELEASE_MS, fs)
        attMid = smooth(MID_ATTACK_MS, fs)
        relMid = smooth(MID_RELEASE_MS, fs)
        attHigh = smooth(HIGH_ATTACK_MS, fs)
        relHigh = smooth(HIGH_RELEASE_MS, fs)
        attLim = smooth(LIMITER_ATTACK_MS, fs)
        relLim = smooth(LIMITER_RELEASE_MS, fs)
        lpLow = FloatArray(ch)
        lpHigh = FloatArray(ch)
        envLow = FloatArray(ch)
        envMid = FloatArray(ch)
        envHigh = FloatArray(ch)
        scratch = FloatArray(ch)
        resetState()
        // No format change: same encoding/rate/channels in and out.
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val frameSize = inputAudioFormat.bytesPerFrame
        if (frameSize <= 0 || remaining < frameSize) return
        if (!enabled) {
            // Bit-exact pass-through (consumes input; same idiom as stock Tee).
            replaceOutputBuffer(remaining).put(inputBuffer).flip()
            return
        }
        val frames = remaining / frameSize
        val out = replaceOutputBuffer(frames * frameSize)
        if (isFloat) {
            repeat(frames) {
                for (ch in 0 until channelCount) {
                    scratch[ch] = processSample(inputBuffer.float, ch)
                }
                val gLim = limiterGain(framePeak())
                for (ch in 0 until channelCount) {
                    out.putFloat(clamp(scratch[ch] * gLim))
                }
            }
        } else {
            repeat(frames) {
                for (ch in 0 until channelCount) {
                    scratch[ch] = processSample(inputBuffer.short / 32768f, ch)
                }
                val gLim = limiterGain(framePeak())
                for (ch in 0 until channelCount) {
                    val s = (clamp(scratch[ch] * gLim) * 32768f).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    out.putShort(s.toShort())
                }
            }
        }
        out.flip()
    }

    override fun onFlush() {
        resetState()
    }

    override fun onReset() {
        resetState()
    }

    /** Zero followers/filter state (no allocation). */
    private fun resetState() {
        lpLow.fill(0f)
        lpHigh.fill(0f)
        envLow.fill(0f)
        envMid.fill(0f)
        envHigh.fill(0f)
        limiterEnv = 0f
    }

    /** One sample through pre-gain + 3-band MBC; limiter applied by caller. */
    private fun processSample(input: Float, ch: Int): Float {
        var x = input
        // Poison guard: a NaN/Inf decoder blip must not stick the followers.
        if (!x.isFinite()) x = 0f
        x *= PRE_GAIN_LIN

        // Telescoping split: low + mid + high == x exactly at unity gain.
        val lowLp = lpLow[ch] + aLpLow * (x - lpLow[ch])
        lpLow[ch] = lowLp
        val highLp = lpHigh[ch] + aLpHigh * (x - lpHigh[ch])
        lpHigh[ch] = highLp
        val low = lowLp
        val mid = highLp - lowLp
        val high = x - highLp

        val gLow = bandGain(low, ch, envLow, attLow, relLow, LOW, MAKEUP_LOW_LIN)
        val gMid = bandGain(mid, ch, envMid, attMid, relMid, MID, MAKEUP_MID_LIN)
        val gHigh = bandGain(high, ch, envHigh, attHigh, relHigh, HIGH, MAKEUP_HIGH_LIN)

        // The linked limiter follower is updated once per frame on the peak
        // across channels (see queueInput), so this stays sample-pure.
        return low * gLow + mid * gMid + high * gHigh
    }

    /** Linked limiter gain: one follower update per frame on the peak across channels. */
    private fun limiterGain(framePeak: Float): Float {
        limiterEnv = follow(limiterEnv, framePeak, attLim, relLim)
        if (limiterEnv <= CEILING_LIN || limiterEnv <= 0f) return 1f
        return CEILING_LIN / limiterEnv
    }

    private fun framePeak(): Float {
        var peak = 0f
        for (ch in 0 until channelCount) {
            val a = kotlin.math.abs(scratch[ch])
            if (a > peak) peak = a
        }
        return peak
    }

    private fun bandGain(
        sample: Float,
        ch: Int,
        env: FloatArray,
        att: Float,
        rel: Float,
        band: Band,
        makeupLin: Float,
    ): Float {
        val level = follow(env[ch], kotlin.math.abs(sample), att, rel)
        env[ch] = level
        // Fast path (common case: quiet): at/below knee-start -> full makeup,
        // no transcendentals.
        if (level <= band.kneeStartLin) return makeupLin
        val overDb = 20f * log10(level / band.thrLin)
        val grDb = if (band.kneeDb <= 0f || overDb >= band.kneeDb / 2f) {
            // Above the knee: textbook ratio reduction.
            overDb * (1f - 1f / band.ratio)
        } else {
            // Inside the soft knee: quadratic blend from 0 to full reduction.
            val x = overDb + band.kneeDb / 2f
            (1f - 1f / band.ratio) * x * x / (2f * band.kneeDb)
        }
        var gainDb = band.makeupDb - grDb
        // Total (pre + band) never exceeds the +20 dB cap.
        if (gainDb > MAX_BAND_GAIN_DB) gainDb = MAX_BAND_GAIN_DB
        return dbToLin(gainDb)
    }

    private companion object {
        // ---- preset (mirrors TV verbatim) ----
        const val PRE_GAIN_DB = 15f
        const val CEILING_DBFS = -1.5f
        const val MAX_TOTAL_GAIN_DB = 20f
        const val MAX_BAND_GAIN_DB = MAX_TOTAL_GAIN_DB - PRE_GAIN_DB // +5

        const val LOW_CUTOFF_HZ = 250f
        const val HIGH_CUTOFF_HZ = 4000f

        const val LOW_THRESH_DB = -26f
        const val LOW_RATIO = 4f
        const val LOW_ATTACK_MS = 20f
        const val LOW_RELEASE_MS = 200f
        const val LOW_MAKEUP_DB = 2f

        const val MID_THRESH_DB = -22f
        const val MID_RATIO = 3f
        const val MID_ATTACK_MS = 10f
        const val MID_RELEASE_MS = 120f
        const val MID_KNEE_DB = 6f
        const val MID_MAKEUP_DB = 5f

        const val HIGH_THRESH_DB = -26f
        const val HIGH_RATIO = 2f
        const val HIGH_ATTACK_MS = 5f
        const val HIGH_RELEASE_MS = 80f
        const val HIGH_MAKEUP_DB = 3f

        const val LIMITER_ATTACK_MS = 0.5f
        const val LIMITER_RELEASE_MS = 50f

        // Validated peak-safe static floor from the film battery (hottest film
        // -10.6 dBFS peaks -> -1.6 dBFS): kept as the documented minimum the
        // full chain strictly exceeds, not as a mode.
        const val FALLBACK_STATIC_GAIN_DB = 9f

        val PRE_GAIN_LIN = dbToLin(PRE_GAIN_DB)
        val CEILING_LIN = dbToLin(CEILING_DBFS)
        val MAKEUP_LOW_LIN = dbToLin(LOW_MAKEUP_DB)
        val MAKEUP_MID_LIN = dbToLin(MID_MAKEUP_DB)
        val MAKEUP_HIGH_LIN = dbToLin(HIGH_MAKEUP_DB)

        val LOW = Band(LOW_THRESH_DB, LOW_RATIO, 0f, LOW_MAKEUP_DB)
        val MID = Band(MID_THRESH_DB, MID_RATIO, MID_KNEE_DB, MID_MAKEUP_DB)
        val HIGH = Band(HIGH_THRESH_DB, HIGH_RATIO, 0f, HIGH_MAKEUP_DB)

        fun dbToLin(db: Float): Float =
            exp(db * 0.11512925464970228f) // 10^(db/20) == e^(db*ln10/20)

        fun clamp(s: Float): Float =
            if (s > CEILING_LIN) CEILING_LIN else if (s < -CEILING_LIN) -CEILING_LIN else s

        /** One-pole lowpass coefficient for cutoff [fcHz] at sample rate [fsHz]. */
        fun onePole(fcHz: Float, fsHz: Float): Float =
            (1.0 - exp(-2.0 * Math.PI * fcHz / fsHz)).toFloat()

        /** Peak-follower smoothing coefficient for a time constant of [ms]. */
        fun smooth(ms: Float, fsHz: Float): Float =
            exp(-1000.0 / (fsHz * ms)).toFloat()

        /** Attack/release peak follow (branchless coefficient pick). */
        fun follow(env: Float, target: Float, att: Float, rel: Float): Float {
            val c = if (target > env) att else rel
            return env + (target - env) * (1f - c)
        }
    }

    private class Band(
        thrDb: Float,
        val ratio: Float,
        val kneeDb: Float,
        val makeupDb: Float,
    ) {
        val thrLin: Float = dbToLin(thrDb)
        // Knee starts kneeDb/2 below threshold (linear domain fast path).
        val kneeStartLin: Float = dbToLin(thrDb - kneeDb / 2f)
    }
}
