package com.example.podclassic.widget

import android.graphics.Color

/**
 * Visual-only tokens for the black/red iPod 3rd skin.
 *
 * Geometry and hit targets remain owned by [SlideController3rd]; this object
 * centralizes material intensity so the physical-device finish can be tuned
 * without touching interaction code.
 */
object IPod3rdSkinTokens {
    const val ICON_SCALE = 0.72f

    const val WHEEL_GROOVE_DP = 5.8f
    const val WHEEL_MATERIAL_ALPHA = 92
    const val WHEEL_CLEAR_COAT_ALPHA = 27
    const val WHEEL_PRESSED_GLOW_ALPHA = 74
    const val WHEEL_TEXTURE_OVERSCAN = 1.32f

    const val BUTTON_RIM_DP = 2.8f
    const val BUTTON_PRESSED_GLOW_ALPHA = 86

    const val LCD_PIXEL_ALPHA = 26
    const val LCD_REFLECTION_ALPHA = 12

    val wheelUnderlayTop = Color.rgb(91, 0, 17)
    val wheelUnderlayMiddle = Color.rgb(160, 0, 29)
    val wheelUnderlayBottom = Color.rgb(202, 4, 42)
    val pressedGlow = Color.rgb(255, 70, 35)
}
