package com.example.podclassic.widget

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import com.example.podclassic.R

/**
 * Black/red translucent polycarbonate enclosure for the original iPod 3rd theme.
 * It keeps the theme opaque and black while adding subsurface red scatter,
 * rounded-edge light transport and the same separate acrylic surface layer used
 * by the warm-white 3G Classic skin.
 */
class IPod3rdBlackRedShellDrawable(resources: Resources) : Drawable() {

    private val density = resources.displayMetrics.density
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val scatterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val reflectionPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val overlayBitmap = BitmapFactory.decodeResource(
        resources,
        R.drawable.plastic_overlay
    )
    private val overlayShader = BitmapShader(
        overlayBitmap,
        Shader.TileMode.CLAMP,
        Shader.TileMode.CLAMP
    )
    private val overlayMatrix = Matrix()
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        shader = overlayShader
        alpha = 42
        colorFilter = PorterDuffColorFilter(Color.rgb(74, 63, 68), PorterDuff.Mode.MULTIPLY)
    }
    private val rect = RectF()
    private var bodyShader: Shader? = null
    private var scatterShader: Shader? = null
    private var reflectionShader: Shader? = null
    private var edgeShader: Shader? = null
    private var drawableAlpha = 255

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        if (bounds.isEmpty) return

        rect.set(bounds)
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()

        overlayMatrix.reset()
        overlayMatrix.setScale(
            width / overlayBitmap.width.toFloat(),
            height / overlayBitmap.height.toFloat()
        )
        overlayMatrix.postTranslate(bounds.left.toFloat(), bounds.top.toFloat())
        overlayShader.setLocalMatrix(overlayMatrix)

        bodyShader = LinearGradient(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat(),
            intArrayOf(
                Color.rgb(16, 17, 21),
                Color.rgb(7, 8, 11),
                Color.rgb(2, 2, 4)
            ),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
        scatterShader = RadialGradient(
            bounds.left + width * 0.52f,
            bounds.top + height * 0.44f,
            maxOf(width, height) * 0.72f,
            intArrayOf(
                Color.argb(17, 147, 8, 31),
                Color.argb(6, 103, 3, 20),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
        reflectionShader = LinearGradient(
            0f,
            bounds.top.toFloat(),
            0f,
            bounds.top + height * 0.38f,
            intArrayOf(
                Color.argb(28, 218, 224, 234),
                Color.argb(8, 124, 130, 143),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP
        )
        edgeShader = LinearGradient(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat(),
            intArrayOf(
                Color.argb(148, 132, 139, 151),
                Color.argb(74, 42, 8, 16),
                Color.argb(164, 185, 20, 43)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val radius = 29f * density

        bodyPaint.alpha = drawableAlpha
        bodyPaint.shader = bodyShader
        canvas.drawRoundRect(rect, radius, radius, bodyPaint)

        scatterPaint.alpha = drawableAlpha
        scatterPaint.shader = scatterShader
        canvas.drawRoundRect(rect, radius, radius, scatterPaint)

        reflectionPaint.alpha = drawableAlpha
        reflectionPaint.shader = reflectionShader
        canvas.drawRoundRect(rect, radius, radius, reflectionPaint)

        overlayPaint.alpha = (drawableAlpha * 0.16f).toInt()
        canvas.drawRoundRect(rect, radius, radius, overlayPaint)

        val outerInset = 0.8f * density
        edgePaint.alpha = drawableAlpha
        edgePaint.strokeWidth = 1.3f * density
        edgePaint.shader = edgeShader
        canvas.drawRoundRect(
            rect.left + outerInset,
            rect.top + outerInset,
            rect.right - outerInset,
            rect.bottom - outerInset,
            radius - outerInset,
            radius - outerInset,
            edgePaint
        )
        edgePaint.shader = null

        val innerInset = 3.1f * density
        edgePaint.strokeWidth = 0.7f * density
        edgePaint.color = Color.argb(70, 183, 35, 56)
        canvas.drawRoundRect(
            rect.left + innerInset,
            rect.top + innerInset,
            rect.right - innerInset,
            rect.bottom - innerInset,
            radius - innerInset,
            radius - innerInset,
            edgePaint
        )
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bodyPaint.colorFilter = colorFilter
        scatterPaint.colorFilter = colorFilter
        reflectionPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
