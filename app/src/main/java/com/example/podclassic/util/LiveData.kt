package com.example.podclassic.util

import com.example.podclassic.base.Observer

class LiveData<E>(e: E? = null) {
    interface OnDataChangeListener {
        fun onStart() {}
        fun onStop() {}
        fun onDataChange() {}
    }

    private var data: E? = e
    private val functionList = HashMap<Observer, OnDataChangeListener>()
    fun get(): E? {
        return data
    }

    fun set(e: E?) {
        this.data = e
        onDataChange()
    }

    private fun onDataChange() {
        for ((observer, listener) in functionList) {
            if (observer.enable) {
                listener.onDataChange()
            }
        }
    }

    fun onStart(observer: Observer) {
        if (observer.enable) {
            functionList[observer]?.onStart()
        }
    }

    fun onStop(observer: Observer) {
        if (observer.enable) {
            functionList[observer]?.onStop()
        }
    }

    fun addObserver(observer: Observer, listener: OnDataChangeListener) {
        functionList[observer] = listener
    }

    fun removeObserver(observer: Observer) {
        functionList.remove(observer)
    }
}