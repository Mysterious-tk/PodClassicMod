package com.example.podclassic.util

import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.core.content.ContextCompat
import com.example.podclassic.R
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.storage.SPManager

object Colors {
    val text by lazy { ContextCompat.getColor(BaseApplication.context, R.color.text) }
    val transparent by lazy { ContextCompat.getColor(BaseApplication.context, R.color.transparent) }
    val color_primary by lazy { ContextCompat.getColor(BaseApplication.context, R.color.colorPrimary) }
    val color_primary_dark by lazy { ContextCompat.getColor(BaseApplication.context, R.color.colorPrimaryDark) }
    val text_light by lazy { ContextCompat.getColor(BaseApplication.context, R.color.text_light) }
    val background by lazy { ContextCompat.getColor(BaseApplication.context, R.color.background) }
    val background_dark_1 by lazy { ContextCompat.getColor(BaseApplication.context, R.color.background_dark_1) }
    val background_dark_2 by lazy { ContextCompat.getColor(BaseApplication.context, R.color.background_dark_2) }
    val line by lazy { ContextCompat.getColor(BaseApplication.context, R.color.line) }

    val main by lazy { ContextCompat.getColor(BaseApplication.context, R.color.main) }

    val battery_green by lazy { ContextCompat.getColor(BaseApplication.context, R.color.battery_green) }
    val battery_red by lazy { ContextCompat.getColor(BaseApplication.context, R.color.battery_red) }
    val battery_yellow by lazy { ContextCompat.getColor(BaseApplication.context, R.color.battery_yellow) }

    val controller : Int get () { return if (SPManager.getBoolean(SPManager.SP_THEME)) red else gray }

    private val red by lazy { ContextCompat.getColor(BaseApplication.context, R.color.red) }
    private val gray by lazy { ContextCompat.getColor(BaseApplication.context, R.color.gray) }
    val white by lazy { ContextCompat.getColor(BaseApplication.context, R.color.white) }

    fun getShader(x0 : Float, y0 : Float, x1 : Float, y1 : Float, c1 : Int, c2 : Int) : Shader {
        return LinearGradient(x0, y0, x1, y1, c1, c2, Shader.TileMode.CLAMP)
    }
}