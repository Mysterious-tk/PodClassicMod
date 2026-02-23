package com.example.podclassic.media

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