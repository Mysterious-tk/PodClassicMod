@file:androidx.annotation.OptIn(
    markerClass = [androidx.media3.common.util.UnstableApi::class]
)

package com.example.podclassic.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/** Media3 bridge that guarantees decoded 16-bit PCM passes through the app DSP chain. */
class PlaybackDspAudioProcessor : BaseAudioProcessor() {
    val tube = TubeAmpDsp()

    @Volatile var loudnessEnabled = false
    @Volatile var targetLevel = 0.16f
    @Volatile var maxGainDb = 6f
    @Volatile var minGainDb = -6f
    @Volatile var attackMs = 80f
    @Volatile var releaseMs = 900f

    private var sampleRate = 44_100f
    private var channelCount = 2
    private var levelEnvelope = 0f
    private var loudnessGain = 1f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate.toFloat()
        channelCount = inputAudioFormat.channelCount
        tube.configure(inputAudioFormat.sampleRate, channelCount)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val output = replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.nativeOrder())
        inputBuffer.order(ByteOrder.nativeOrder())
        val frame = FloatArray(channelCount)
        while (inputBuffer.remaining() >= channelCount * 2) {
            for (channel in 0 until channelCount) {
                frame[channel] = inputBuffer.short / 32768f
            }
            applyLoudness(frame)
            tube.processFrame(frame)
            for (sample in frame) {
                output.putShort((sample.coerceIn(-1f, 0.9999695f) * 32768f).toInt().toShort())
            }
        }
        // A valid PCM buffer should contain whole frames; preserve any tail defensively.
        while (inputBuffer.hasRemaining()) output.put(inputBuffer.get())
        output.flip()
    }

    override fun onFlush() = resetState()
    override fun onReset() = resetState()

    private fun resetState() {
        levelEnvelope = 0f
        loudnessGain = 1f
        tube.reset()
    }

    private fun applyLoudness(frame: FloatArray) {
        if (!loudnessEnabled) return
        var power = 0f
        for (sample in frame) power += sample * sample
        val rms = sqrt(power / frame.size.coerceAtLeast(1))

        // 400 ms programme-level detector; silence never receives extra gain.
        val detectorCoeff = 1f - exp(-1f / (0.4f * sampleRate))
        levelEnvelope += (rms - levelEnvelope) * detectorCoeff
        val wanted = if (levelEnvelope > 0.003f) targetLevel.coerceIn(0.08f, 0.25f) / levelEnvelope else 1f
        val minimum = dbToLinear(minGainDb.coerceIn(-12f, 0f))
        val maximum = dbToLinear(maxGainDb.coerceIn(0f, 12f))
        val targetGain = wanted.coerceIn(minimum, maximum)
        val time = if (targetGain < loudnessGain) attackMs else releaseMs
        val gainCoeff = 1f - exp(-1f / (time.coerceAtLeast(10f) / 1000f * sampleRate))
        loudnessGain += (targetGain - loudnessGain) * gainCoeff

        for (i in frame.indices) frame[i] *= loudnessGain
    }

    private fun dbToLinear(db: Float): Float = Math.E.pow(db * ln(10.0) / 20.0).toFloat()
}
