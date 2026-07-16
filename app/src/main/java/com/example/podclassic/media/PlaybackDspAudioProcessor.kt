@file:androidx.annotation.OptIn(
    markerClass = [androidx.media3.common.util.UnstableApi::class]
)

package com.example.podclassic.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Media3 bridge that guarantees decoded 16-bit PCM passes through the app DSP chain. */
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
        val output = replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.nativeOrder())
        inputBuffer.order(ByteOrder.nativeOrder())
        val frame = FloatArray(channelCount)
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        while (inputBuffer.remaining() >= channelCount * bytesPerSample) {
            for (channel in 0 until channelCount) {
                frame[channel] = if (encoding == C.ENCODING_PCM_FLOAT) inputBuffer.float
                else inputBuffer.short / 32768f
            }
            val active = hasActiveEffects()
            normalizer.processFrame(frame)
            nightVolume.processFrame(frame)
            equalizer.processFrame(frame)
            clearBass.processFrame(frame)
            tube.processFrame(frame)
            crossfeed.processFrame(frame)
            if (active) limiter.processFrame(frame)
            for (sample in frame) {
                if (encoding == C.ENCODING_PCM_FLOAT) {
                    output.putFloat(if (active) sample.coerceIn(-1f, 1f) else sample)
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

    private fun hasActiveEffects(): Boolean = normalizer.parameters.enabled ||
        nightVolume.parameters.enabled || equalizer.parameters.enabled ||
        clearBass.parameters.enabled || tube.parameters.enabled || crossfeed.parameters.enabled
}
