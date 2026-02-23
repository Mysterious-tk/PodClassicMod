package com.example.podclassic.media

class TomSteadyAGC {
    // 算法参数
    var targetLevel: Float = 0.7f // 目标电平 (0.0-1.0)
    var maxGain: Float = 20.0f // 最大增益 (dB)
    var minGain: Float = -10.0f // 最小增益 (dB)
    var attackTime: Float = 50.0f // 攻击时间 (ms)
    var releaseTime: Float = 200.0f // 释放时间 (ms)
    var enabled: Boolean = true // 是否启用
    
    // 内部状态
    private var currentGain: Float = 0.0f // 当前增益 (dB)
    private var referenceLevel: Float = 0.0f // 参考电平（固定）
    private var hasReferenceLevel: Boolean = false // 是否已有参考电平
    
    // 预计算相关
    private val levelSamples: MutableList<Float> = mutableListOf() // 电平样本
    private var totalSamples: Long = 0 // 音乐总样本数
    private var preAnalysisCounter: Long = 0 // 预计算计数器
    private val SKIP_DURATION_MS: Int = 20000 // 跳过开头20秒
    private val NUM_SAMPLE_SEGMENTS: Int = 30 // 采集段数
    private val SAMPLE_DURATION_MS: Int = 100 // 每段采样时长100ms
    private var sampleInterval: Long = 0 // 采样间隔（根据总时长计算）
    private var currentSegment: Int = 0 // 当前采集段
    private var segmentSampleCounter: Int = 0 // 段内采样计数器
    
    // 计算攻击和释放系数
    private val attackCoeff: Float
        get() = calculateCoeff(attackTime)
    
    private val releaseCoeff: Float
        get() = calculateCoeff(releaseTime)
    
    /**
     * 设置音乐总时长（播放前调用）
     * @param durationMs 音乐总时长（毫秒）
     */
    fun setDuration(durationMs: Long) {
        // 计算总样本数（假设44.1kHz采样率）
        totalSamples = durationMs * 44100 / 1000
        // 计算采样间隔（跳过开头20秒后，均匀分布采集点）
        val skipSamples = SKIP_DURATION_MS * 44100L / 1000
        val availableSamples = totalSamples - skipSamples
        sampleInterval = if (availableSamples > 0) {
            availableSamples / NUM_SAMPLE_SEGMENTS
        } else {
            0
        }
    }
    
    /**
     * 处理音频数据
     * @param buffer 音频数据缓冲区
     * @param size 数据大小
     * @return 处理后的音频数据
     */
    fun processAudio(buffer: ShortArray, size: Int): ShortArray {
        if (!enabled || size == 0) {
            return buffer
        }
        
        val outputBuffer = ShortArray(size)
        val maxSample = Short.MAX_VALUE.toFloat()
        val minShort = Short.MIN_VALUE.toInt()
        val maxShort = Short.MAX_VALUE.toInt()
        
        for (i in 0 until size) {
            val sample = buffer[i].toFloat()
            
            // 计算目标增益（基于固定参考电平）
            val targetGain = calculateTargetGain()
            
            // 平滑调整增益
            val coeff = if (targetGain > currentGain) attackCoeff else releaseCoeff
            currentGain += (targetGain - currentGain) * coeff
            
            // 应用增益
            val gainFactor = dbToLinear(currentGain)
            var processedSample = (sample * gainFactor)
            
            // 限制输出范围
            outputBuffer[i] = kotlin.math.max(minShort, 
                kotlin.math.min(maxShort, processedSample.toInt())).toShort()
        }
        
        return outputBuffer
    }
    
    /**
     * 预分析音频数据（播放前调用）
     * @param buffer 音频数据缓冲区
     * @param size 数据大小
     * @return 是否完成预分析
     */
    fun preAnalyzeAudio(buffer: ShortArray, size: Int): Boolean {
        if (!enabled || size == 0 || totalSamples == 0L) {
            return false
        }
        
        val maxSample = Short.MAX_VALUE.toFloat()
        val skipSamples = SKIP_DURATION_MS * 44100L / 1000
        
        for (i in 0 until size) {
            preAnalysisCounter++
            
            // 跳过开头20秒
            if (preAnalysisCounter < skipSamples) {
                continue
            }
            
            // 检查是否到达下一个采样点
            val expectedSamplePos = skipSamples + currentSegment * sampleInterval
            
            if (preAnalysisCounter >= expectedSamplePos && currentSegment < NUM_SAMPLE_SEGMENTS) {
                segmentSampleCounter++
                
                // 在当前位置采样100ms
                if (segmentSampleCounter <= SAMPLE_DURATION_MS * 44100 / 1000) {
                    val sample = buffer[i].toFloat()
                    val absSample = kotlin.math.abs(sample) / maxSample
                    
                    // 避免无声部分
                    if (absSample > 0.01f) {
                        levelSamples.add(absSample)
                    }
                } else {
                    // 完成当前段，进入下一段
                    currentSegment++
                    segmentSampleCounter = 0
                }
            }
            
            // 完成所有段采集
            if (currentSegment >= NUM_SAMPLE_SEGMENTS) {
                referenceLevel = calculateReferenceLevel()
                hasReferenceLevel = true
                return true
            }
        }
        
        return hasReferenceLevel
    }
    
    /**
     * 设置参考电平（外部计算后设置）
     * @param level 参考电平
     */
    fun setReferenceLevel(level: Float) {
        referenceLevel = level
        hasReferenceLevel = true
    }
    
    /**
     * 计算参考电平
     */
    private fun calculateReferenceLevel(): Float {
        if (levelSamples.isEmpty()) {
            return 0.1f // 默认参考电平
        }
        
        // 排序样本
        val sortedSamples = levelSamples.sorted()
        
        // 移除最大声和最小声（各10%）
        val startIndex = (sortedSamples.size * 0.1f).toInt()
        val endIndex = (sortedSamples.size * 0.9f).toInt()
        
        if (startIndex >= endIndex || startIndex >= sortedSamples.size) {
            return sortedSamples.average().toFloat()
        }
        
        val filteredSamples = sortedSamples.subList(startIndex, endIndex)
        
        if (filteredSamples.isEmpty()) {
            return 0.1f // 默认参考电平
        }
        
        // 计算平均值
        return filteredSamples.average().toFloat()
    }
    
    /**
     * 计算目标增益
     */
    private fun calculateTargetGain(): Float {
        val levelToUse = if (hasReferenceLevel && referenceLevel > 0.0f) {
            referenceLevel
        } else {
            // 没有参考电平时使用默认电平
            0.1f
        }
        
        if (levelToUse < 0.001f) {
            return 0.0f
        }
        
        // 计算所需增益
        val desiredGain = 20.0f * kotlin.math.log10(targetLevel / levelToUse)
        
        // 限制增益范围
        return kotlin.math.max(minGain, kotlin.math.min(maxGain, desiredGain))
    }
    
    /**
     * 计算时间常数对应的系数
     */
    private fun calculateCoeff(timeMs: Float): Float {
        val sampleRate = 44100.0f
        val timeConstant = timeMs / 1000.0f
        return 1.0f - kotlin.math.exp(-1.0f / (timeConstant * sampleRate))
    }
    
    /**
     * 将分贝转换为线性增益
     */
    private fun dbToLinear(db: Float): Float {
        return Math.pow(10.0, db / 20.0).toFloat()
    }
    
    /**
     * 重置AGC状态
     */
    fun reset() {
        currentGain = 0.0f
        referenceLevel = 0.0f
        hasReferenceLevel = false
        levelSamples.clear()
        preAnalysisCounter = 0
        currentSegment = 0
        segmentSampleCounter = 0
        totalSamples = 0
        sampleInterval = 0
    }
    
    /**
     * 获取当前增益值
     */
    fun getCurrentGain(): Float {
        return currentGain
    }
    
    /**
     * 获取参考电平
     */
    fun getReferenceLevel(): Float {
        return referenceLevel
    }
    
    /**
     * 检查是否已有参考电平
     */
    fun hasReferenceLevel(): Boolean {
        return hasReferenceLevel
    }
    
    /**
     * 检查预分析是否完成
     */
    fun isPreAnalysisComplete(): Boolean {
        return hasReferenceLevel
    }
}
