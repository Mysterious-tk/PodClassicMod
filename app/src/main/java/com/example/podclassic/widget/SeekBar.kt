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
            // Draw background
            if (backPaint.shader == null) {
                backPaint.shader = Colors.getShader(
                    0f,
                    0f,
                    0f,
                    height.toFloat(),
                    Colors.background_dark_1,
                    Colors.background_dark_2
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backPaint)
            
            // Draw progress bar with Mac OS X style
            if (paint.shader == null) {
                // Create a blue color for Mac OS X style
                val blueColor = Color.parseColor("#4A90E2")
                
                // Create a linear gradient shader with blue color
                paint.shader = LinearGradient(
                    0f,
                    0f,
                    100f,
                    0f,
                    intArrayOf(blueColor, blueColor, Color.WHITE, blueColor, blueColor),
                    floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1.0f),
                    Shader.TileMode.REPEAT
                )
            }
            if (seekMode) {
                // Calculate marker position based on current progress
                val markerPosition = width * current / max.toFloat()
                
                // Draw a diamond-shaped marker at the current progress position
                val markerSize = height * 2
                val halfMarkerSize = markerSize / 2
                
                // Save canvas state
                canvas?.save()
                
                // Translate to marker position
                canvas?.translate(markerPosition, height / 2f)
                
                // Rotate canvas to draw diamond
                canvas?.rotate(45f)
                
                // Draw diamond
                rect.set(-halfMarkerSize.toFloat(), -halfMarkerSize.toFloat(), halfMarkerSize.toFloat(), halfMarkerSize.toFloat())
                canvas?.drawRect(rect, paint)
                
                // Restore canvas state
                canvas?.restore()
            } else {
                // Draw progress bar with Mac OS X style including glass highlight
                val progressWidth = width * current / max.toFloat()
                
                // Draw main progress bar
                rect.set(0f, 0f, progressWidth, height.toFloat())
                canvas?.drawRect(rect, paint)
                
                // Add glass highlight effect
                val highlightPaint = Paint()
                highlightPaint.color = Color.WHITE
                highlightPaint.alpha = 60
                
                // Draw diagonal highlight
                val highlightPath = Path()
                val heightFloat = height.toFloat()
                highlightPath.moveTo(0f, 0f)
                highlightPath.lineTo(progressWidth + heightFloat, -heightFloat)
                highlightPath.lineTo(progressWidth + heightFloat, heightFloat)
                highlightPath.lineTo(0f, 2 * heightFloat)
                highlightPath.close()
                canvas?.drawPath(highlightPath, highlightPaint)
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