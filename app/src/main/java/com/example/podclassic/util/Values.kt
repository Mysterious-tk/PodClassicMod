package com.example.podclassic.util

import com.example.podclassic.activity.MainActivity
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.storage.SPManager


object Values {
    const val POD = "iPod"

    const val NULL = "空"
    const val RESOLUTION_HIGH = 1440
    const val RESOLUTION_LOW = 720

    val DEFAULT_PADDING by lazy {
        when {
            resolution >= RESOLUTION_HIGH -> 36
            resolution <= RESOLUTION_LOW && resolution != 0 -> 12
            else -> 24
        }
    }

    var resolution = 0

    fun getVersionName(): String {
        val context = BaseApplication.getContext()
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }
}