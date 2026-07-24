package com.example.podclassic.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PlaybackDspAudioProcessorTest {
    private val stereoPcm16 = AudioProcessor.AudioFormat(
        48_000,
        2,
        C.ENCODING_PCM_16BIT
    )

    @Test
    fun allEffectsDisabledMakesProcessorInactive() {
        val processor = PlaybackDspAudioProcessor()

        processor.configure(stereoPcm16)
        processor.flush()

        assertFalse(processor.hasActiveEffects())
        assertFalse(processor.isActive)
    }

    @Test
    fun disabledDefensiveQueueIsBitTransparent() {
        val processor = PlaybackDspAudioProcessor()
        val bytes = byteArrayOf(
            0x00, 0x80.toByte(),
            0x01, 0x00,
            0xff.toByte(), 0x7f,
            0x37, 0x13
        )
        val input = ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .put(bytes)
            .flip() as ByteBuffer

        processor.configure(stereoPcm16)
        processor.flush()
        processor.queueInput(input)
        val output = processor.output
        val actual = ByteArray(output.remaining())
        output.get(actual)

        assertArrayEquals(bytes, actual)
    }

    @Test
    fun enablingAnEffectActivatesProcessor() {
        val processor = PlaybackDspAudioProcessor()
        processor.equalizer.parameters = processor.equalizer.parameters.copy(enabled = true)

        processor.configure(stereoPcm16)
        processor.flush()

        assertTrue(processor.hasActiveEffects())
        assertTrue(processor.isActive)
    }
}
