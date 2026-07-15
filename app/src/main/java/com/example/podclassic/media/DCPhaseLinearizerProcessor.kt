package com.example.podclassic.media

import kotlin.math.*

/**
 * Sony DC Phase Linearizer (相位线性器) 音效处理器
 *
 * 模拟 Sony 的 DC Phase Linearizer 技术，用于校正音频信号中的相位失真：
 * - 相位校正：恢复录音和播放过程中失真的频率相位关系
 * - 群延迟均衡：确保不同频率的信号到达时间一致
 * - 时间对齐：改善声音的结像力和声场深度
 * - 透明处理：在改善音质的同时保持原始音色
 *
 * 核心算法使用全通滤波器 (All-Pass Filter) 实现相位校正
 */
class DCPhaseLinearizerProcessor {

    // DC Phase Linearizer 参数
    var enabled: Boolean = false
    var correctionStrength: Float = 0.5f      // 校正强度 (0.0 - 1.0)
    var lowFreqDelay: Float = 0.3f            // 低频延迟 (0.0 - 1.0)
    var midFreqDelay: Float = 0.2f            // 中频延迟 (0.0 - 1.0)
    var highFreqDelay: Float = 0.1f           // 高频延迟 (0.0 - 1.0)
    var crossoverFreq: Float = 500.0f         // 分频点 (Hz) - 低/中频分界
    var highCrossoverFreq: Float = 3000.0f    // 高频分频点 (Hz) - 中/高频分界

    // 采样率
    private val sampleRate: Float = 44100.0f

    // 内部状态 - 全通滤波器
    // 每个频段需要左右声道各一个滤波器
    private data class AllPassFilterState(
        var x1: Float = 0f,  // 上一个输入样本
        var y1: Float = 0f   // 上一个输出样本
    )

    private val lowFilterL = AllPassFilterState()
    private val lowFilterR = AllPassFilterState()
    private val midFilterL = AllPassFilterState()
    private val midFilterR = AllPassFilterState()
    private val highFilterL = AllPassFilterState()
    private val highFilterR = AllPassFilterState()

    // 分频滤波器状态 (Linkwitz-Riley 4th order)
    private data class CrossoverFilterState(
        var x1: Float = 0f, var x2: Float = 0f, var x3: Float = 0f, var x4: Float = 0f,
        var y1: Float = 0f, var y2: Float = 0f, var y3: Float = 0f, var y4: Float = 0f
    )

    private val lowCrossoverL = CrossoverFilterState()
    private val lowCrossoverR = CrossoverFilterState()
    private val midCrossoverLowL = CrossoverFilterState()
    private val midCrossoverLowR = CrossoverFilterState()
    private val midCrossoverHighL = CrossoverFilterState()
    private val midCrossoverHighR = CrossoverFilterState()
    private val highCrossoverL = CrossoverFilterState()
    private val highCrossoverR = CrossoverFilterState()

    /**
     * 处理立体声音频数据
     * @param buffer 音频数据缓冲区（交错立体声：L,R,L,R...）
     * @param size 数据大小（样本数）
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

        // 计算分频滤波器系数
        val lowCoef = calculateCrossoverCoefficients(crossoverFreq, true)   // 低通
        val midLowCoef = calculateCrossoverCoefficients(crossoverFreq, false) // 高通
        val midHighCoef = calculateCrossoverCoefficients(highCrossoverFreq, true) // 低通
        val highCoef = calculateCrossoverCoefficients(highCrossoverFreq, false)  // 高通

        var i = 0
        while (i < size) {
            // 获取立体声样本
            val sampleL = if (i < size) buffer[i].toFloat() / maxSample else 0f
            val sampleR = if (i + 1 < size) buffer[i + 1].toFloat() / maxSample else sampleL

            // 1. 分频处理 - 将信号分为低、中、高频
            val (lowL, midLowL, highMidL) = applyCrossover(sampleL, lowCrossoverL, lowCoef)
            val (midL, highL) = applyCrossover(midLowL, midCrossoverHighL, midHighCoef)
            val midFinalL = applyHighCrossover(midLowL, midCrossoverLowL, midLowCoef)

            val (lowR, midLowR, highMidR) = applyCrossover(sampleR, lowCrossoverR, lowCoef)
            val (midR, highR) = applyCrossover(midLowR, midCrossoverHighR, midHighCoef)
            val midFinalR = applyHighCrossover(midLowR, midCrossoverLowR, midLowCoef)

            // 2. 对每个频段应用相位校正 (全通滤波器)
            val phaseCorrectedLowL = applyAllPassFilter(lowL, lowFilterL, lowFreqDelay)
            val phaseCorrectedLowR = applyAllPassFilter(lowR, lowFilterR, lowFreqDelay)

            val phaseCorrectedMidL = applyAllPassFilter(midFinalL, midFilterL, midFreqDelay)
            val phaseCorrectedMidR = applyAllPassFilter(midFinalR, midFilterR, midFreqDelay)

            val phaseCorrectedHighL = applyAllPassFilter(highL, highFilterL, highFreqDelay)
            val phaseCorrectedHighR = applyAllPassFilter(highR, highFilterR, highFreqDelay)

            // 3. 根据校正强度混合原始信号和校正信号
            val dryWetMix = correctionStrength
            val outputL = sampleL * (1f - dryWetMix) +
                    (phaseCorrectedLowL + phaseCorrectedMidL + phaseCorrectedHighL) * dryWetMix
            val outputR = sampleR * (1f - dryWetMix) +
                    (phaseCorrectedLowR + phaseCorrectedMidR + phaseCorrectedHighR) * dryWetMix

            // 4. 限制输出范围并转换回Short
            outputBuffer[i] = max(minShort, min(maxShort, (outputL * maxSample).toInt())).toShort()
            if (i + 1 < size) {
                outputBuffer[i + 1] = max(minShort, min(maxShort, (outputR * maxSample).toInt())).toShort()
            }

            i += 2
        }

        return outputBuffer
    }

    /**
     * 一阶全通滤波器 - 用于相位校正
     * 传递函数: H(s) = (s - wc) / (s + wc)
     * 离散化: y[n] = -g * x[n] + x[n-1] + g * y[n-1]
     * 其中 g = (1 - tan(π*fc/fs)) / (1 + tan(π*fc/fs))
     */
    private fun applyAllPassFilter(
        input: Float,
        state: AllPassFilterState,
        delayAmount: Float
    ): Float {
        if (delayAmount < 0.01f) return input

        // 根据延迟量计算中心频率
        // 延迟越大，中心频率越低
        val centerFreq = 1000f * (1f - delayAmount * 0.9f)

        // 计算全通滤波器系数
        val omega = 2f * PI.toFloat() * centerFreq / sampleRate
        val tanOmega = tan(omega / 2f)
        val g = (1f - tanOmega) / (1f + tanOmega)

        // 全通滤波器差分方程
        val output = -g * input + state.x1 + g * state.y1

        // 更新状态
        state.x1 = input
        state.y1 = output

        return output
    }

    /**
     * 计算Linkwitz-Riley 4阶分频滤波器系数
     * @param freq 分频频率 (Hz)
     * @param isLowPass true为低通，false为高通
     */
    private fun calculateCrossoverCoefficients(freq: Float, isLowPass: Boolean): CrossoverCoefficients {
        val omega = 2f * PI.toFloat() * freq / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / 1.41421356f  // Q = 0.707 for Butterworth

        // 二阶滤波器系数 (每个Linkwitz-Riley 4阶需要两个级联的二阶滤波器)
        val b0 = if (isLowPass) {
            (1f - cosOmega) / 2f
        } else {
            (1f + cosOmega) / 2f
        }
        val b1 = if (isLowPass) {
            1f - cosOmega
        } else {
            -(1f + cosOmega)
        }
        val b2 = b0
        val a0 = 1f + alpha
        val a1 = -2f * cosOmega
        val a2 = 1f - alpha

        // 归一化
        return CrossoverCoefficients(
            b0 = b0 / a0,
            b1 = b1 / a0,
            b2 = b2 / a0,
            a1 = a1 / a0,
            a2 = a2 / a0
        )
    }

    /**
     * 应用分频滤波器 (低通)
     */
    private fun applyCrossover(
        input: Float,
        state: CrossoverFilterState,
        coef: CrossoverCoefficients
    ): Triple<Float, Float, Float> {
        // 二阶滤波器级联实现4阶Linkwitz-Riley
        val stage1 = biquadFilter(input, state.x1, state.x2, state.y1, state.y2, coef)
        val stage2 = biquadFilter(stage1, state.x3, state.x4, state.y3, state.y4, coef)

        // 更新状态
        state.x4 = state.x3
        state.x3 = stage1
        state.x2 = state.x1
        state.x1 = input
        state.y4 = state.y3
        state.y3 = stage2
        state.y2 = stage1
        state.y1 = stage2

        return Triple(stage2, stage1, input)
    }

    /**
     * 应用高通分频滤波器
     */
    private fun applyHighCrossover(
        input: Float,
        state: CrossoverFilterState,
        coef: CrossoverCoefficients
    ): Float {
        return applyCrossover(input, state, coef).first
    }

    /**
     * 二阶滤波器 (Biquad)
     */
    private fun biquadFilter(
        x0: Float, x1: Float, x2: Float,
        y1: Float, y2: Float,
        coef: CrossoverCoefficients
    ): Float {
        return coef.b0 * x0 + coef.b1 * x1 + coef.b2 * x2 -
                coef.a1 * y1 - coef.a2 * y2
    }

    /**
     * 分频滤波器系数
     */
    private data class CrossoverCoefficients(
        var b0: Float, var b1: Float, var b2: Float,
        var a1: Float, var a2: Float
    )

    /**
     * 重置处理器状态
     */
    fun reset() {
        lowFilterL.x1 = 0f; lowFilterL.y1 = 0f
        lowFilterR.x1 = 0f; lowFilterR.y1 = 0f
        midFilterL.x1 = 0f; midFilterL.y1 = 0f
        midFilterR.x1 = 0f; midFilterR.y1 = 0f
        highFilterL.x1 = 0f; highFilterL.y1 = 0f
        highFilterR.x1 = 0f; highFilterR.y1 = 0f

        lowCrossoverL.x1 = 0f; lowCrossoverL.x2 = 0f; lowCrossoverL.x3 = 0f; lowCrossoverL.x4 = 0f
        lowCrossoverL.y1 = 0f; lowCrossoverL.y2 = 0f; lowCrossoverL.y3 = 0f; lowCrossoverL.y4 = 0f
        lowCrossoverR.x1 = 0f; lowCrossoverR.x2 = 0f; lowCrossoverR.x3 = 0f; lowCrossoverR.x4 = 0f
        lowCrossoverR.y1 = 0f; lowCrossoverR.y2 = 0f; lowCrossoverR.y3 = 0f; lowCrossoverR.y4 = 0f

        midCrossoverLowL.x1 = 0f; midCrossoverLowL.x2 = 0f; midCrossoverLowL.x3 = 0f; midCrossoverLowL.x4 = 0f
        midCrossoverLowL.y1 = 0f; midCrossoverLowL.y2 = 0f; midCrossoverLowL.y3 = 0f; midCrossoverLowL.y4 = 0f
        midCrossoverLowR.x1 = 0f; midCrossoverLowR.x2 = 0f; midCrossoverLowR.x3 = 0f; midCrossoverLowR.x4 = 0f
        midCrossoverLowR.y1 = 0f; midCrossoverLowR.y2 = 0f; midCrossoverLowR.y3 = 0f; midCrossoverLowR.y4 = 0f

        midCrossoverHighL.x1 = 0f; midCrossoverHighL.x2 = 0f; midCrossoverHighL.x3 = 0f; midCrossoverHighL.x4 = 0f
        midCrossoverHighL.y1 = 0f; midCrossoverHighL.y2 = 0f; midCrossoverHighL.y3 = 0f; midCrossoverHighL.y4 = 0f
        midCrossoverHighR.x1 = 0f; midCrossoverHighR.x2 = 0f; midCrossoverHighR.x3 = 0f; midCrossoverHighR.x4 = 0f
        midCrossoverHighR.y1 = 0f; midCrossoverHighR.y2 = 0f; midCrossoverHighR.y3 = 0f; midCrossoverHighR.y4 = 0f

        highCrossoverL.x1 = 0f; highCrossoverL.x2 = 0f; highCrossoverL.x3 = 0f; highCrossoverL.x4 = 0f
        highCrossoverL.y1 = 0f; highCrossoverL.y2 = 0f; highCrossoverL.y3 = 0f; highCrossoverL.y4 = 0f
        highCrossoverR.x1 = 0f; highCrossoverR.x2 = 0f; highCrossoverR.x3 = 0f; highCrossoverR.x4 = 0f
        highCrossoverR.y1 = 0f; highCrossoverR.y2 = 0f; highCrossoverR.y3 = 0f; highCrossoverR.y4 = 0f
    }

    /**
     * 应用预设参数
     */
    fun applyPreset(preset: DCPhasePreset) {
        when (preset) {
            DCPhasePreset.NONE -> {
                enabled = false
            }
            DCPhasePreset.NATURAL -> {
                enabled = true
                correctionStrength = 0.4f
                lowFreqDelay = 0.25f
                midFreqDelay = 0.15f
                highFreqDelay = 0.1f
                crossoverFreq = 500.0f
                highCrossoverFreq = 3000.0f
            }
            DCPhasePreset.PRECISE -> {
                enabled = true
                correctionStrength = 0.7f
                lowFreqDelay = 0.4f
                midFreqDelay = 0.3f
                highFreqDelay = 0.2f
                crossoverFreq = 600.0f
                highCrossoverFreq = 4000.0f
            }
            DCPhasePreset.SPATIAL -> {
                enabled = true
                correctionStrength = 0.6f
                lowFreqDelay = 0.5f
                midFreqDelay = 0.25f
                highFreqDelay = 0.15f
                crossoverFreq = 400.0f
                highCrossoverFreq = 2500.0f
            }
            DCPhasePreset.CUSTOM -> {
                // 使用当前自定义参数
                enabled = true
            }
        }
    }
}

/**
 * DC Phase Linearizer 预设模式
 */
enum class DCPhasePreset(val displayName: String, val description: String) {
    NONE("关闭", "禁用 DC Phase Linearizer"),
    NATURAL("自然", "轻度相位校正，保持原有音色"),
    PRECISE("精确", "中度相位校正，提升结像力"),
    SPATIAL("空间", "增强空间感和声场深度"),
    CUSTOM("自定义", "手动调整各项参数")
}
