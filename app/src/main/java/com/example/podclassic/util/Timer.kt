package com.example.podclassic.util

import java.util.Timer as JavaTimer
import java.util.TimerTask


//一个可以暂停的Timer
class Timer {
    private val freq: Long
    private val once: Boolean
    private val task: Task?
    private val function: ((Long) -> Unit)?

    private fun timerTask(generation: Long): TimerTask {
        return object : TimerTask() {
            override fun run() {
                val nextTime = synchronized(this@Timer) {
                    if (generation != timerGeneration) {
                        return
                    }
                    timeCount += freq
                    if (once) {
                        timer = null
                    }
                    timeCount
                }
                task?.apply { run(nextTime) }
                function?.let { it(nextTime) }
            }
        }
    }

    constructor(freq: Long, once: Boolean = false, task: Task) {
        this.freq = freq
        this.once = once
        this.task = task
        function = null
    }

    constructor(freq: Long, once: Boolean = false, function: (Long) -> Unit) {
        this.freq = freq
        this.once = once
        this.function = function
        task = null
    }

    interface Task {
        fun run(timeCount: Long)
    }

    var timeCount = 0L

    private var timer: JavaTimer? = null
    private var timerGeneration = 0L


    @Synchronized
    fun start() {
        start(timeCount)
    }

    @Synchronized
    fun start(timeCount: Long) {
        cancelTimer()
        this.timeCount = timeCount
        val generation = ++timerGeneration
        timer = JavaTimer("PodClassicTimer", true).apply {
            if (once) {
                schedule(timerTask(generation), freq)
            } else {
                schedule(timerTask(generation), freq, freq)
            }
        }
    }

    @Synchronized
    fun pause() {
        timerGeneration++
        cancelTimer()
    }

    @Synchronized
    fun reset() {
        timerGeneration++
        cancelTimer()
        timeCount = 0L
    }

    private fun cancelTimer() {
        timer?.cancel()
        timer?.purge()
        timer = null
    }
}
