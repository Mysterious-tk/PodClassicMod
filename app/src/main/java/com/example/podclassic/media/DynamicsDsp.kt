package com.example.podclassic.media

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/** Slow programme leveller. It changes overall level while leaving short musical dynamics intact. */
class VolumeNormalizerDsp {
    data class Parameters(
        val enabled: Boolean = false,
        val targetDb: Float = -16f,
        val maxBoostDb: Float = 6f
    )

    @Volatile var parameters = Parameters()
    private var sampleRate = 44_100f
    private var powerEnvelope = 0f
    private var gain = 1f

    fun configure(sampleRate: Int) {
        this.sampleRate = sampleRate.coerceAtLeast(8_000).toFloat()
        reset()
    }

    fun processFrame(frame: FloatArray) {
        val p = parameters
        if (!p.enabled) return
        var power = 0f
        frame.forEach { power += it * it }
        power /= frame.size.coerceAtLeast(1)
        val detector = coefficient(3_000f)
        powerEnvelope += (power - powerEnvelope) * detector
        val rms = sqrt(max(powerEnvelope, 0f))
        val target = dbToLinear(p.targetDb.coerceIn(-20f, -12f))
        val wanted = if (rms > dbToLinear(-50f)) target / rms else 1f
        val targetGain = wanted.coerceIn(dbToLinear(-6f), dbToLinear(p.maxBoostDb.coerceIn(0f, 9f)))
        val timeMs = if (targetGain < gain) 1_500f else 6_000f
        gain += (targetGain - gain) * coefficient(timeMs)
        frame.indices.forEach { frame[it] *= gain }
    }

    fun reset() {
        powerEnvelope = 0f
        gain = 1f
    }

    private fun coefficient(ms: Float) = 1f - exp(-1f / (ms / 1000f * sampleRate))
    private fun dbToLinear(db: Float) = 10.0.pow(db / 20.0).toFloat()
}

/** Faster leveller intended for quiet listening, speech and shuffle playback at night. */
class NightVolumeDsp {
    data class Parameters(
        val enabled: Boolean = false,
        val targetLevel: Float = 0.16f,
        val maxGainDb: Float = 6f,
        val attackMs: Float = 80f,
        val releaseMs: Float = 900f
    )

    @Volatile var parameters = Parameters()
    private var sampleRate = 44_100f
    private var levelEnvelope = 0f
    private var gain = 1f

    fun configure(sampleRate: Int) {
        this.sampleRate = sampleRate.coerceAtLeast(8_000).toFloat()
        reset()
    }

    fun processFrame(frame: FloatArray) {
        val p = parameters
        if (!p.enabled) return
        var power = 0f
        frame.forEach { power += it * it }
        val rms = sqrt(power / frame.size.coerceAtLeast(1))
        levelEnvelope += (rms - levelEnvelope) * coefficient(400f)
        val wanted = if (levelEnvelope > 0.003f) p.targetLevel.coerceIn(0.08f, 0.20f) / levelEnvelope else 1f
        val targetGain = wanted.coerceIn(dbToLinear(-9f), dbToLinear(p.maxGainDb.coerceIn(0f, 9f)))
        val time = if (targetGain < gain) p.attackMs else p.releaseMs
        gain += (targetGain - gain) * coefficient(time.coerceAtLeast(10f))
        frame.indices.forEach { frame[it] *= gain }
    }

    fun reset() {
        levelEnvelope = 0f
        gain = 1f
    }

    private fun coefficient(ms: Float) = 1f - exp(-1f / (ms / 1000f * sampleRate))
    private fun dbToLinear(db: Float): Float = Math.E.pow(db * ln(10.0) / 20.0).toFloat()
}

/** Stereo-linked safety limiter. It prevents DSP boosts from reaching the PCM hard clipper. */
class SafetyLimiterDsp {
    private var sampleRate = 44_100f
    private var gain = 1f

    fun configure(sampleRate: Int) {
        this.sampleRate = sampleRate.coerceAtLeast(8_000).toFloat()
        reset()
    }

    fun processFrame(frame: FloatArray) {
        var peak = 0f
        frame.forEach { peak = max(peak, kotlin.math.abs(it)) }
        val ceiling = 0.8912509f // -1 dBFS sample peak
        val wanted = if (peak > ceiling) ceiling / peak else 1f
        gain = if (wanted < gain) wanted else gain + (1f - gain) *
            (1f - exp(-1f / (0.12f * sampleRate)))
        frame.indices.forEach { frame[it] = (frame[it] * gain).coerceIn(-ceiling, ceiling) }
    }

    fun reset() { gain = 1f }
}
