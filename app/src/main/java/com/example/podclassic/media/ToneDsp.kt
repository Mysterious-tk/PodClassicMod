package com.example.podclassic.media

import kotlin.math.pow

enum class EqualizerPreset(val title: String, val gains: FloatArray) {
    BALANCED("均衡", floatArrayOf(1f, 0f, 0f, 0.5f, 1f)),
    VOCAL("人声", floatArrayOf(-1f, -0.5f, 1.5f, 2f, 0.5f)),
    ROCK("摇滚", floatArrayOf(2f, 0.5f, -0.5f, 1.5f, 2f)),
    JAZZ("爵士", floatArrayOf(1.5f, 0.5f, 0f, 1f, 1.5f)),
    CLASSICAL("古典", floatArrayOf(0.5f, 0f, -0.5f, 1f, 1.5f));
}

class EqualizerDsp {
    data class Parameters(
        val enabled: Boolean = false,
        val preset: EqualizerPreset = EqualizerPreset.BALANCED,
        val strength: Float = 1f
    )

    @Volatile var parameters = Parameters()
    private var sampleRate = 44_100f
    private var channels = 2
    private val filters = Array(FREQUENCIES.size) { BiquadFilter() }
    private var applied: Parameters? = null
    private var preamp = 1f

    fun configure(sampleRate: Int, channelCount: Int) {
        this.sampleRate = sampleRate.coerceAtLeast(8_000).toFloat()
        channels = channelCount.coerceAtLeast(1)
        filters.forEach { it.configureChannels(channels) }
        applied = null
        reset()
    }

    fun processFrame(frame: FloatArray) {
        val current = parameters
        if (!current.enabled) return
        updateCoefficients(current)
        for (channel in 0 until minOf(frame.size, channels)) {
            var value = frame[channel] * preamp
            filters.forEach { value = it.process(value, channel) }
            frame[channel] = value
        }
    }

    fun reset() = filters.forEach { it.reset() }

    private fun updateCoefficients(current: Parameters) {
        if (current == applied) return
        val strength = current.strength.coerceIn(0.25f, 1f)
        val gains = current.preset.gains
        for (index in filters.indices) {
            filters[index].setPeaking(sampleRate, FREQUENCIES[index], 0.85f, gains[index] * strength)
        }
        val maximumBoost = gains.maxOrNull()?.coerceAtLeast(0f)?.times(strength) ?: 0f
        preamp = dbToLinear(-maximumBoost * 0.7f)
        applied = current
    }

    private fun dbToLinear(db: Float) = 10.0.pow(db / 20.0).toFloat()

    companion object {
        private val FREQUENCIES = floatArrayOf(80f, 250f, 1_000f, 4_000f, 12_000f)
    }
}

class ClearBassDsp {
    data class Parameters(val enabled: Boolean = false, val level: Int = 2)

    @Volatile var parameters = Parameters()
    private val shelf = BiquadFilter()
    private var sampleRate = 44_100f
    private var channels = 2
    private var applied: Parameters? = null
    private var preamp = 1f

    fun configure(sampleRate: Int, channelCount: Int) {
        this.sampleRate = sampleRate.coerceAtLeast(8_000).toFloat()
        channels = channelCount.coerceAtLeast(1)
        shelf.configureChannels(channels)
        applied = null
        reset()
    }

    fun processFrame(frame: FloatArray) {
        val current = parameters
        if (!current.enabled) return
        if (current != applied) {
            val gainDb = current.level.coerceIn(1, 5) * 1.25f
            shelf.setLowShelf(sampleRate, 95f, gainDb)
            preamp = 10.0.pow((-gainDb * 0.45f / 20f).toDouble()).toFloat()
            applied = current
        }
        for (channel in 0 until minOf(frame.size, channels)) {
            frame[channel] = shelf.process(frame[channel] * preamp, channel)
        }
    }

    fun reset() = shelf.reset()
}
