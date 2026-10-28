package com.idlix.mobile.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Software-path tests for [LoudnessNormalizeProcessor].
 *
 * Pure JVM: exercises [AudioProcessor.configure]/`queueInput`/`getOutput`
 * with explicit little-endian buffers (the production pipeline hands the
 * processor native-order buffers; the JVM default is big-endian, so tests
 * must set the order). No player, no Android framework.
 */
class LoudnessNormalizeProcessorTest {

    private val rate = 48000
    private val channels = 2
    private val format16 = AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_16BIT)
    private val formatFloat = AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_FLOAT)

    /** Stereo sine at [freqHz]/[peak] for [seconds], interleaved. */
    private fun sine16(freqHz: Double, peak: Float, seconds: Double): ByteBuffer {
        val frames = (rate * seconds).toInt()
        val buf = ByteBuffer.allocateDirect(frames * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { i ->
            val s = (sin(2.0 * PI * freqHz * i / rate) * peak * 32767f).toInt().toShort()
            repeat(channels) { buf.putShort(s) }
        }
        buf.flip()
        return buf
    }

    private fun sineFloat(freqHz: Double, peak: Float, seconds: Double): ByteBuffer {
        val frames = (rate * seconds).toInt()
        val buf = ByteBuffer.allocateDirect(frames * channels * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { i ->
            val s = (sin(2.0 * PI * freqHz * i / rate) * peak).toFloat()
            repeat(channels) { buf.putFloat(s) }
        }
        buf.flip()
        return buf
    }

    /** Feeds [input] in one shot, returns the peak |sample| of the float output. */
    private fun outputPeakFloat(p: LoudnessNormalizeProcessor, input: ByteBuffer): Float {
        p.queueInput(input)
        val out = p.getOutput().order(ByteOrder.LITTLE_ENDIAN)
        var peak = 0f
        while (out.remaining() >= 4) {
            val a = kotlin.math.abs(out.float)
            if (a > peak) peak = a
        }
        return peak
    }

    private fun outputPeak16(p: LoudnessNormalizeProcessor, input: ByteBuffer): Float {
        p.queueInput(input)
        val out = p.getOutput().order(ByteOrder.LITTLE_ENDIAN)
        var peak = 0f
        while (out.remaining() >= 2) {
            val a = kotlin.math.abs(out.short / 32768f)
            if (a > peak) peak = a
        }
        return peak
    }

    @Test
    fun configureKeepsFormat() {
        val out = LoudnessNormalizeProcessor().configure(format16)
        assertEquals(rate, out.sampleRate)
        assertEquals(channels, out.channelCount)
        assertEquals(C.ENCODING_PCM_16BIT, out.encoding)
    }

    @Test
    fun quietDialogueGainsUpWithoutClipping() {
        // -30 dBFS 440 Hz (mid band): post-gain -15 dBFS straddles the mid
        // threshold, so the output must be much louder yet under the ceiling.
        val p = LoudnessNormalizeProcessor()
        p.configure(format16)
        p.flush()
        // Settle the followers first, then measure the settled second.
        outputPeak16(p, sine16(440.0, 0.0316f, 1.0))
        val peak = outputPeak16(p, sine16(440.0, 0.0316f, 1.0))
        assertTrue("expected strong makeup, got $peak", peak > 0.10f)
        assertTrue("must stay at/below -1.5 dBFS ceiling, got $peak", peak <= 0.8415f)
    }

    @Test
    fun fullScaleNeverExceedsCeiling() {
        // 0 dBFS sine straight into the limiter: every sample clamped.
        val p = LoudnessNormalizeProcessor()
        p.configure(format16)
        p.flush()
        val peak = outputPeak16(p, sine16(440.0, 1.0f, 0.5))
        assertTrue("limiter+clamp must hold the ceiling, got $peak", peak <= 0.8415f)
        assertTrue("limiter must not gate to silence, got $peak", peak > 0.5f)
    }

    @Test
    fun floatPathMatches16BitPath() {
        val pcm = LoudnessNormalizeProcessor().also { it.configure(format16); it.flush() }
        val flt = LoudnessNormalizeProcessor().also { it.configure(formatFloat); it.flush() }
        outputPeak16(pcm, sine16(440.0, 0.0316f, 1.0))
        outputPeakFloat(flt, sineFloat(440.0, 0.0316f, 1.0))
        val peakPcm = outputPeak16(pcm, sine16(440.0, 0.0316f, 0.5))
        val peakFlt = outputPeakFloat(flt, sineFloat(440.0, 0.0316f, 0.5))
        assertEquals(peakPcm, peakFlt, 0.01f)
    }

    @Test
    fun disabledIsBitExactPassthrough() {
        val p = LoudnessNormalizeProcessor()
        p.configure(format16)
        p.flush()
        p.setEnabled(false)
        val input = sine16(440.0, 0.5f, 0.1)
        val expected = ByteArray(input.remaining())
        input.mark()
        input.get(expected)
        input.reset()
        p.queueInput(input)
        val out = p.getOutput()
        val actual = ByteArray(out.remaining())
        out.get(actual)
        assertArrayEquals(expected, actual)
    }

    @Test
    fun unsupportedEncodingThrows() {
        val p = LoudnessNormalizeProcessor()
        try {
            p.configure(AudioProcessor.AudioFormat(rate, channels, C.ENCODING_AC3))
            fail("expected UnhandledAudioFormatException")
        } catch (_: AudioProcessor.UnhandledAudioFormatException) {
            // Expected: the sink drops this stage for non-PCM.
        }
    }
}
