package com.example.podclassic.util

import java.util.*
import java.util.Timer


//一个可以暂停的Timer
class Timer {
    private val freq: Long
    private val once: Boolean
    private val task: Task?
    private val function: ((Long) -> Unit)?

    private val timerTask: TimerTask
        get() {
            return object : TimerTask() {
                override fun run() {
                    timeCount += freq
                    task?.apply { run(timeCount) }
                    function?.let { it(timeCount) }
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

    private var timer: Timer? = null


    @Synchronized
    fun start() {
        start(timeCount)
    }

    @Synchronized
    fun start(timeCount: Long) {
        timer?.cancel()
        this.timeCount = timeCount
        timer = Timer().apply {
            if (once) {
                schedule(timerTask, freq)
            } else {
                schedule(timerTask, freq, freq)
            }
        }
    }

    fun pause() {
        timer?.cancel()
        timer = null
    }

    fun reset() {
        timer?.cancel()
        timer = null
        timeCount = 0L
    }
}