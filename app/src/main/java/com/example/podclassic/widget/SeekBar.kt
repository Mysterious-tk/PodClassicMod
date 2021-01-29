package com.example.podclassic.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.example.podclassic.util.Colors
import com.example.podclassic.util.Values

class SeekBar(context: Context) : FrameLayout(context) {

    companion object {
        private val BAR_HEIGHT = Values.DEFAULT_PADDING * 2
    }

    private val bar = View(context)
    private val background = View(context)
    private val leftView = TextView(context)
    private val rightView = TextView(context)

    init {
        background.setBackgroundColor(Colors.background_dark_2)
        bar.setBackgroundColor(Colors.main)
        addView(background)
        addView(bar)

        val layoutParamsLeft = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        leftView.setPadding(0, BAR_HEIGHT / 2,0,0)
        addView(leftView, layoutParamsLeft)

        val layoutParamsRight = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        rightView.gravity = Gravity.END
        rightView.setPadding(0, BAR_HEIGHT / 2,0,0)
        addView(rightView, layoutParamsRight)
    }

    private var max = 1

    private var current = 0


    var textVisibility = View.VISIBLE
    set(value) {
        if (value != VISIBLE) {
            leftView.text = ""
            rightView.text = ""
        }
        field = value
    }

    fun setLeftText(text : String) {
        leftView.text = text
    }

    fun getLeftText() : String {
        return leftView.text.toString()
    }

    fun setLeftIcon(drawable: Drawable) {
        leftView.setLeftIcon(drawable)
    }

    fun setRightIcon(drawable: Drawable) {
        rightView.setRightIcon(drawable)
    }

    fun setMax(duration : Int) {
        if (duration in 1..1000 * 60 * 720) {
            this.max = duration
        } else {
            this.max = 1
        }
        if (textVisibility == View.VISIBLE) {
            rightView.setBufferedText(toMinute(this.max))
        }
        onCurrentPositionChange()
    }

    fun setCurrent(cur : Int) {
        if (cur in 0..1000 * 60 * 720) {
            this.current = cur
        } else {
            this.current = 0
        }
        onCurrentPositionChange()
    }

    fun getCurrent() : Int {
        return current
    }

    private fun onCurrentPositionChange() {
        if (textVisibility == View.VISIBLE) {
            leftView.text = toMinute(current)
        }
        if (width != 0) {
            bar.layout(0, 0, width * current / max, BAR_HEIGHT)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, BAR_HEIGHT * 3)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        background.layout(0, 0, width, BAR_HEIGHT)
        leftView.layout(0, BAR_HEIGHT, width / 2, height)
        rightView.layout(width / 2, BAR_HEIGHT, width, height)
        bar.layout(0, 0, width * current / max, BAR_HEIGHT)
    }

    private fun toMinute(ms: Int): String {
        val s = ms / 1000
        val stringBuilder = StringBuilder()
        val min = s / 60
        if (min < 10) {
            stringBuilder.append('0')
        }
        stringBuilder.append(min)
        stringBuilder.append(":")
        if (s % 60 < 10) {
            stringBuilder.append('0')
        }
        stringBuilder.append(s % 60)
        return stringBuilder.toString()
    }
}