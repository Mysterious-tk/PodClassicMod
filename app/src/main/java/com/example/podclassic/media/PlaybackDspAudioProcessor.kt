@file:androidx.annotation.OptIn(
    markerClass = [androidx.media3.common.util.UnstableApi::class]
)

package com.example.podclassic.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Media3 bridge for the app DSP chain.
 *
 * When every effect is disabled this processor reports itself as inactive. Media3 can then skip
 * PCM processing entirely (and, when supported by the device/format, use audio offload).
 */
class PlaybackDspAudioProcessor : BaseAudioProcessor() {
    val tube = TubeAmpDsp()
    val normalizer = VolumeNormalizerDsp()
    val nightVolume = NightVolumeDsp()
    val equalizer = EqualizerDsp()
    val clearBass = ClearBassDsp()
    val crossfeed = CrossfeedDsp()
    private val limiter = SafetyLimiterDsp()

    private var channelCount = 2
    private var encoding = C.ENCODING_PCM_16BIT

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!hasActiveEffects()) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        encoding = inputAudioFormat.encoding
        channelCount = inputAudioFormat.channelCount
        tube.configure(inputAudioFormat.sampleRate, channelCount)
        normalizer.configure(inputAudioFormat.sampleRate)
        nightVolume.configure(inputAudioFormat.sampleRate)
        equalizer.configure(inputAudioFormat.sampleRate, channelCount)
        clearBass.configure(inputAudioFormat.sampleRate, channelCount)
        crossfeed.configure(inputAudioFormat.sampleRate)
        limiter.configure(inputAudioFormat.sampleRate)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!hasActiveEffects()) {
            // Normally unreachable because an inactive BaseAudioProcessor is omitted from the
            // chain. Keep direct calls bit-transparent without doing a per-sample float roundtrip.
            val output = replaceOutputBuffer(inputBuffer.remaining())
            output.put(inputBuffer)
            output.flip()
            return
        }
        val output = replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.nativeOrder())
        inputBuffer.order(ByteOrder.nativeOrder())
        val frame = FloatArray(channelCount)
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        while (inputBuffer.remaining() >= channelCount * bytesPerSample) {
            for (channel in 0 until channelCount) {
                frame[channel] = if (encoding == C.ENCODING_PCM_FLOAT) inputBuffer.float
                else inputBuffer.short / 32768f
            }
            normalizer.processFrame(frame)
            nightVolume.processFrame(frame)
            equalizer.processFrame(frame)
            clearBass.processFrame(frame)
            tube.processFrame(frame)
            crossfeed.processFrame(frame)
            limiter.processFrame(frame)
            for (sample in frame) {
                if (encoding == C.ENCODING_PCM_FLOAT) {
                    output.putFloat(sample.coerceIn(-1f, 1f))
                }
                else output.putShort((sample.coerceIn(-1f, 0.9999695f) * 32768f).toInt().toShort())
            }
        }
        // A valid PCM buffer should contain whole frames; preserve any tail defensively.
        while (inputBuffer.hasRemaining()) output.put(inputBuffer.get())
        output.flip()
    }

    override fun onFlush() = resetState()
    override fun onReset() = resetState()

    private fun resetState() {
        normalizer.reset()
        nightVolume.reset()
        equalizer.reset()
        clearBass.reset()
        tube.reset()
        crossfeed.reset()
        limiter.reset()
    }

    internal fun hasActiveEffects(): Boolean = normalizer.parameters.enabled ||
        nightVolume.parameters.enabled || equalizer.parameters.enabled ||
        clearBass.parameters.enabled || tube.parameters.enabled || crossfeed.parameters.enabled
}
