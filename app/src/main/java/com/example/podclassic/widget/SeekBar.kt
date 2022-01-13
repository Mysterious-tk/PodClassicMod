package com.example.podclassic.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.example.podclassic.util.Colors
import com.example.podclassic.util.Values
import kotlin.math.sqrt


class SeekBar(context: Context) : ViewGroup(context) {

    companion object {
        private val BAR_HEIGHT = Values.DEFAULT_PADDING * 2
    }


    private val bar = object : View(context) {
        private val paint by lazy { Paint().apply { shader = null } }
        private val rect by lazy { RectF() }
        var seekMode = false

        override fun onDraw(canvas: Canvas?) {
            if (paint.shader == null) {
                paint.shader = Colors.getShader(0f, 0f, 0f, height / 2f, Colors.main_light, Colors.main, Shader.TileMode.MIRROR)
            }
            if (seekMode) {
                val sqrt2 = sqrt(2f)
                val len = right.toFloat() / sqrt2 - height / 3f //why 3f?
                rect.set(len - height / sqrt2, len, len, len + height / sqrt2)
                canvas?.rotate(-45f)
                canvas?.drawRect(rect, paint)
            } else {
                canvas?.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
        }
    }

    private val background = object : View(context) {
        private val paint by lazy { Paint().apply { shader = null } }

        override fun onDraw(canvas: Canvas?) {
            if (paint.shader == null) {
                paint.shader = Colors.getShader(0f, height / 2f, 0f, height.toFloat(), Colors.background_dark_1, Colors.background_dark_2)
            }
            canvas?.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }

    private val frameLayout = FrameLayout(context)

    private val leftView = TextView(context)
    private val rightView = TextView(context)

    init {
        addView(background)
        addView(bar)
        leftView.setPadding(0, Values.DEFAULT_PADDING, Values.DEFAULT_PADDING * 2, 0)
        leftView.ellipsize = TextUtils.TruncateAt.END
        rightView.setPadding(Values.DEFAULT_PADDING * 2, Values.DEFAULT_PADDING, 0, 0)
        leftView.ellipsize = TextUtils.TruncateAt.START
        //rightView.gravity = Gravity.END
        //rightView.ellipsize = TextUtils.TruncateAt.MARQUEE

        frameLayout.apply {
            addView(leftView, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            //leftView.setPadding(0, BAR_HEIGHT / 2,0,0)
            //addView(leftView)

            //val layoutParamsRight = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            //rightView.gravity = Gravity.END
            //rightView.setPadding(0, BAR_HEIGHT / 2,0,0)
            addView(rightView, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END
            })
        }

        addView(frameLayout)
    }

    private var max = 1

    private var current = 0

    var textVisibility = View.VISIBLE
    set(value) {
        if (value != VISIBLE) {
            leftView.text = null
            rightView.text = null
        }
        field = value
    }

    fun getProgress() : Int {
        return current
    }

    fun getMax() : Int {
        return max
    }

    fun setLeftText(text : String) {
        if (textVisibility == VISIBLE) {
            leftView.text = text
        }
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

    fun set(cur : Int, duration: Int) {
        if (duration in 1..1000 * 60 * 720) {
            this.max = duration
        } else {
            this.max = 1
        }
        if (cur in 0..1000 * 60 * 720) {
            this.current = cur
        } else {
            this.current = 0
        }
        onCurrentPositionChange()
    }


    @SuppressLint("SetTextI18n")
    fun setMax(duration : Int) {
        if (duration in 1..1000 * 60 * 720) {
            this.max = duration
        } else {
            this.max = 1
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

    fun setSeekMode(seekMode : Boolean) {
        bar.seekMode = seekMode
        bar.invalidate()
    }

    private fun onCurrentPositionChange() {
        if (textVisibility == View.VISIBLE) {
            val ms = (current / 1000) * 1000
            leftView.text = toMinute(ms)
            rightView.text = toMinute(ms - max)
        }
        if (width != 0) {
            bar.layout(0, 0, width * current / max, BAR_HEIGHT)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, BAR_HEIGHT * 3)
        background.measure(width, BAR_HEIGHT)
        bar.measure(width, BAR_HEIGHT)
        frameLayout.measure(width, BAR_HEIGHT * 2)
        //leftView.measure((width / 2), (BAR_HEIGHT * 2))
        //rightView.measure((width / 2), (BAR_HEIGHT * 2))

    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        //if (!changed) {
        //    return
        //}
        background.layout(0, 0, right, BAR_HEIGHT)
        frameLayout.layout(0, BAR_HEIGHT, right, bottom)
        //leftView.layout(0, BAR_HEIGHT, right / 2, bottom)
        //rightView.layout(right / 2, BAR_HEIGHT, right, bottom)
        bar.layout(0, 0, right * current / max, BAR_HEIGHT)
    }



    private fun toMinute(temp : Int): String {
        val stringBuilder = StringBuilder()
        val ms = if (temp < 0) {
            stringBuilder.append('-')
            -temp
        } else {
            temp
        }
        //val s = (if (temp < 0) floor(ms.toDouble() / 1000.0) else ceil(ms.toDouble() / 1000.0)).toInt()
        val s = ms / 1000
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