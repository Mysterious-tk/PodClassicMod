package com.example.podclassic.base

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import com.example.podclassic.`object`.Core

class BaseApplication : Application() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
    }

    override fun onCreate() {
        super.onCreate()
        context = this
        Core.init()
    }
}