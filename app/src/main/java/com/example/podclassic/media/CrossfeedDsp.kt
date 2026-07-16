package com.example.podclassic.media

import kotlin.math.PI
import kotlin.math.exp

enum class CrossfeedLevel(val title: String, val mix: Float) {
    LIGHT("轻微", 0.08f), NATURAL("自然", 0.14f), STRONG("明显", 0.22f)
}

/** Headphone crossfeed: a quiet, low-passed copy of each channel is fed to the opposite ear. */
class CrossfeedDsp {
    data class Parameters(
        val enabled: Boolean = false,
        val level: CrossfeedLevel = CrossfeedLevel.NATURAL
    )

    @Volatile var parameters = Parameters()
    private var alpha = 0.1f
    private var leftLow = 0f
    private var rightLow = 0f
    private var delayedLeft = 0f
    private var delayedRight = 0f

    fun configure(sampleRate: Int) {
        alpha = (1f - exp((-2.0 * PI * 700.0 / sampleRate.coerceAtLeast(8_000)).toFloat()))
        reset()
    }

    fun processFrame(frame: FloatArray) {
        val p = parameters
        if (!p.enabled || frame.size != 2) return
        val left = frame[0]
        val right = frame[1]
        leftLow += alpha * (right - leftLow)
        rightLow += alpha * (left - rightLow)
        val mix = p.level.mix
        val scale = 1f / (1f + mix * 0.6f)
        frame[0] = (left + delayedLeft * mix) * scale
        frame[1] = (right + delayedRight * mix) * scale
        delayedLeft = leftLow
        delayedRight = rightLow
    }

    fun reset() {
        leftLow = 0f
        rightLow = 0f
        delayedLeft = 0f
        delayedRight = 0f
    }
}
