package com.example.podclassic.values

import com.example.podclassic.base.BaseApplication


object Values {
    const val LAUNCHER = false

    const val RESOLUTION_HIGH = 1440
    const val RESOLUTION_LOW = 720
    const val IMAGE_WIDTH = 400

    val LINE_WIDTH by lazy {
        when {
            screenWidth >= RESOLUTION_HIGH -> 3
            screenWidth <= RESOLUTION_LOW && screenWidth != 0 -> 1
            else -> 2
        }
    }

    val DEFAULT_PADDING by lazy {
        when {
            screenWidth >= RESOLUTION_HIGH -> 36
            screenWidth <= RESOLUTION_LOW && screenWidth != 0 -> 12
            else -> 24
        }
    }

    var screenWidth = 0
    var screenHeight = 0

    fun getVersionName(): String {
        val context = BaseApplication.context
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }
}
