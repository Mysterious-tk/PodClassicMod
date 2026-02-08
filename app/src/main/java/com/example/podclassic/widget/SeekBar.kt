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

    private var max = 1
    private var current = 0

    // Custom ProgressView class to access seekMode property
    private inner class ProgressView(context: Context) : View(context) {
        val paint = Paint().apply { shader = null }
        val backPaint = Paint().apply { shader = null }
        val rect = RectF()
        var seekMode = false

        override fun onDraw(canvas: Canvas) {
            // Draw background with glassmorphism effect
            val backgroundColor = Color.parseColor("#202020")
            backPaint.color = backgroundColor
            backPaint.alpha = 150 // Reduced opacity for more transparency
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), height.toFloat() / 2, height.toFloat() / 2, backPaint)
            
            // Add subtle border to background
            val borderPaint = Paint()
            borderPaint.color = Color.WHITE
            borderPaint.alpha = 20 // Reduced opacity for more transparency
            borderPaint.style = Paint.Style.STROKE
            borderPaint.strokeWidth = 1f
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), height.toFloat() / 2, height.toFloat() / 2, borderPaint)
            
            // Draw progress bar with glassmorphism effect
            val progressWidth = width * current / max.toFloat()
            
            if (progressWidth > 0) {
                // Create blue glassmorphism effect for progress
                val glassPaint = Paint()
                val blueColor = Color.parseColor("#4A90D9")
                glassPaint.color = blueColor
                glassPaint.alpha = 140 // Reduced opacity for more transparency
                
                // Draw main progress bar
                rect.set(0f, 0f, progressWidth, height.toFloat())
                canvas.drawRoundRect(rect, height.toFloat() / 2, height.toFloat() / 2, glassPaint)
                
                // Add inner border to progress bar
                val progressBorderPaint = Paint()
                progressBorderPaint.color = Color.WHITE
                progressBorderPaint.alpha = 40 // Reduced opacity for more transparency
                progressBorderPaint.style = Paint.Style.STROKE
                progressBorderPaint.strokeWidth = 1f
                canvas.drawRoundRect(rect, height.toFloat() / 2, height.toFloat() / 2, progressBorderPaint)
                
                // Add glass highlight effect
                val highlightPaint = Paint()
                highlightPaint.color = Color.WHITE
                highlightPaint.alpha = 60 // Reduced opacity for more transparency
                
                // Draw top highlight
                val highlightRect = RectF(0f, 0f, progressWidth, height.toFloat() / 2)
                canvas.drawRoundRect(highlightRect, height.toFloat() / 2, height.toFloat() / 2, highlightPaint)
                
                // Draw subtle gradient overlay for depth
                val gradientPaint = Paint()
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
                val markerPaint = Paint()
                markerPaint.color = Color.parseColor("#4A90D9")
                markerPaint.alpha = 160 // Reduced opacity for more transparency
                canvas.drawCircle(markerPosition, height / 2f, halfMarkerSize, markerPaint)
                
                // Draw marker border
                val markerBorderPaint = Paint()
                markerBorderPaint.color = Color.WHITE
                markerBorderPaint.alpha = 120 // Reduced opacity for more transparency
                markerBorderPaint.style = Paint.Style.STROKE
                markerBorderPaint.strokeWidth = 2f
                canvas.drawCircle(markerPosition, height / 2f, halfMarkerSize - 1, markerBorderPaint)
                
                // Draw marker highlight
                val markerHighlightPaint = Paint()
                markerHighlightPaint.color = Color.WHITE
                markerHighlightPaint.alpha = 80 // Reduced opacity for more transparency
                canvas.drawCircle(markerPosition - halfMarkerSize / 3, height / 2f - halfMarkerSize / 3, halfMarkerSize / 4, markerHighlightPaint)
            }
        }
    }

    init {
        orientation = HORIZONTAL
        
        // Create time text views using AppCompatTextView
        leftTime = AppCompatTextView(context).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setTextColor(Colors.text)
            textSize = 14f
            setPadding(0, 0, 8, 0) // Remove top padding to align with progress bar
        }
        
        rightTime = AppCompatTextView(context).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextColor(Colors.text)
            textSize = 14f
            setPadding(8, 0, 0, 0) // Remove top padding to align with progress bar
        }
        
        // Create progress bar
        progressView = ProgressView(context)
        
        // Add views to layout
        addView(leftTime)
        addView(progressView)
        addView(rightTime)
        
        // Set layout parameters
        leftTime.layoutParams = LinearLayout.LayoutParams(180, ViewGroup.LayoutParams.MATCH_PARENT)
        
        // Create layout params for progress bar with vertical gravity
        val progressParams = LinearLayout.LayoutParams(0, BAR_HEIGHT / 2, 1f)
        progressParams.gravity = Gravity.CENTER_VERTICAL // Ensure vertical alignment
        progressView.layoutParams = progressParams
        
        rightTime.layoutParams = LinearLayout.LayoutParams(180, ViewGroup.LayoutParams.MATCH_PARENT)
        
        // Add top margin to the entire SeekBar to move everything down
        val layoutParams = this.layoutParams as? ViewGroup.MarginLayoutParams
        if (layoutParams != null) {
            layoutParams.topMargin = 20 // Move the entire SeekBar down
            this.layoutParams = layoutParams
        }
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
        leftTime.setCompoundDrawables(drawable, null, null, null)
    }

    fun setRightIcon(drawable: Drawable) {
        rightTime.setCompoundDrawables(null, null, drawable, null)
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