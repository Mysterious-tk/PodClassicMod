package com.example.podclassic.base

import com.example.podclassic.util.LiveData

class Observer {

    var enable = false

    private val liveData = ArrayList<LiveData<*>>()

    fun addLiveData(liveData: LiveData<*>, onDataChangeListener: LiveData.OnDataChangeListener) {
        this.liveData.add(liveData)
        liveData.addObserver(this, onDataChangeListener)
    }

    fun onViewCreate() {
        //enable = true
        //for (data in liveData) {
        //    data.onStart(this)
        //}
    }

    fun onViewDelete() {
        for (data in liveData) {
            data.removeObserver(this)
        }
        liveData.clear()
        enable = false
    }

    fun onViewAdd() {
        enable = true
        for (data in liveData) {
            data.onStart(this)
        }
    }

    fun onViewRemove() {
        for (data in liveData) {
            data.onStop(this)
        }
        enable = false
    }
}