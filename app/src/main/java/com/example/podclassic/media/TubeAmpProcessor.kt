package com.example.podclassic.media

import kotlin.math.*

/**
 * 胆机（真空管）音效处理器
 *
 * 模拟真空管放大器的温暖音色特性：
 * - 偶次谐波失真：产生以2次、4次谐波为主的丰富泛音
 * - 动态压缩：平滑的动态响应，增加音乐厚度
 * - 软削波：渐进式饱和，避免数字削波的刺耳感
 * - 温暖音色：中低频饱满，高频柔和
 */
class TubeAmpProcessor {

    // 胆机参数
    var tubeGain: Float = 1.2f              // 管增益 (1.0 - 2.0)
    var saturationAmount: Float = 0.6f      // 饱和度 (0.0 - 1.0)
    var harmonicContent: Float = 0.3f       // 谐波含量 (0.0 - 1.0)
    var compressionRatio: Float = 4.0f      // 压缩比 (1.0 - 10.0)
    var attackTimeMs: Float = 10.0f         // 启动时间 (ms)
    var releaseTimeMs: Float = 100.0f       // 释放时间 (ms)
    var warmth: Float = 0.5f                // 温暖度 (0.0 - 1.0) - 综合参数
    var enabled: Boolean = false            // 是否启用

    // 内部状态
    private var envelope: Float = 0.0f      // 当前包络值
    private var leftGain: Float = 0.0f      // 左声道压缩增益
    private var rightGain: Float = 0.0f     // 右声道压缩增益
    private var lastSampleL: Float = 0.0f   // 上一个左声道样本
    private var lastSampleR: Float = 0.0f   // 上一个右声道样本

    // 预计算系数
    private val attackCoeff: Float
        get() = calculateTimeCoeff(attackTimeMs)

    private val releaseCoeff: Float
        get() = calculateTimeCoeff(releaseTimeMs)

    // 采样率（假设44.1kHz）
    private val sampleRate: Float = 44100.0f

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

        var i = 0
        while (i < size) {
            // 获取立体声样本（左声道）
            val sampleL = if (i < size) buffer[i].toFloat() / maxSample else 0f
            val sampleR = if (i + 1 < size) buffer[i + 1].toFloat() / maxSample else sampleL

            // 归一化到 -1.0 到 1.0
            var normL = sampleL
            var normR = sampleR

            // 1. 应用胆机增益
            normL *= tubeGain
            normR *= tubeGain

            // 2. 生成偶次谐波失真（幂级数展开）
            // 真空管传递函数: Vout = a1*Vin + a2*Vin^2 + a4*Vin^4
            // 偶次项产生温暖音色
            normL = addEvenHarmonics(normL, harmonicContent)
            normR = addEvenHarmonics(normR, harmonicContent)

            // 3. 软削波（tanh函数模拟真空管饱和）
            normL = softClip(normL, saturationAmount)
            normR = softClip(normR, saturationAmount)

            // 4. 动态压缩
            val envelopeValue = calculateEnvelope(normL, normR)
            val compressedL = applyCompression(normL, envelopeValue, true)
            val compressedR = applyCompression(normR, envelopeValue, false)

            // 5. 应用温暖度滤波器（低频增强）
            val warmL = applyWarmthFilter(compressedL, lastSampleL)
            val warmR = applyWarmthFilter(compressedR, lastSampleR)

            lastSampleL = warmL
            lastSampleR = warmR

            // 6. 限制输出范围并转换回Short
            outputBuffer[i] = max(minShort, min(maxShort, (warmL * maxSample).toInt())).toShort()
            if (i + 1 < size) {
                outputBuffer[i + 1] = max(minShort, min(maxShort, (warmR * maxSample).toInt())).toShort()
            }

            i += 2
        }

        return outputBuffer
    }

    /**
     * 添加偶次谐波失真
     * 使用幂级数展开产生2次和4次谐波
     */
    private fun addEvenHarmonics(sample: Float, amount: Float): Float {
        // 限制输入范围防止数值溢出
        val limited = max(-1.0f, min(1.0f, sample))

        // 偶次谐波失真：a2*x^2 + a4*x^4
        // 系数根据谐波含量调整
        val a2 = amount * 0.5f    // 2次谐波系数
        val a4 = amount * 0.1f    // 4次谐波系数（较小）

        // 计算谐波
        val h2 = a2 * limited * limited
        val h4 = a4 * limited * limited * limited * limited

        // 添加基波和谐波（保持基波主导）
        val fundamental = limited * (1.0f - amount * 0.3f)
        var result = fundamental + h2 + h4

        // 恢复原始信号的符号
        result = if (limited < 0) -abs(result) else abs(result)

        return result
    }

    /**
     * 软削波处理
     * 使用tanh函数模拟真空管的渐进式饱和特性
     */
    private fun softClip(sample: Float, amount: Float): Float {
        // 根据饱和度调整削波点
        val threshold = 1.0f / (0.5f + amount * 0.5f)

        // 归一化到削波点
        val normalized = sample * threshold

        // 使用tanh函数进行软削波
        val clipped = tanh(normalized)

        // 混合原始信号和削波信号
        val dryWetMix = amount
        val result = sample * (1.0f - dryWetMix) + clipped * threshold * dryWetMix

        return result
    }

    /**
     * 计算信号包络
     */
    private fun calculateEnvelope(sampleL: Float, sampleR: Float): Float {
        // 计算左右声道的RMS值
        val rms = sqrt((sampleL * sampleL + sampleR * sampleR) / 2.0f)

        // 平滑包络
        val coeff = if (rms > envelope) attackCoeff else releaseCoeff
        envelope += (rms - envelope) * coeff

        return envelope
    }

    /**
     * 应用动态压缩
     */
    private fun applyCompression(sample: Float, env: Float, isLeft: Boolean): Float {
        if (env < 0.001f) {
            return sample
        }

        // 计算压缩增益
        // 超过阈值的信号被压缩
        val threshold = 0.5f
        val gain = if (env > threshold) {
            val excess = env - threshold
            val compressedExcess = excess / compressionRatio
            threshold / env + compressedExcess / env
        } else {
            1.0f
        }

        // 平滑增益变化
        val targetGain = if (isLeft) gain else gain
        val currentGain = if (isLeft) leftGain else rightGain
        val coeff = if (targetGain < currentGain) attackCoeff else releaseCoeff

        val smoothedGain = currentGain + (targetGain - currentGain) * coeff

        if (isLeft) {
            leftGain = smoothedGain
        } else {
            rightGain = smoothedGain
        }

        return sample * smoothedGain
    }

    /**
     * 应用温暖度滤波器（低频增强）
     * 使用简单的一阶低通滤波器
     */
    private fun applyWarmthFilter(sample: Float, lastSample: Float): Float {
        // 温暖度越高，低频保留越多
        // fc = 100Hz (温暖度最大时) 到 1000Hz (温暖度最小时)
        val minFreq = 100.0f
        val maxFreq = 1000.0f
        val fc = minFreq + (maxFreq - minFreq) * (1.0f - warmth)

        // 计算滤波器系数
        val rc = 1.0f / (2.0f * PI.toFloat() * fc)
        val dt = 1.0f / sampleRate
        val alpha = dt / (rc + dt)

        // 一阶低通滤波器
        return lastSample + alpha * (sample - lastSample)
    }

    /**
     * 计算时间常数对应的系数
     */
    private fun calculateTimeCoeff(timeMs: Float): Float {
        val timeConstant = timeMs / 1000.0f
        return 1.0f - exp(-1.0f / (timeConstant * sampleRate))
    }

    /**
     * 重置处理器状态
     */
    fun reset() {
        envelope = 0.0f
        leftGain = 0.0f
        rightGain = 0.0f
        lastSampleL = 0.0f
        lastSampleR = 0.0f
    }

    /**
     * 设置综合温暖度参数
     * 自动调整多个参数以达到特定的音色风格
     */
    fun updateWarmth(value: Float) {
        warmth = max(0.0f, min(1.0f, value))

        // 根据温暖度自动调整其他参数
        saturationAmount = 0.3f + warmth * 0.5f        // 0.3 - 0.8
        harmonicContent = 0.2f + warmth * 0.4f         // 0.2 - 0.6
        tubeGain = 1.1f + warmth * 0.3f               // 1.1 - 1.4
        compressionRatio = 2.0f + warmth * 4.0f        // 2.0 - 6.0
    }

    /**
     * 应用预设参数
     */
    fun applyPreset(preset: TubeAmpPreset) {
        when (preset) {
            TubeAmpPreset.NONE -> {
                enabled = false
            }
            TubeAmpPreset.WARM -> {
                enabled = true
                tubeGain = 1.1f
                saturationAmount = 0.4f
                harmonicContent = 0.2f
                compressionRatio = 3.0f
                warmth = 0.3f
            }
            TubeAmpPreset.SMOOTH -> {
                enabled = true
                tubeGain = 1.2f
                saturationAmount = 0.6f
                harmonicContent = 0.3f
                compressionRatio = 4.0f
                warmth = 0.5f
            }
            TubeAmpPreset.VINTAGE -> {
                enabled = true
                tubeGain = 1.3f
                saturationAmount = 0.8f
                harmonicContent = 0.4f
                compressionRatio = 5.0f
                warmth = 0.7f
            }
            TubeAmpPreset.DYNAMIC -> {
                enabled = true
                tubeGain = 1.4f
                saturationAmount = 0.7f
                harmonicContent = 0.35f
                compressionRatio = 3.0f
                attackTimeMs = 5.0f
                releaseTimeMs = 50.0f
                warmth = 0.6f
            }
        }
    }
}

/**
 * 胆机预设模式
 */
enum class TubeAmpPreset(val displayName: String, val description: String) {
    NONE("关闭", "禁用胆机音效"),
    WARM("温暖", "轻度胆机味，适合人声"),
    SMOOTH("醇厚", "中等胆机味，适合流行音乐"),
    VINTAGE("复古", "重度胆机味，适合爵士和古典"),
    DYNAMIC("动态", "快速响应，适合摇滚和电子音乐")
}
