package com.example.podclassic.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.example.podclassic.values.Colors
import com.example.podclassic.values.Values
import kotlin.math.sqrt


class SeekBar(context: Context) : LinearLayout(context) {

    companion object {
        private val BAR_HEIGHT = (Values.DEFAULT_PADDING * 2)
    }


    private val front = object : View(context) {
        private val paint by lazy { Paint().apply { shader = null } }
        private val rect by lazy { RectF() }
        var seekMode = false

        override fun onDraw(canvas: Canvas?) {
            if (paint.shader == null) {
                paint.shader = Colors.getShader(
                    0f,
                    0f,
                    0f,
                    height / 2f,
                    Colors.main_light,
                    Colors.main,
                    Shader.TileMode.MIRROR
                )
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

    private val back = object : View(context) {
        private val paint by lazy { Paint().apply { shader = null } }

        override fun onDraw(canvas: Canvas?) {
            if (paint.shader == null) {
                paint.shader = Colors.getShader(
                    0f,
                    height / 2f,
                    0f,
                    height.toFloat(),
                    Colors.background_dark_1,
                    Colors.background_dark_2
                )
            }
            canvas?.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }

    private inner class Layout(context: Context?) : ViewGroup(context) {
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            if (!changed) {
                return
            }
            back.layout(l, t, r, b)
            front.layout(l, t, width * current / max, b)
        }
    }

    private val layout = Layout(context)
    private val linearLayout = LinearLayout(context)

    private val leftView = TextView(context)
    private val rightView = TextView(context)

    init {
        orientation = VERTICAL
        layout.apply {
            addView(back)
            addView(front)
        }
        linearLayout.apply {
            addView(
                leftView.apply { gravity = Gravity.START; setPadding(Values.DEFAULT_PADDING, 0, 0, 0) },
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(
                rightView.apply { gravity = Gravity.END; setPadding(0, 0, Values.DEFAULT_PADDING, 0) },
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1f)
            )
            setPadding(0, Values.DEFAULT_PADDING, 0, 0)
        }

        addView(layout, LayoutParams(LayoutParams.MATCH_PARENT, BAR_HEIGHT))
        addView(linearLayout)
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

    fun getProgress(): Int {
        return current
    }

    fun getMax(): Int {
        return max
    }

    fun setLeftText(text: String) {
        if (textVisibility == VISIBLE) {
            leftView.text = text
        }
    }

    fun getLeftText(): String {
        return leftView.text.toString()
    }

    fun setLeftIcon(drawable: Drawable) {
        leftView.setCompoundDrawables(drawable, null, null, null)
    }

    fun setRightIcon(drawable: Drawable) {
        rightView.setCompoundDrawables(null, null, drawable, null)
    }

    fun set(cur: Int, duration: Int) {

        if (duration in 1 until 1000 * 60 * 720) {
            this.max = duration
        } else {
            this.max = 1
        }
        if (cur in 0 until 1000 * 60 * 720) {
            this.current = cur
        } else {
            this.current = 0
        }
        onCurrentPositionChange()
    }


    @SuppressLint("SetTextI18n")
    fun setMax(duration: Int) {

        if (duration in 1 until 1000 * 60 * 720) {
            this.max = duration
        } else {
            this.max = 1
        }
        onCurrentPositionChange()
    }

    fun setCurrent(cur: Int) {
        if (cur in 0 until 1000 * 60 * 720) {
            this.current = cur
        } else {
            this.current = 0
        }
        onCurrentPositionChange()
    }

    fun getCurrent(): Int {
        return current
    }

    fun setSeekMode(seekMode: Boolean) {
        front.seekMode = seekMode
        front.invalidate()
    }

    private fun onCurrentPositionChange() {
        if (textVisibility == View.VISIBLE) {
            val ms = (current / 1000) * 1000
            leftView.text = toMinute(ms)
            rightView.text = toMinute(max - ms)
        }
        front.layout(0, 0, width * current / max, BAR_HEIGHT)
    }


    private fun toMinute(temp: Int): String {
        val stringBuilder = StringBuilder()
        val ms = temp
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