package com.example.podclassic.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit


object ThreadUtil {
    private val handler = Handler(Looper.getMainLooper())

    fun runOnUiThread(runnable: Runnable) {
        handler.post(runnable)
    }

    private val threadPoolExecutor = ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, SynchronousQueue<Runnable>())

    fun newThread(runnable: Runnable) {
        threadPoolExecutor.execute(runnable)
    }
}