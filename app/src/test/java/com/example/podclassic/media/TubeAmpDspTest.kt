package com.example.podclassic.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class TubeAmpDspTest {
    @Test
    fun disabledProcessorIsBitTransparent() {
        val dsp = TubeAmpDsp().apply { configure(48_000, 2) }
        val frame = floatArrayOf(-0.75f, 0.42f)

        assertArrayEquals(floatArrayOf(-0.75f, 0.42f), dsp.processFrame(frame), 0f)
    }

    @Test
    fun enabledProcessorIsStereoLinkedAndNeverExceedsCeiling() {
        val dsp = TubeAmpDsp().apply {
            configure(96_000, 2)
            parameters = TubeAmpDsp.Parameters(
                enabled = true,
                drive = 1.7f,
                saturation = 0.8f,
                harmonics = 0.6f,
                warmth = 0.8f
            )
        }

        repeat(20_000) { index ->
            val sample = (1.15 * sin(2.0 * PI * 997.0 * index / 96_000.0)).toFloat()
            val output = dsp.processFrame(floatArrayOf(sample, sample))
            assertEquals(output[0], output[1], 1e-6f)
            assertTrue(abs(output[0]) <= TubeAmpDsp.OUTPUT_CEILING + 1e-6f)
        }
    }

    @Test
    fun asymmetricStageAddsSecondHarmonicWithoutDestroyingTreble() {
        val sampleRate = 48_000
        val frequency = 1_000.0
        val sampleCount = 9_600
        val discard = 2_400
        val dsp = TubeAmpDsp().apply {
            configure(sampleRate, 1)
            parameters = TubeAmpDsp.Parameters(
                enabled = true,
                drive = 1.45f,
                saturation = 0.5f,
                harmonics = 0.5f,
                warmth = 0.6f
            )
        }
        val output = FloatArray(sampleCount)
        for (index in output.indices) {
            val input = (0.45 * sin(2.0 * PI * frequency * index / sampleRate)).toFloat()
            output[index] = dsp.processFrame(floatArrayOf(input))[0]
        }

        val secondHarmonic = spectralAmplitude(output, 2.0 * frequency, sampleRate, discard)
        val fundamental = spectralAmplitude(output, frequency, sampleRate, discard)
        assertTrue("expected measurable even harmonic", secondHarmonic > 0.001f)
        assertTrue("tube colour should remain subtle", secondHarmonic < fundamental * 0.25f)

        // A 7 kHz signal must remain audible; the removed implementation low-passed it away.
        dsp.reset()
        var energy = 0.0
        repeat(sampleCount) { index ->
            val input = (0.35 * sin(2.0 * PI * 7_000.0 * index / sampleRate)).toFloat()
            val value = dsp.processFrame(floatArrayOf(input))[0]
            if (index >= discard) energy += value * value
        }
        val rms = sqrt(energy / (sampleCount - discard)).toFloat()
        assertTrue("warmth must not remove treble", rms > 0.12f)
    }

    private fun spectralAmplitude(
        samples: FloatArray,
        frequency: Double,
        sampleRate: Int,
        start: Int
    ): Float {
        var real = 0.0
        var imaginary = 0.0
        for (index in start until samples.size) {
            val phase = 2.0 * PI * frequency * index / sampleRate
            real += samples[index] * cos(phase)
            imaginary -= samples[index] * sin(phase)
        }
        return (2.0 * sqrt(real * real + imaginary * imaginary) / (samples.size - start)).toFloat()
    }
}
