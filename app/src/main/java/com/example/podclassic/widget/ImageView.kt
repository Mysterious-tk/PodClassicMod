package com.example.podclassic.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import com.example.podclassic.values.Colors

class ImageView : androidx.appcompat.widget.AppCompatImageView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attr: AttributeSet?) : super(context, attr)

    private val paint = Paint().apply {
        color = Colors.text
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rect = canvas.clipBounds.apply {
            bottom--
            right--
            top++
            left++
        }

        canvas.drawRect(rect, paint)
    }
}