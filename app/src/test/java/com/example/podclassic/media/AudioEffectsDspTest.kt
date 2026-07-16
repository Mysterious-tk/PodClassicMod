package com.example.podclassic.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class AudioEffectsDspTest {
    @Test
    fun disabledToneProcessorsAreTransparent() {
        val equalizer = EqualizerDsp().apply { configure(48_000, 2) }
        val bass = ClearBassDsp().apply { configure(48_000, 2) }
        val frame = floatArrayOf(-0.42f, 0.73f)
        equalizer.processFrame(frame)
        bass.processFrame(frame)
        assertArrayEquals(floatArrayOf(-0.42f, 0.73f), frame, 0f)
    }

    @Test
    fun clearBassFavorsBassWithoutUnboundedOutput() {
        val bassRms = processedRms(80.0)
        val trebleRms = processedRms(4_000.0)
        assertTrue("bass shelf should favor 80 Hz", bassRms > trebleRms * 1.35f)
        assertTrue(bassRms < 0.8f)
    }

    @Test
    fun crossfeedMovesLowFrequencyEnergyToSilentEar() {
        val dsp = CrossfeedDsp().apply {
            configure(48_000)
            parameters = CrossfeedDsp.Parameters(true, CrossfeedLevel.NATURAL)
        }
        var rightEnergy = 0.0
        repeat(4_800) { index ->
            val left = (0.5 * sin(2.0 * PI * 300.0 * index / 48_000.0)).toFloat()
            val frame = floatArrayOf(left, 0f)
            dsp.processFrame(frame)
            if (index > 500) rightEnergy += frame[1] * frame[1]
        }
        assertTrue("crossfeed must create a quiet opposite-ear signal", rightEnergy > 1.0)
    }

    @Test
    fun slowNormalizerRaisesQuietProgrammeButNeverRaisesSilence() {
        val dsp = VolumeNormalizerDsp().apply {
            configure(8_000)
            parameters = VolumeNormalizerDsp.Parameters(true, -16f, 6f)
        }
        var last = 0f
        repeat(8_000 * 15) { index ->
            val frame = floatArrayOf(
                (0.025 * sin(2.0 * PI * 440.0 * index / 8_000.0)).toFloat()
            )
            dsp.processFrame(frame)
            last = maxOf(last, kotlin.math.abs(frame[0]))
        }
        assertTrue("quiet programme should receive useful gain", last > 0.035f)

        dsp.reset()
        repeat(20_000) {
            val frame = floatArrayOf(0f, 0f)
            dsp.processFrame(frame)
            assertArrayEquals(floatArrayOf(0f, 0f), frame, 0f)
        }
    }

    @Test
    fun limiterIsStereoLinkedAndHoldsMinusOneDbCeiling() {
        val limiter = SafetyLimiterDsp().apply { configure(48_000) }
        repeat(20_000) {
            val frame = floatArrayOf(1.6f, 0.4f)
            limiter.processFrame(frame)
            assertTrue(kotlin.math.abs(frame[0]) <= 0.891251f)
            assertTrue(kotlin.math.abs(frame[1]) <= 0.891251f)
            assertTrue("both channels must use one gain", frame[0] / frame[1] > 3.99f)
        }
    }

    private fun processedRms(frequency: Double): Float {
        val bass = ClearBassDsp().apply {
            configure(48_000, 1)
            parameters = ClearBassDsp.Parameters(true, 4)
        }
        var energy = 0.0
        var count = 0
        repeat(24_000) { index ->
            val frame = floatArrayOf(
                (0.25 * sin(2.0 * PI * frequency * index / 48_000.0)).toFloat()
            )
            bass.processFrame(frame)
            if (index >= 4_000) {
                energy += frame[0] * frame[0]
                count++
            }
        }
        return sqrt(energy / count).toFloat()
    }
}
