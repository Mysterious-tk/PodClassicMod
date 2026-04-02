package com.example.podclassic.media

import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer

class TomSteadyProcessor(audioSessionId: Int) {
    private val tomSteadyAGC = TomSteadyAGC()
    private var tubeAmpProcessor: TubeAmpProcessor? = null
    private var dcPhaseProcessor: DCPhaseLinearizerProcessor? = null
    private var audioBufferSize = 0
    var tubeAmpEnabled: Boolean = false
        set(value) {
            field = value
            // 初始化胆机处理器（首次启用时）
            if (value && tubeAmpProcessor == null) {
                tubeAmpProcessor = TubeAmpProcessor()
            }
            tubeAmpProcessor?.enabled = value
        }

    var dcPhaseEnabled: Boolean = false
        set(value) {
            field = value
            // 初始化 DC Phase Linearizer（首次启用时）
            if (value && dcPhaseProcessor == null) {
                dcPhaseProcessor = DCPhaseLinearizerProcessor()
            }
            dcPhaseProcessor?.enabled = value
        }

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
        var processed = if (tomSteadyAGC.enabled) {
            tomSteadyAGC.processAudio(buffer, size)
        } else {
            buffer
        }

        // 应用胆机音效
        if (tubeAmpEnabled && tubeAmpProcessor != null) {
            processed = tubeAmpProcessor!!.processAudio(processed, size)
        }

        // 应用 DC Phase Linearizer
        if (dcPhaseEnabled && dcPhaseProcessor != null) {
            processed = dcPhaseProcessor!!.processAudio(processed, size)
        }

        return processed
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
            val sampleRate = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100)
            } else {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            }

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
        tubeAmpProcessor?.reset()
        dcPhaseProcessor?.reset()
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

    /**
     * 获取TubeAmpProcessor实例
     */
    fun getTubeAmpProcessor(): TubeAmpProcessor? {
        return tubeAmpProcessor
    }

    /**
     * 设置胆机音效参数
     */
    fun setTubeAmpParameters(
        gain: Float? = null,
        saturation: Float? = null,
        harmonics: Float? = null,
        ratio: Float? = null,
        attack: Float? = null,
        release: Float? = null,
        warmth: Float? = null
    ) {
        // 确保胆机处理器已初始化
        if (tubeAmpProcessor == null) {
            tubeAmpProcessor = TubeAmpProcessor()
        }
        tubeAmpProcessor?.let {
            gain?.let { v -> it.tubeGain = v }
            saturation?.let { v -> it.saturationAmount = v }
            harmonics?.let { v -> it.harmonicContent = v }
            ratio?.let { v -> it.compressionRatio = v }
            attack?.let { v -> it.attackTimeMs = v }
            release?.let { v -> it.releaseTimeMs = v }
            warmth?.let { v -> it.updateWarmth(v) }
        }
    }

    /**
     * 应用胆机预设
     */
    fun applyTubeAmpPreset(preset: TubeAmpPreset) {
        // 确保胆机处理器已初始化
        if (tubeAmpProcessor == null) {
            tubeAmpProcessor = TubeAmpProcessor()
        }
        tubeAmpProcessor?.applyPreset(preset)
        // 更新启用状态
        tubeAmpEnabled = preset != TubeAmpPreset.NONE
    }

    /**
     * 获取DCPhaseLinearizerProcessor实例
     */
    fun getDCPhaseProcessor(): DCPhaseLinearizerProcessor? {
        return dcPhaseProcessor
    }

    /**
     * 设置DC Phase Linearizer参数
     */
    fun setDCPhaseParameters(
        strength: Float? = null,
        lowDelay: Float? = null,
        midDelay: Float? = null,
        highDelay: Float? = null,
        crossover: Float? = null,
        highCrossover: Float? = null
    ) {
        // 确保DC Phase处理器已初始化
        if (dcPhaseProcessor == null) {
            dcPhaseProcessor = DCPhaseLinearizerProcessor()
        }
        dcPhaseProcessor?.let {
            strength?.let { v -> it.correctionStrength = v }
            lowDelay?.let { v -> it.lowFreqDelay = v }
            midDelay?.let { v -> it.midFreqDelay = v }
            highDelay?.let { v -> it.highFreqDelay = v }
            crossover?.let { v -> it.crossoverFreq = v }
            highCrossover?.let { v -> it.highCrossoverFreq = v }
        }
    }

    /**
     * 应用DC Phase Linearizer预设
     */
    fun applyDCPhasePreset(preset: DCPhasePreset) {
        // 确保DC Phase处理器已初始化
        if (dcPhaseProcessor == null) {
            dcPhaseProcessor = DCPhaseLinearizerProcessor()
        }
        dcPhaseProcessor?.applyPreset(preset)
        // 更新启用状态
        dcPhaseEnabled = preset != DCPhasePreset.NONE
    }
}
