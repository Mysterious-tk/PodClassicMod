package com.example.podclassic.util

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

/**
 * Produces a restrained player background from album artwork and animates song changes.
 * Color extraction is intentionally separate from [apply] so callers can do it off the UI thread.
 */
class ArtworkBackgroundController(
    private val target: View,
    private val baseColor: Int,
    private val artworkStrength: Float
) {
    data class Scheme(val top: Int, val bottom: Int)

    private var current = Scheme(baseColor, baseColor)
    private var animator: ValueAnimator? = null

    fun extract(bitmap: Bitmap): Scheme {
        val artworkHeight = bitmap.width.coerceAtMost(bitmap.height)
        val palette = Palette.from(bitmap)
            // Cached player artwork can include a reflection; only sample the square cover.
            .setRegion(0, 0, bitmap.width, artworkHeight)
            .resizeBitmapArea(112 * 112)
            .maximumColorCount(12)
            .generate()

        val sampledColor = palette.mutedSwatch?.rgb
            ?: palette.lightMutedSwatch?.rgb
            ?: palette.vibrantSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: baseColor

        val softenedColor = soften(sampledColor)
        return Scheme(
            top = ColorUtils.blendARGB(baseColor, softenedColor, artworkStrength),
            bottom = ColorUtils.blendARGB(baseColor, softenedColor, artworkStrength * 0.42f)
        )
    }

    fun apply(scheme: Scheme?) {
        val destination = scheme ?: Scheme(baseColor, baseColor)
        animator?.cancel()

        val start = current
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450L
            val evaluator = ArgbEvaluator()
            addUpdateListener { animation ->
                val fraction = animation.animatedFraction
                current = Scheme(
                    evaluator.evaluate(fraction, start.top, destination.top) as Int,
                    evaluator.evaluate(fraction, start.bottom, destination.bottom) as Int
                )
                target.background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(current.top, current.bottom)
                )
            }
            start()
        }
    }

    fun cancel() {
        animator?.cancel()
        animator = null
    }

    private fun soften(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] *= 0.68f
        return Color.HSVToColor(Color.alpha(color), hsv)
    }
}
