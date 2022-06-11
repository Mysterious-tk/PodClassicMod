package com.example.podclassic.values

import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.core.content.ContextCompat
import com.example.podclassic.R
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.storage.SPManager

object Colors {
    val text by lazy { ContextCompat.getColor(BaseApplication.context, R.color.text) }

    val color_primary by lazy {
        ContextCompat.getColor(
            BaseApplication.context,
            R.color.colorPrimary
        )
    }

    val text_light by lazy { ContextCompat.getColor(BaseApplication.context, R.color.text_light) }
    val background by lazy { ContextCompat.getColor(BaseApplication.context, R.color.background) }
    val background_dark_1 by lazy {
        ContextCompat.getColor(
            BaseApplication.context,
            R.color.background_dark_1
        )
    }
    val background_dark_2 by lazy {
        ContextCompat.getColor(
            BaseApplication.context,
            R.color.background_dark_2
        )
    }
    val line by lazy { ContextCompat.getColor(BaseApplication.context, R.color.line) }

    val main by lazy { ContextCompat.getColor(BaseApplication.context, R.color.main) }
    val main_light by lazy { ContextCompat.getColor(BaseApplication.context, R.color.main_light) }

    val battery_green by lazy {
        ContextCompat.getColor(
            BaseApplication.context,
            R.color.battery_green
        )
    }
    val battery_red by lazy { ContextCompat.getColor(BaseApplication.context, R.color.battery_red) }
    val battery_yellow by lazy {
        ContextCompat.getColor(
            BaseApplication.context,
            R.color.battery_yellow
        )
    }

    val white by lazy { ContextCompat.getColor(BaseApplication.context, R.color.white) }
    val black by lazy { ContextCompat.getColor(BaseApplication.context, R.color.black) }

    val theme_red by lazy { ContextCompat.getColor(BaseApplication.context, R.color.theme_red) }
    val theme_black by lazy { ContextCompat.getColor(BaseApplication.context, R.color.theme_black) }
    val theme_white by lazy { ContextCompat.getColor(BaseApplication.context, R.color.theme_white) }

    val controller: Int
        get() = when (SPManager.getInt(SPManager.Theme.SP_NAME)) {
            SPManager.Theme.RED.id -> theme_red
            SPManager.Theme.BLACK.id -> theme_black
            SPManager.Theme.WHITE.id -> theme_white
            else -> theme_red
        }

    val screen: Int
        get() = when (SPManager.getInt(SPManager.Theme.SP_NAME)) {
            SPManager.Theme.RED.id or SPManager.Theme.BLACK.id -> black
            SPManager.Theme.WHITE.id -> white
            else -> black
        }

    val button: Int
        get() = when (SPManager.getInt(SPManager.Theme.SP_NAME)) {
            SPManager.Theme.RED.id or SPManager.Theme.BLACK.id -> black
            SPManager.Theme.WHITE.id -> white
            else -> black
        }

    fun getShader(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        c1: Int,
        c2: Int,
        tileMode: Shader.TileMode = Shader.TileMode.CLAMP
    ): Shader {
        return LinearGradient(x0, y0, x1, y1, c1, c2, tileMode)
    }
}