package com.example.podclassic.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
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


class SeekBar(context: Context) : LinearLayout(context) {

    companion object {
        private val BAR_HEIGHT = Values.DEFAULT_PADDING
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
        private val density = resources.displayMetrics.density
        private val trackRect = RectF()
        private val progressRect = RectF()
        private val highlightRect = RectF()
        var seekMode = false

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val trackGlossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val trackEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density
        }
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val progressGlossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val progressEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density
        }
        private val markerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val markerGlossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val markerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density
        }

        override fun onDraw(canvas: Canvas) {
            if (width <= 0 || height <= 0) return

            val viewHeight = height.toFloat()
            val trackInset = density.coerceAtMost(viewHeight * 0.08f)
            trackRect.set(trackInset, trackInset, width - trackInset, viewHeight - trackInset)
            val trackRadius = trackRect.height() / 2f
            val progressFraction = (current / max.toFloat()).coerceIn(0f, 1f)
            val progressX = trackRect.left + trackRect.width() * progressFraction

            // A transparent smoked-glass channel. Its bright upper rim and darker
            // lower edge create thickness without hiding the surface underneath.
            trackPaint.shader = LinearGradient(
                0f,
                trackRect.top,
                0f,
                trackRect.bottom,
                intArrayOf(
                    Color.argb(54, 244, 252, 255),
                    Color.argb(66, 44, 66, 88),
                    Color.argb(92, 8, 20, 38)
                ),
                floatArrayOf(0f, 0.42f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(trackRect, trackRadius, trackRadius, trackPaint)

            highlightRect.set(
                trackRect.left + density,
                trackRect.top + density * 0.55f,
                trackRect.right - density,
                trackRect.top + trackRect.height() * 0.43f
            )
            trackGlossPaint.shader = LinearGradient(
                0f,
                highlightRect.top,
                0f,
                highlightRect.bottom,
                Color.argb(72, 255, 255, 255),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(highlightRect, trackRadius, trackRadius, trackGlossPaint)

            trackEdgePaint.color = Color.argb(92, 221, 245, 255)
            canvas.drawRoundRect(trackRect, trackRadius, trackRadius, trackEdgePaint)

            if (progressX > trackRect.left) {
                progressRect.set(trackRect.left, trackRect.top, progressX, trackRect.bottom)
                progressPaint.shader = LinearGradient(
                    progressRect.left,
                    progressRect.top,
                    progressRect.right,
                    progressRect.bottom,
                    intArrayOf(
                        Color.argb(116, 57, 182, 255),
                        Color.argb(92, 20, 133, 238),
                        Color.argb(126, 0, 75, 183)
                    ),
                    floatArrayOf(0f, 0.58f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(progressRect, trackRadius, trackRadius, progressPaint)

                if (progressRect.width() > density * 2f) {
                    highlightRect.set(
                        progressRect.left + density,
                        progressRect.top + density * 0.55f,
                        progressRect.right - density,
                        progressRect.top + progressRect.height() * 0.48f
                    )
                    progressGlossPaint.shader = LinearGradient(
                        0f,
                        highlightRect.top,
                        0f,
                        highlightRect.bottom,
                        Color.argb(110, 255, 255, 255),
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRoundRect(highlightRect, trackRadius, trackRadius, progressGlossPaint)
                }

                progressEdgePaint.color = Color.argb(126, 210, 246, 255)
                canvas.drawRoundRect(progressRect, trackRadius, trackRadius, progressEdgePaint)
            }

            if (seekMode) {
                // Keep the scrubber entirely inside its canvas at both endpoints.
                // The old 1.5x-height circle was clipped into a flat-looking disc.
                val markerRadius = viewHeight * 0.44f
                val markerX = if (width >= markerRadius * 2f) {
                    progressX.coerceIn(markerRadius, width - markerRadius)
                } else {
                    width / 2f
                }
                val markerY = viewHeight / 2f

                markerShadowPaint.color = Color.argb(70, 0, 28, 72)
                canvas.drawCircle(markerX, markerY + density, markerRadius, markerShadowPaint)

                markerPaint.shader = RadialGradient(
                    markerX - markerRadius * 0.28f,
                    markerY - markerRadius * 0.32f,
                    markerRadius * 1.35f,
                    intArrayOf(
                        Color.argb(178, 146, 231, 255),
                        Color.argb(138, 30, 148, 240),
                        Color.argb(172, 0, 67, 168)
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(markerX, markerY, markerRadius, markerPaint)

                markerGlossPaint.shader = LinearGradient(
                    0f,
                    markerY - markerRadius,
                    0f,
                    markerY + markerRadius * 0.25f,
                    Color.argb(150, 255, 255, 255),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                canvas.drawOval(
                    markerX - markerRadius * 0.64f,
                    markerY - markerRadius * 0.72f,
                    markerX + markerRadius * 0.64f,
                    markerY + markerRadius * 0.1f,
                    markerGlossPaint
                )

                markerBorderPaint.color = Color.argb(210, 226, 250, 255)
                val outerRimRadius = (markerRadius - density * 0.5f)
                    .coerceAtLeast(markerRadius * 0.78f)
                canvas.drawCircle(markerX, markerY, outerRimRadius, markerBorderPaint)
                markerBorderPaint.color = Color.argb(86, 0, 48, 124)
                val innerRimRadius = (markerRadius - density * 1.5f)
                    .coerceAtLeast(markerRadius * 0.56f)
                canvas.drawCircle(markerX, markerY, innerRimRadius, markerBorderPaint)
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
