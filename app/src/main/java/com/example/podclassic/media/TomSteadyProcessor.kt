package com.example.podclassic.media

import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer

class TomSteadyProcessor(audioSessionId: Int) {
    private val tomSteadyAGC = TomSteadyAGC()
    private var audioBufferSize = 0

    /**
     * 初始化处理器
     */
    fun init() {
        // 设置默认缓冲区大小
        audioBufferSize = 1024
    }

    /**
     * 设置音乐总时长（用于分段采样）
     * @param durationMs 音乐总时长（毫秒）
     */
    fun setDuration(durationMs: Long) {
        tomSteadyAGC.setDuration(durationMs)
    }

    /**
     * 处理音频数据
     * @param buffer 音频数据
     * @param size 数据大小
     */
    fun processAudio(buffer: ShortArray, size: Int): ShortArray {
        return if (tomSteadyAGC.enabled) {
            tomSteadyAGC.processAudio(buffer, size)
        } else {
            buffer
        }
    }

    /**
     * 预分析音频数据（播放前调用）
     * @param buffer 音频数据缓冲区
     * @param size 数据大小
     * @return 是否完成预分析
     */
    fun preAnalyzeAudio(buffer: ShortArray, size: Int): Boolean {
        return tomSteadyAGC.preAnalyzeAudio(buffer, size)
    }

    /**
     * 从文件路径预分析音频（后台线程调用）
     * @param filePath 音频文件路径
     * @param durationMs 音乐总时长（毫秒）
     * @return 是否成功完成预分析
     */
    fun preAnalyzeFromFile(filePath: String, durationMs: Long): Boolean {
        if (!tomSteadyAGC.enabled) {
            return false
        }

        // 设置时长
        tomSteadyAGC.setDuration(durationMs)

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(filePath)

            // 找到音频轨道
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    break
                }
            }

            if (audioTrackIndex < 0) {
                return false
            }

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)

            // 获取采样率
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100)

            // 读取音频数据
            val bufferSize = 1024 * 1024 // 1MB buffer
            val buffer = ByteBuffer.allocate(bufferSize)

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) {
                    break
                }

                // 将字节数据转换为 ShortArray（假设16位PCM）
                val shortArray = ShortArray(sampleSize / 2)
                buffer.rewind()
                for (i in shortArray.indices) {
                    shortArray[i] = buffer.short
                }

                // 预分析
                val isComplete = tomSteadyAGC.preAnalyzeAudio(shortArray, shortArray.size)
                if (isComplete) {
                    return true
                }

                extractor.advance()
            }

            return tomSteadyAGC.isPreAnalysisComplete()
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            extractor.release()
        }
    }

    /**
     * 重置处理器状态
     */
    fun reset() {
        tomSteadyAGC.reset()
    }

    /**
     * 释放资源
     */
    fun release() {
        reset()
    }

    /**
     * 获取TomSteadyAGC实例
     */
    fun getTomSteadyAGC(): TomSteadyAGC {
        return tomSteadyAGC
    }

    /**
     * 检查处理器是否可用
     */
    fun isAvailable(): Boolean {
        return audioBufferSize > 0
    }

    /**
     * 检查预分析是否完成
     */
    fun isPreAnalysisComplete(): Boolean {
        return tomSteadyAGC.isPreAnalysisComplete()
    }

    /**
     * 获取参考电平
     */
    fun getReferenceLevel(): Float {
        return tomSteadyAGC.getReferenceLevel()
    }

    /**
     * 设置TomSteady算法参数
     */
    fun setParameters(
        targetLevel: Float? = null,
        maxGain: Float? = null,
        minGain: Float? = null,
        attackTime: Float? = null,
        releaseTime: Float? = null,
        enabled: Boolean? = null
    ) {
        targetLevel?.let { tomSteadyAGC.targetLevel = it }
        maxGain?.let { tomSteadyAGC.maxGain = it }
        minGain?.let { tomSteadyAGC.minGain = it }
        attackTime?.let { tomSteadyAGC.attackTime = it }
        releaseTime?.let { tomSteadyAGC.releaseTime = it }
        enabled?.let { tomSteadyAGC.enabled = it }
    }
}
