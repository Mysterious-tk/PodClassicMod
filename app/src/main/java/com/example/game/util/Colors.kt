package com.example.game.util

import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.core.content.ContextCompat
import com.example.podclassic.R
import com.example.podclassic.base.BaseApplication

object Colors {
    val context: Context by lazy { BaseApplication.context }
    fun getColor(id: Int): Int {
        return ContextCompat.getColor(context, id)
    }

    val line by lazy { getColor(R.color.colorPrimary) }

    val ball_1 by lazy { getColor(R.color.ball_1) }
    val ball_2 by lazy { getColor(R.color.ball_2) }

    val board_1 by lazy { getColor(R.color.board_1) }
    val board_2 by lazy { getColor(R.color.board_2) }

    val brick_1 by lazy {
        arrayOf(
            getColor(R.color.brick_1),
            getColor(R.color.brick_3),
            getColor(R.color.brick_5),
            getColor(R.color.brick_7),
            getColor(R.color.brick_9)
        )
    }
    val brick_2 by lazy {
        arrayOf(
            getColor(R.color.brick_2),
            getColor(R.color.brick_4),
            getColor(R.color.brick_6),
            getColor(R.color.brick_8),
            getColor(R.color.brick_10)
        )
    }

    val background_1 by lazy { getColor(R.color.background_1) }
    val background_2 by lazy { getColor(R.color.background_2) }

    fun getShader(x0: Float, y0: Float, x1: Float, y1: Float, c1: Int, c2: Int): Shader {
        return LinearGradient(x0, y0, x1, y1, c1, c2, Shader.TileMode.CLAMP)
    }
}