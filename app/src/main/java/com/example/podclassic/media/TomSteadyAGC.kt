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
    private var peakLevel: Float = 0.0f // 峰值电平
    private var isInitialPhase: Boolean = true // 是否处于初始阶段
    private var initialPhaseCounter: Int = 0 // 初始阶段计数器
    private val INITIAL_PHASE_SAMPLES: Int = 661500 // 初始阶段样本数 (约15秒)
    
    // 计算攻击和释放系数
    private val attackCoeff: Float
        get() = calculateCoeff(attackTime)
    
    private val releaseCoeff: Float
        get() = calculateCoeff(releaseTime)
    
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
            // 计算当前样本的绝对值
            val sample = buffer[i].toFloat()
            val absSample = kotlin.math.abs(sample)
            
            // 更新初始阶段状态
            if (isInitialPhase) {
                initialPhaseCounter++
                if (initialPhaseCounter >= INITIAL_PHASE_SAMPLES) {
                    isInitialPhase = false
                    // 初始阶段结束，重置状态重新计算，避免开头脏数据影响后续
                    peakLevel = 0.0f
                    currentGain = 0.0f
                }
            }
            
            // 更新峰值电平
            updatePeakLevel(absSample)
            
            // 计算目标增益
            val targetGain = calculateTargetGain()
            
            // 平滑调整增益
            // 对于初始阶段或突然大声，使用更保守的增益调整
            val isLoud = absSample > maxSample * 0.5f // 检测大声
            val adjustedAttackTime = if (isInitialPhase || isLoud) 5.0f else attackTime
            val adjustedAttackCoeff = calculateCoeff(adjustedAttackTime)
            
            val coeff = if (targetGain > currentGain) {
                adjustedAttackCoeff // 更快的攻击速度
            } else {
                releaseCoeff
            }
            currentGain += (targetGain - currentGain) * coeff
            
            // 应用增益
            val gainFactor = dbToLinear(currentGain)
            var processedSample = (sample * gainFactor)
            
            // 初始阶段硬性限幅：将输出限制在 targetLevel 以内
            if (isInitialPhase) {
                val normalizedOutput = processedSample / maxSample
                if (kotlin.math.abs(normalizedOutput) > targetLevel) {
                    processedSample = if (normalizedOutput > 0) {
                        targetLevel * maxSample
                    } else {
                        -targetLevel * maxSample
                    }
                }
            }
            
            // 限制输出范围
            outputBuffer[i] = kotlin.math.max(minShort, 
                kotlin.math.min(maxShort, processedSample.toInt())).toShort()
        }
        
        return outputBuffer
    }
    
    /**
     * 更新峰值电平
     */
    private fun updatePeakLevel(sample: Float) {
        val maxSample = Short.MAX_VALUE.toFloat()
        val normalizedSample = sample / maxSample
        
        // 使用峰值检测器
        if (normalizedSample > peakLevel) {
            peakLevel = normalizedSample
        } else {
            // 峰值衰减
            peakLevel *= 0.99f
            if (peakLevel < 0.001f) {
                peakLevel = 0.0f
            }
        }
    }
    
    /**
     * 计算目标增益
     */
    private fun calculateTargetGain(): Float {
        if (peakLevel < 0.001f) {
            // 对于极低的峰值电平，使用较小的正增益
            return 0.0f // 零增益，确保开头不会过大但也不会过小
        }
        
        // 计算所需增益
        val desiredGain = 20.0f * kotlin.math.log10(targetLevel / peakLevel)
        
        // 限制增益范围
        var limitedGain = kotlin.math.max(minGain, kotlin.math.min(maxGain, desiredGain))
        
        // 对于初始阶段，使用适度的增益限制
        if (isInitialPhase) {
            // 初始阶段使用适度的增益限制，不再过度降低声音
            limitedGain = kotlin.math.max(minGain, kotlin.math.min(0.0f, limitedGain))
        } else {
            // 非初始阶段，使用更合理的增益限制
            limitedGain = kotlin.math.max(minGain, kotlin.math.min(15.0f, limitedGain))
        }
        
        // 对于突然大声，进一步限制增益
        val isLoud = peakLevel > 0.85f // 检测大声
        if (isLoud) {
            // 大声时使用负增益或更小的增益
            limitedGain = kotlin.math.max(minGain, kotlin.math.min(2.0f, limitedGain))
        }
        
        return limitedGain
    }
    
    /**
     * 平滑更新当前增益
     */
    private fun updateCurrentGain(targetGain: Float) {
        val coeff = if (targetGain > currentGain) attackCoeff else releaseCoeff
        currentGain += (targetGain - currentGain) * coeff
    }
    
    /**
     * 计算时间常数对应的系数
     */
    private fun calculateCoeff(timeMs: Float): Float {
        // 假设采样率为44100Hz
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
        peakLevel = 0.0f
        isInitialPhase = true
        initialPhaseCounter = 0
    }
    
    /**
     * 获取当前增益值
     */
    fun getCurrentGain(): Float {
        return currentGain
    }
    
    /**
     * 获取当前峰值电平
     */
    fun getCurrentPeakLevel(): Float {
        return peakLevel
    }
}
