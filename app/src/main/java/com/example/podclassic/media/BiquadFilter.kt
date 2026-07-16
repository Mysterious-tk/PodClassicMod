package com.example.podclassic.media

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Small, allocation-free RBJ biquad used by the app-owned tone controls. */
class BiquadFilter {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var z1 = FloatArray(2)
    private var z2 = FloatArray(2)

    fun configureChannels(channelCount: Int) {
        z1 = FloatArray(channelCount.coerceAtLeast(1))
        z2 = FloatArray(channelCount.coerceAtLeast(1))
    }

    fun setPeaking(sampleRate: Float, frequency: Float, q: Float, gainDb: Float) {
        val a = 10.0.pow(gainDb / 40.0).toFloat()
        val safeFrequency = frequency.coerceIn(10f, sampleRate * 0.45f)
        val omega = (2.0 * PI * safeFrequency / sampleRate).toFloat()
        val alpha = sin(omega) / (2f * q.coerceAtLeast(0.1f))
        setCoefficients(
            1f + alpha * a,
            -2f * cos(omega),
            1f - alpha * a,
            1f + alpha / a,
            -2f * cos(omega),
            1f - alpha / a
        )
    }

    fun setLowShelf(sampleRate: Float, frequency: Float, gainDb: Float, slope: Float = 0.8f) {
        val a = 10.0.pow(gainDb / 40.0).toFloat()
        val safeFrequency = frequency.coerceIn(10f, sampleRate * 0.45f)
        val omega = (2.0 * PI * safeFrequency / sampleRate).toFloat()
        val c = cos(omega)
        val s = sin(omega)
        val alpha = s / 2f * sqrt(((a + 1f / a) * (1f / slope.coerceAtLeast(0.1f) - 1f) + 2f))
        val beta = 2f * sqrt(a) * alpha
        setCoefficients(
            a * ((a + 1f) - (a - 1f) * c + beta),
            2f * a * ((a - 1f) - (a + 1f) * c),
            a * ((a + 1f) - (a - 1f) * c - beta),
            (a + 1f) + (a - 1f) * c + beta,
            -2f * ((a - 1f) + (a + 1f) * c),
            (a + 1f) + (a - 1f) * c - beta
        )
    }

    fun process(sample: Float, channel: Int): Float {
        val output = b0 * sample + z1[channel]
        z1[channel] = b1 * sample - a1 * output + z2[channel]
        z2[channel] = b2 * sample - a2 * output
        return output
    }

    fun reset() {
        z1.fill(0f)
        z2.fill(0f)
    }

    private fun setCoefficients(nb0: Float, nb1: Float, nb2: Float, na0: Float, na1: Float, na2: Float) {
        val inverseA0 = 1f / na0
        b0 = nb0 * inverseA0
        b1 = nb1 * inverseA0
        b2 = nb2 * inverseA0
        a1 = na1 * inverseA0
        a2 = na2 * inverseA0
    }
}
