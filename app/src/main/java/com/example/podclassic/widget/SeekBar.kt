package com.example.podclassic.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import com.example.podclassic.values.Colors
import com.example.podclassic.values.Values
import kotlin.math.sqrt


class SeekBar(context: Context) : LinearLayout(context) {

    companion object {
        private val BAR_HEIGHT = (Values.DEFAULT_PADDING * 2)
    }

    private val leftTime: AppCompatTextView
    private val rightTime: AppCompatTextView
    private var progressView: ProgressView
    private val leftIcon: ImageView
    private val rightIcon: ImageView

    private var max = 1
    private var current = 0

    // Custom ProgressView class to access seekMode property
    private inner class ProgressView(context: Context) : View(context) {
        val paint = Paint().apply { shader = null }
        val backPaint = Paint().apply { shader = null }
        val rect = RectF()
        var seekMode = false

        // Pre-allocated Paint objects to avoid allocation in onDraw
        private val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        private val glassPaint = Paint()
        private val progressBorderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        private val highlightPaint = Paint()
        private val gradientPaint = Paint()
        private val markerPaint = Paint()
        private val markerBorderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        private val markerHighlightPaint = Paint()
        private val highlightRect = RectF()

        override fun onDraw(canvas: Canvas) {
            // Draw background with glassmorphism effect
            val backgroundColor = Color.parseColor("#202020")
            backPaint.color = backgroundColor
            backPaint.alpha = 150 // Reduced opacity for more transparency
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), height.toFloat() / 2, height.toFloat() / 2, backPaint)
            
            // Add subtle border to background
            borderPaint.color = Color.WHITE
            borderPaint.alpha = 20 // Reduced opacity for more transparency
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), height.toFloat() / 2, height.toFloat() / 2, borderPaint)
            
            // Draw progress bar with glassmorphism effect
            val progressWidth = width * current / max.toFloat()
            
            if (progressWidth > 0) {
                // Create blue glassmorphism effect for progress
                val blueColor = Color.parseColor("#4A90D9")
                glassPaint.color = blueColor
                glassPaint.alpha = 140 // Reduced opacity for more transparency
                
                // Draw main progress bar
                rect.set(0f, 0f, progressWidth, height.toFloat())
                canvas.drawRoundRect(rect, height.toFloat() / 2, height.toFloat() / 2, glassPaint)
                
                // Add inner border to progress bar
                progressBorderPaint.color = Color.WHITE
                progressBorderPaint.alpha = 40 // Reduced opacity for more transparency
                canvas.drawRoundRect(rect, height.toFloat() / 2, height.toFloat() / 2, progressBorderPaint)
                
                // Add glass highlight effect
                highlightPaint.color = Color.WHITE
                highlightPaint.alpha = 60 // Reduced opacity for more transparency
                
                // Draw top highlight
                highlightRect.set(0f, 0f, progressWidth, height.toFloat() / 2)
                canvas.drawRoundRect(highlightRect, height.toFloat() / 2, height.toFloat() / 2, highlightPaint)
                
                // Draw subtle gradient overlay for depth
                gradientPaint.shader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    height.toFloat(),
                    Color.parseColor("#30FFFFFF"), // Reduced opacity for more transparency
                    Color.parseColor("#00FFFFFF"),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(rect, height.toFloat() / 2, height.toFloat() / 2, gradientPaint)
            }
            
            if (seekMode) {
                // Calculate marker position based on current progress
                val markerPosition = width * current / max.toFloat()
                
                // Draw a circular marker at the current progress position
                val markerSize = height * 1.5f
                val halfMarkerSize = markerSize / 2
                
                // Draw marker background
                markerPaint.color = Color.parseColor("#4A90D9")
                markerPaint.alpha = 160 // Reduced opacity for more transparency
                canvas.drawCircle(markerPosition, height / 2f, halfMarkerSize, markerPaint)
                
                // Draw marker border
                markerBorderPaint.color = Color.WHITE
                markerBorderPaint.alpha = 120 // Reduced opacity for more transparency
                canvas.drawCircle(markerPosition, height / 2f, halfMarkerSize - 1, markerBorderPaint)
                
                // Draw marker highlight
                markerHighlightPaint.color = Color.WHITE
                markerHighlightPaint.alpha = 80 // Reduced opacity for more transparency
                canvas.drawCircle(markerPosition - halfMarkerSize / 3, height / 2f - halfMarkerSize / 3, halfMarkerSize / 4, markerHighlightPaint)
            }
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        
        // 创建图标 ImageView
        leftIcon = ImageView(context).apply {
            visibility = GONE
        }
        rightIcon = ImageView(context).apply {
            visibility = GONE
        }
        
        // 创建时间文本视图
        leftTime = AppCompatTextView(context).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setTextColor(Colors.text)
            textSize = 14f
            setPadding(0, 0, 8, 0)
        }
        
        rightTime = AppCompatTextView(context).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextColor(Colors.text)
            textSize = 14f
            setPadding(8, 0, 0, 0)
        }
        
        // 创建进度条
        progressView = ProgressView(context)
        
        // 添加视图到布局：图标 + 时间 + 进度条 + 时间 + 图标
        addView(leftIcon, LinearLayout.LayoutParams(60, 60).apply {
            gravity = Gravity.CENTER_VERTICAL
        })
        addView(leftTime, LinearLayout.LayoutParams(180, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.CENTER_VERTICAL
        })
        addView(progressView, LinearLayout.LayoutParams(0, BAR_HEIGHT, 1f).apply {
            gravity = Gravity.CENTER_VERTICAL
        })
        addView(rightTime, LinearLayout.LayoutParams(180, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.CENTER_VERTICAL
        })
        addView(rightIcon, LinearLayout.LayoutParams(60, 60).apply {
            gravity = Gravity.CENTER_VERTICAL
        })
    }

    var textVisibility = View.VISIBLE
        set(value) {
            field = value
            if (value != View.VISIBLE) {
                leftTime.text = ""
                rightTime.text = ""
                leftTime.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
                rightTime.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
            } else {
                leftTime.layoutParams = LinearLayout.LayoutParams(180, ViewGroup.LayoutParams.MATCH_PARENT)
                rightTime.layoutParams = LinearLayout.LayoutParams(180, ViewGroup.LayoutParams.MATCH_PARENT)
                updateDisplay()
            }
        }

    fun getProgress(): Int {
        return current
    }

    fun getMax(): Int {
        return max
    }

    fun setLeftText(text: String) {
        leftTime.text = text
    }

    fun getLeftText(): String {
        return leftTime.text.toString()
    }

    fun setLeftIcon(drawable: Drawable) {
        leftIcon.setImageDrawable(drawable)
        leftIcon.visibility = VISIBLE
    }

    fun setRightIcon(drawable: Drawable) {
        rightIcon.setImageDrawable(drawable)
        rightIcon.visibility = VISIBLE
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
        updateDisplay()
    }

    @SuppressLint("SetTextI18n")
    fun setMax(duration: Int) {
        if (duration in 1 until 1000 * 60 * 720) {
            this.max = duration
        } else {
            this.max = 1
        }
        updateDisplay()
    }

    fun setCurrent(cur: Int) {
        if (cur in 0 until 1000 * 60 * 720) {
            this.current = cur
        } else {
            this.current = 0
        }
        updateDisplay()
    }

    fun getCurrent(): Int {
        return current
    }

    fun setSeekMode(seekMode: Boolean) {
        progressView.seekMode = seekMode
        progressView.invalidate()
    }

    private fun updateDisplay() {
        progressView.invalidate()
        if (textVisibility == View.VISIBLE) {
            leftTime.text = toMinute(current)
            rightTime.text = toMinute(max)
        }
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
