package com.example.podclassic.widget

import android.graphics.BitmapShader
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.content.res.Resources
import com.example.podclassic.R

/**
 * Warm injection-moulded polycarbonate used by the separate "iPod 3G Classic" skin.
 *
 * The original iPod 3rd theme deliberately keeps its existing liquid-glass treatment.
 * This drawable is only installed for the new classic skin.
 */
class IPod3GClassicShellDrawable(resources: Resources) : Drawable() {

    private val density = resources.displayMetrics.density
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val scatterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
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
    }
    private val rect = RectF()
    private var bodyShader: Shader? = null
    private var scatterShader: Shader? = null
    private var bottomShader: Shader? = null
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

        // Layer 1: a restrained warm-white body. It stays opaque enough to
        // conceal the app below, while later layers create optical depth.
        bodyShader = LinearGradient(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat(),
            intArrayOf(
                Color.rgb(253, 252, 247),
                Color.rgb(244, 243, 236),
                Color.rgb(231, 230, 221)
            ),
            floatArrayOf(0f, 0.57f, 1f),
            Shader.TileMode.CLAMP
        )
        scatterShader = RadialGradient(
            bounds.left + width * 0.47f,
            bounds.top + height * 0.39f,
            maxOf(width, height) * 0.73f,
            intArrayOf(
                Color.argb(112, 255, 255, 252),
                Color.argb(68, 252, 250, 242),
                Color.argb(18, 211, 209, 198)
            ),
            floatArrayOf(0f, 0.61f, 1f),
            Shader.TileMode.CLAMP
        )
        bottomShader = LinearGradient(
            0f,
            bounds.top + height * 0.72f,
            0f,
            bounds.bottom.toFloat(),
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(8, 112, 108, 96),
                Color.argb(24, 100, 96, 84)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        edgeShader = LinearGradient(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat(),
            intArrayOf(
                Color.argb(224, 255, 255, 253),
                Color.argb(92, 216, 214, 204),
                Color.argb(210, 255, 255, 252)
            ),
            floatArrayOf(0f, 0.48f, 1f),
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

        lightPaint.alpha = drawableAlpha
        lightPaint.shader = bottomShader
        canvas.drawRoundRect(rect, radius, radius, lightPaint)
        lightPaint.shader = null

        // Layer 2: the transparent acrylic cover contains only edge transport,
        // a soft top reflection, and microscopic density variation.
        overlayPaint.alpha = drawableAlpha
        canvas.drawRoundRect(rect, radius, radius, overlayPaint)

        val inset = 0.8f * density
        edgePaint.alpha = drawableAlpha
        edgePaint.strokeWidth = 1.35f * density
        edgePaint.shader = edgeShader
        canvas.drawRoundRect(
            rect.left + inset,
            rect.top + inset,
            rect.right - inset,
            rect.bottom - inset,
            radius - inset,
            radius - inset,
            edgePaint
        )
        edgePaint.shader = null

        // The second inner line is the refracted light carried by the rounded
        // polycarbonate edge, not a glass outline.
        val innerInset = 3.1f * density
        edgePaint.strokeWidth = 0.75f * density
        edgePaint.color = Color.argb(118, 255, 255, 251)
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
        lightPaint.colorFilter = colorFilter
        overlayPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
