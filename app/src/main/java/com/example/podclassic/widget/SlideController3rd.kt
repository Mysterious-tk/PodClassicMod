package com.example.podclassic.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.podclassic.R
import com.example.podclassic.util.ThreadUtil
import com.example.podclassic.util.VolumeUtil
import com.example.podclassic.values.Icons
import java.util.*
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

class SlideController3rd : View {

    companion object {
        private const val SLIDE_VAL = 18
        private const val WHEEL_DIAMETER_DP = 217f
        private const val CLASSIC_WHEEL_DIAMETER_DP = 248f
        private const val WHEEL_BOTTOM_MARGIN_DP = 64f
        private const val CONTROL_BUTTON_DIAMETER_DP = 56f
        private const val CLASSIC_CONTROL_BUTTON_DIAMETER_DP = 68f
        private const val CLASSIC_CONTROL_WHEEL_GAP_DP = 12f
        private const val BACKLIGHT_DURATION_MS = 2800L
        private const val BACKLIGHT_HOLD_FRACTION = 0.72f

    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attributeSet: AttributeSet?) : super(context, attributeSet)

    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = PorterDuffColorFilter(
            Color.argb(224, 242, 244, 248),
            PorterDuff.Mode.SRC_IN
        )
    }
    private val iconBacklightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val materialPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val wheelMaterialBitmap = BitmapFactory.decodeResource(
        resources,
        R.drawable.ipod3rd_wheel_red_material
    )
    private val wheelClearCoatBitmap = BitmapFactory.decodeResource(
        resources,
        R.drawable.ipod3rd_wheel_glass_highlight
    )
    private val acrylicNoisePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        shader = BitmapShader(
            createAcrylicNoiseTile(),
            Shader.TileMode.REPEAT,
            Shader.TileMode.REPEAT
        )
    }
    private var wheelBaseShader: Shader? = null
    private var wheelReflectionShader: Shader? = null
    private var wheelDepthShader: Shader? = null
    private var wheelEdgeShader: Shader? = null
    private var centerX = 0f
    private var centerY = 0f
    private var minR = 0f
    private var maxR = 0f
    private var buttonRadius = 0f
    private var density = 1f
    private var buttonBacklightStartedAt = 0L
    private var pressedButtonIndex = -1
    private var centerButtonPressed = false
    private var wheelPressed = false

    var enable = true
        set(value) {
            if (!value) {
                cancelTimer()
                buttonBacklightStartedAt = 0L
                pressedButtonIndex = -1
                centerButtonPressed = false
                wheelPressed = false
                invalidate()
            }
            field = value
        }

    var colorController: Int = Color.RED
    var colorButton: Int = Color.BLACK

    /**
     * The existing iPod 3rd skin keeps its liquid-glass finish. A second controller
     * instance enables this flag for the separate, historically grounded 3G skin.
     */
    var classicMaterial: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            updateGlassShaders()
            requestLayout()
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val x = MeasureSpec.getSize(widthMeasureSpec)
        val y = MeasureSpec.getSize(heightMeasureSpec)
        centerX = x / 2f
        density = resources.displayMetrics.density
        val buttonDiameter =
            if (classicMaterial) CLASSIC_CONTROL_BUTTON_DIAMETER_DP
            else CONTROL_BUTTON_DIAMETER_DP
        buttonRadius = buttonDiameter * density / 2f

        val wheelDiameter =
            if (classicMaterial) CLASSIC_WHEEL_DIAMETER_DP else WHEEL_DIAMETER_DP
        val requestedRadius = wheelDiameter * density / 2f
        val bottomMargin = WHEEL_BOTTOM_MARGIN_DP * density
        val controlGap =
            if (classicMaterial) CLASSIC_CONTROL_WHEEL_GAP_DP * density else 0f
        val heightLimitedRadius = if (classicMaterial) {
            // buttonRowY is 0.5R and the wheel occupies another 2R below its
            // top edge, so reserve 2.5R + button radius + the visible gap.
            ((y - bottomMargin - buttonRadius - controlGap) / 2.5f)
                .coerceAtLeast(0f)
        } else {
            (y - bottomMargin).coerceAtLeast(0f) / 2f
        }

        // 3G 转盘保持 217dp 直径，以 64dp 底边距为顶部按钮留出更多呼吸空间。
        // 极窄或极矮的窗口中才缩小，避免转盘被裁切。
        maxR = min(
            requestedRadius,
            min(centerX, heightLimitedRadius)
        )
        val bottomAnchoredCenter = (y - bottomMargin - maxR).coerceAtLeast(maxR)
        val minimumSeparatedCenter = if (classicMaterial) {
            maxR * 1.5f + buttonRadius + controlGap
        } else {
            maxR
        }
        centerY = maxOf(bottomAnchoredCenter, minimumSeparatedCenter)
            .coerceAtMost((y - maxR).coerceAtLeast(maxR))

        minR = if (classicMaterial) maxR * 0.358f else maxR / 16 * 5

        updateGlassShaders()

    }

    private fun updateGlassShaders() {
        if (maxR <= 0f) {
            wheelBaseShader = null
            wheelReflectionShader = null
            wheelDepthShader = null
            wheelEdgeShader = null
            return
        }

        wheelBaseShader = LinearGradient(
            centerX - maxR,
            centerY - maxR,
            centerX + maxR,
            centerY + maxR,
            intArrayOf(
                Color.argb(218, 93, 0, 13),
                Color.argb(204, 164, 0, 23),
                Color.argb(194, 238, 29, 48)
            ),
            floatArrayOf(0f, 0.56f, 1f),
            Shader.TileMode.CLAMP
        )
        wheelReflectionShader = LinearGradient(
            centerX,
            centerY - maxR,
            centerX,
            centerY + maxR,
            intArrayOf(
                Color.argb(54, 25, 0, 4),
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                Color.argb(44, 255, 160, 171)
            ),
            floatArrayOf(0f, 0.32f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        wheelDepthShader = RadialGradient(
            centerX,
            centerY,
            maxR,
            intArrayOf(
                Color.argb(18, 255, 88, 105),
                Color.TRANSPARENT,
                Color.argb(82, 35, 0, 6)
            ),
            floatArrayOf(0f, 0.68f, 1f),
            Shader.TileMode.CLAMP
        )
        wheelEdgeShader = LinearGradient(
            centerX,
            centerY - maxR,
            centerX,
            centerY + maxR,
            intArrayOf(
                Color.argb(230, 255, 116, 130),
                Color.argb(218, 177, 0, 25),
                Color.argb(238, 43, 0, 7)
            ),
            floatArrayOf(0f, 0.46f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 横屏全屏页面会暂时把控制器高度设为 0。配置切换期间 View 仍可能
        // 收到一次绘制回调，此时 RadialGradient 不接受 0 半径。
        if (maxR <= 0f || minR <= 0f || width <= 0 || height <= 0) return

        if (classicMaterial) {
            drawClassicTouchWheel(canvas)
            drawClassicCenterButton(canvas)
        } else {
            drawLiquidGlassWheel(canvas)
            drawRecessedCenterButton(canvas)
        }

        // 按钮位置调整：在屏幕顶部，形成一行
        val buttonRowY = maxR * 0.5f // 按钮行位于屏幕顶部，距离圆盘顶部一定距离
        val button1X = buttonCenterX(0)
        val button2X = buttonCenterX(1)
        val button3X = buttonCenterX(2)
        val button4X = buttonCenterX(3)

        if (classicMaterial) {
            drawClassicTouchButton(canvas, button1X, buttonRowY, 0)
            drawClassicTouchButton(canvas, button2X, buttonRowY, 1)
            drawClassicTouchButton(canvas, button3X, buttonRowY, 2)
            drawClassicTouchButton(canvas, button4X, buttonRowY, 3)
        } else {
            drawGlassButton(canvas, button1X, buttonRowY)
            drawGlassButton(canvas, button2X, buttonRowY)
            drawGlassButton(canvas, button3X, buttonRowY)
            drawGlassButton(canvas, button4X, buttonRowY)
        }

        // Keep the existing assets and exact centers; only their finish changes.
        drawBacklitIcon(canvas, Icons.PREV.bitmap, button1X, buttonRowY)
        drawBacklitIcon(canvas, Icons.MENU.bitmap, button2X, buttonRowY)
        drawBacklitIcon(canvas, Icons.PAUSE.bitmap, button3X, buttonRowY)
        drawBacklitIcon(canvas, Icons.NEXT.bitmap, button4X, buttonRowY)

        if (currentBacklightIntensity() > 0f) {
            postInvalidateOnAnimation()
        }

    }

    private fun startButtonBacklight() {
        buttonBacklightStartedAt = SystemClock.uptimeMillis().coerceAtLeast(1L)
        postInvalidateOnAnimation()
    }

    private fun buttonCenterX(index: Int): Float {
        return if (classicMaterial) {
            // Reference positions: 1/6, 7/18, 11/18 and 5/6 of the body width.
            width * (1f / 6f + index * 2f / 9f)
        } else {
            width * (index + 1) / 5f
        }
    }

    private fun drawBacklitIcon(
        canvas: Canvas,
        bitmap: Bitmap,
        centerX: Float,
        centerY: Float
    ) {
        if (classicMaterial) {
            drawClassicBacklitIcon(canvas, bitmap, centerX, centerY)
            return
        }

        val iconScale = IPod3rdSkinTokens.ICON_SCALE
        val iconWidth = bitmap.width * iconScale
        val iconHeight = bitmap.height * iconScale
        val destination = RectF(
            centerX - iconWidth / 2f,
            centerY - iconHeight / 2f,
            centerX + iconWidth / 2f,
            centerY + iconHeight / 2f
        )
        val intensity = currentBacklightIntensity()
        if (intensity <= 0f) {
            canvas.drawBitmap(bitmap, null, destination, iconPaint)
            return
        }

        // Keep the feedback confined to the crisp symbol; the glass stays unchanged.
        val red = 242 + (13f * intensity).toInt()
        val green = 244 - (82f * intensity).toInt()
        val blue = 248 - (210f * intensity).toInt()
        iconBacklightPaint.alpha = 255
        iconBacklightPaint.maskFilter = null
        iconBacklightPaint.colorFilter = PorterDuffColorFilter(
            Color.rgb(red, green, blue),
            PorterDuff.Mode.SRC_IN
        )
        canvas.drawBitmap(bitmap, null, destination, iconBacklightPaint)
        iconBacklightPaint.colorFilter = null
    }

    private fun drawClassicBacklitIcon(
        canvas: Canvas,
        bitmap: Bitmap,
        centerX: Float,
        centerY: Float
    ) {
        val intensity = currentBacklightIntensity()
        val iconScale = 0.75f
        val iconWidth = bitmap.width * iconScale
        val iconHeight = bitmap.height * iconScale
        val destination = RectF(
            centerX - iconWidth / 2f,
            centerY - iconHeight / 2f,
            centerX + iconWidth / 2f,
            centerY + iconHeight / 2f
        )
        // The reference keeps the four capacitive legends orange-red even when idle.
        // Touch feedback only warms and brightens the printed symbol slightly.
        val red = (226 + 21f * intensity).toInt().coerceIn(0, 255)
        val green = (78 + 20f * intensity).toInt().coerceIn(0, 255)
        val blue = (39 + 14f * intensity).toInt().coerceIn(0, 255)
        iconBacklightPaint.alpha = (238 + 17f * intensity).toInt()
        iconBacklightPaint.colorFilter = PorterDuffColorFilter(
            Color.rgb(red, green, blue),
            PorterDuff.Mode.SRC_IN
        )
        canvas.drawBitmap(bitmap, null, destination, iconBacklightPaint)
        iconBacklightPaint.colorFilter = null
    }

    private fun currentBacklightIntensity(): Float {
        val startedAt = buttonBacklightStartedAt
        if (startedAt == 0L) return 0f

        val elapsed = SystemClock.uptimeMillis() - startedAt
        if (elapsed >= BACKLIGHT_DURATION_MS) return 0f

        val fraction = elapsed.toFloat() / BACKLIGHT_DURATION_MS
        return when {
            fraction < 0.06f -> 0.58f + fraction / 0.06f * 0.42f
            fraction < BACKLIGHT_HOLD_FRACTION -> {
                1f - (fraction - 0.06f) / (BACKLIGHT_HOLD_FRACTION - 0.06f) * 0.12f
            }
            else -> {
                val fade = (fraction - BACKLIGHT_HOLD_FRACTION) / (1f - BACKLIGHT_HOLD_FRACTION)
                0.88f * (1f - fade) * (1f - fade)
            }
        }
    }

    private fun createAcrylicNoiseTile(): Bitmap {
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        val random = Random(20031016L)
        for (index in pixels.indices) {
            val warmth = 238 + random.nextInt(14)
            pixels[index] = Color.argb(
                8 + random.nextInt(14),
                warmth,
                warmth,
                (warmth - 5).coerceAtLeast(0)
            )
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    private fun drawLiquidGlassWheel(canvas: Canvas) {
        val grooveWidth = IPod3rdSkinTokens.WHEEL_GROOVE_DP * density
        val surfaceRadius = (maxR - grooveWidth).coerceAtLeast(0f)

        // Dark red inner wall: the wheel is a cavity in the black shell, not a
        // glossy disc placed on top of it.
        glassPaint.style = Paint.Style.FILL
        glassPaint.shader = LinearGradient(
            centerX - maxR,
            centerY - maxR,
            centerX + maxR,
            centerY + maxR,
            intArrayOf(
                Color.argb(250, 20, 0, 5),
                Color.argb(244, 43, 0, 10),
                Color.argb(218, 151, 10, 34)
            ),
            floatArrayOf(0f, 0.54f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, maxR, glassPaint)

        // The code-drawn base carries the color and remains fully deterministic.
        // The reusable raster contributes only optical variation above it.
        glassPaint.shader = LinearGradient(
            centerX - surfaceRadius,
            centerY - surfaceRadius,
            centerX + surfaceRadius,
            centerY + surfaceRadius,
            intArrayOf(
                IPod3rdSkinTokens.wheelUnderlayTop,
                IPod3rdSkinTokens.wheelUnderlayMiddle,
                IPod3rdSkinTokens.wheelUnderlayBottom
            ),
            floatArrayOf(0f, 0.56f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, surfaceRadius, glassPaint)

        drawCircularMaterial(
            canvas,
            wheelMaterialBitmap,
            centerX,
            centerY,
            surfaceRadius,
            IPod3rdSkinTokens.WHEEL_MATERIAL_ALPHA,
            IPod3rdSkinTokens.WHEEL_TEXTURE_OVERSCAN
        )
        drawCircularMaterial(
            canvas,
            wheelClearCoatBitmap,
            centerX,
            centerY,
            surfaceRadius,
            IPod3rdSkinTokens.WHEEL_CLEAR_COAT_ALPHA,
            IPod3rdSkinTokens.WHEEL_TEXTURE_OVERSCAN
        )

        if (wheelPressed) {
            glassPaint.shader = RadialGradient(
                centerX,
                centerY,
                surfaceRadius,
                intArrayOf(
                    Color.argb(
                        IPod3rdSkinTokens.WHEEL_PRESSED_GLOW_ALPHA,
                        Color.red(IPod3rdSkinTokens.pressedGlow),
                        Color.green(IPod3rdSkinTokens.pressedGlow),
                        Color.blue(IPod3rdSkinTokens.pressedGlow)
                    ),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.28f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(centerX, centerY, surfaceRadius, glassPaint)
        }
        glassPaint.shader = null

        val rimBounds = RectF(
            centerX - maxR + density,
            centerY - maxR + density,
            centerX + maxR - density,
            centerY + maxR - density
        )
        edgePaint.shader = null
        edgePaint.strokeWidth = 1.05f * density
        edgePaint.color = Color.argb(150, 13, 0, 3)
        canvas.drawArc(rimBounds, 184f, 171f, false, edgePaint)
        edgePaint.color = Color.argb(78, 239, 50, 78)
        canvas.drawArc(rimBounds, 4f, 171f, false, edgePaint)

        val insetBounds = RectF(
            centerX - surfaceRadius,
            centerY - surfaceRadius,
            centerX + surfaceRadius,
            centerY + surfaceRadius
        )
        edgePaint.strokeWidth = 2.2f * density
        edgePaint.color = Color.argb(132, 31, 0, 7)
        canvas.drawArc(insetBounds, 184f, 171f, false, edgePaint)
        edgePaint.strokeWidth = 0.65f * density
        edgePaint.color = Color.argb(54, 248, 64, 91)
        canvas.drawArc(insetBounds, 4f, 171f, false, edgePaint)
    }

    private fun drawRecessedCenterButton(canvas: Canvas) {
        val lipWidth = 3f * density
        val press = if (centerButtonPressed) 1f else 0f
        glassPaint.style = Paint.Style.FILL

        // Concave cavity wall: the top half is occluded by the shell while the
        // lower-right edge receives a small amount of transmitted red light.
        glassPaint.shader = LinearGradient(
            centerX - minR,
            centerY - minR,
            centerX + minR,
            centerY + minR,
            intArrayOf(
                Color.rgb(0, 0, 2),
                Color.rgb(5, 5, 8),
                Color.rgb(62, 5, 17)
            ),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, minR + lipWidth, glassPaint)

        // A stable smoked floor with darkened edges reads as a touch well,
        // without a bright center or complete glossy rim.
        glassPaint.shader = RadialGradient(
            centerX,
            centerY,
            minR,
            intArrayOf(
                Color.rgb((15 - 3f * press).toInt(), (17 - 3f * press).toInt(), (23 - 3f * press).toInt()),
                Color.rgb((13 - 3f * press).toInt(), (15 - 3f * press).toInt(), (21 - 3f * press).toInt()),
                Color.rgb((4 - 2f * press).toInt(), (5 - 2f * press).toInt(), (8 - 2f * press).toInt())
            ),
            floatArrayOf(0f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, minR, glassPaint)
        glassPaint.shader = null

        val bounds = RectF(centerX - minR, centerY - minR, centerX + minR, centerY + minR)
        edgePaint.shader = null
        edgePaint.strokeWidth = 1.35f * density
        edgePaint.color = Color.argb((214 + 20f * press).toInt(), 0, 0, 1)
        canvas.drawArc(bounds, 184f, 171f, false, edgePaint)
        edgePaint.strokeWidth = 0.65f * density
        edgePaint.color = Color.argb((72 + 24f * press).toInt(), 218, 37, 62)
        canvas.drawArc(bounds, 4f, 171f, false, edgePaint)
    }

    private fun drawGlassButton(canvas: Canvas, x: Float, y: Float) {
        val index = ((x / width * 5f) - 1f).toInt().coerceIn(0, 3)
        val press = if (pressedButtonIndex == index) 1f else 0f
        val rimWidth = IPod3rdSkinTokens.BUTTON_RIM_DP * density
        val surfaceRadius = buttonRadius - rimWidth

        // Recessed cavity copied from the lighting logic of the accepted white
        // theme: dark upper wall, quiet lower/right transmission, no outer drop
        // shadow and no complete bright outline.
        glassPaint.shader = LinearGradient(
            x - buttonRadius,
            y - buttonRadius,
            x + buttonRadius,
            y + buttonRadius,
            intArrayOf(
                Color.rgb(0, 0, 2),
                Color.rgb(6, 6, 9),
                Color.rgb(
                    (52 + 18f * press).toInt(),
                    (5 + 3f * press).toInt(),
                    (15 + 5f * press).toInt()
                )
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, buttonRadius, glassPaint)

        // Nearly planar smoked-acrylic floor. The perimeter darkens inward,
        // reversing the convex cue that the generated button image introduced.
        glassPaint.shader = RadialGradient(
            x,
            y,
            surfaceRadius,
            intArrayOf(
                Color.rgb((17 - 4f * press).toInt(), (19 - 4f * press).toInt(), (26 - 4f * press).toInt()),
                Color.rgb((15 - 4f * press).toInt(), (17 - 4f * press).toInt(), (23 - 4f * press).toInt()),
                Color.rgb((3 - 2f * press).toInt(), (4 - 2f * press).toInt(), (7 - 2f * press).toInt())
            ),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, surfaceRadius, glassPaint)

        // A very weak clear top layer remains inside the well; it is broad and
        // low contrast so it reads as acrylic, not as a raised glossy cap.
        glassPaint.shader = LinearGradient(
            x,
            y - surfaceRadius,
            x,
            y + surfaceRadius,
            intArrayOf(
                Color.argb(9, 206, 216, 231),
                Color.argb(3, 197, 207, 221),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.32f, 0.72f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, surfaceRadius, glassPaint)
        glassPaint.shader = null

        if (press > 0f) {
            glassPaint.shader = RadialGradient(
                x,
                y,
                surfaceRadius,
                Color.argb(IPod3rdSkinTokens.BUTTON_PRESSED_GLOW_ALPHA, 255, 65, 30),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(x, y, surfaceRadius, glassPaint)
            glassPaint.shader = null
        }

        val faceBounds = RectF(
            x - surfaceRadius,
            y - surfaceRadius,
            x + surfaceRadius,
            y + surfaceRadius
        )
        edgePaint.shader = null
        edgePaint.strokeWidth = 1.3f * density
        edgePaint.color = Color.argb((218 + 18f * press).toInt(), 0, 0, 1)
        canvas.drawArc(faceBounds, 184f, 171f, false, edgePaint)
        edgePaint.strokeWidth = 0.65f * density
        edgePaint.color = Color.argb((62 + 22f * press).toInt(), 218, 37, 62)
        canvas.drawArc(faceBounds, 4f, 171f, false, edgePaint)
    }

    private fun drawCircularMaterial(
        canvas: Canvas,
        bitmap: Bitmap,
        x: Float,
        y: Float,
        radius: Float,
        alpha: Int,
        overscan: Float
    ) {
        if (bitmap.width <= 0 || bitmap.height <= 0 || radius <= 0f || alpha <= 0) return
        val destinationRadius = radius * overscan
        val saveCount = canvas.save()
        canvas.clipPath(android.graphics.Path().apply {
            addCircle(x, y, radius, android.graphics.Path.Direction.CW)
        })
        materialPaint.alpha = alpha.coerceIn(0, 255)
        materialPaint.colorFilter = null
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(
                x - destinationRadius,
                y - destinationRadius,
                x + destinationRadius,
                y + destinationRadius
            ),
            materialPaint
        )
        canvas.restoreToCount(saveCount)
    }

    private fun drawClassicTouchWheel(canvas: Canvas) {
        val grooveWidth = 7.6f * density
        val faceRadius = (maxR - grooveWidth).coerceAtLeast(0f)

        glassPaint.style = Paint.Style.FILL
        // Layer 5 starts with a recessed circular cavity: the upper-left inner
        // wall is shaded and the lower-right wall transports light.
        glassPaint.shader = LinearGradient(
            centerX - maxR,
            centerY - maxR,
            centerX + maxR,
            centerY + maxR,
            intArrayOf(
                Color.argb(148, 137, 134, 126),
                Color.argb(112, 205, 202, 194),
                Color.argb(224, 255, 255, 252)
            ),
            floatArrayOf(0f, 0.50f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, maxR, glassPaint)

        // The cavity floor is deliberately a little darker than the shell. A
        // diagonal gradient suggests depth without creating a convex centre.
        glassPaint.shader = LinearGradient(
            centerX - faceRadius,
            centerY - faceRadius,
            centerX + faceRadius,
            centerY + faceRadius,
            intArrayOf(
                Color.argb(224, 224, 222, 214),
                Color.argb(218, 235, 233, 226),
                Color.argb(210, 244, 242, 235)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, faceRadius, glassPaint)

        // Low-contrast sub-surface scatter; there is no central bright spot.
        glassPaint.shader = LinearGradient(
            centerX,
            centerY - faceRadius,
            centerX,
            centerY + faceRadius,
            intArrayOf(
                Color.argb(28, 255, 255, 252),
                Color.argb(12, 250, 248, 241),
                Color.argb(24, 174, 171, 162)
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, faceRadius, glassPaint)

        acrylicNoisePaint.alpha = 8
        canvas.drawCircle(centerX, centerY, faceRadius, acrylicNoisePaint)
        glassPaint.shader = null

        // A translucent edge ring follows the cavity floor rather than sitting
        // above it as a raised disc.
        edgePaint.shader = null
        edgePaint.strokeWidth = 1.15f * density
        edgePaint.shader = LinearGradient(
            centerX - faceRadius,
            centerY - faceRadius,
            centerX + faceRadius,
            centerY + faceRadius,
            Color.argb(118, 115, 111, 103),
            Color.argb(198, 255, 255, 252),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, faceRadius, edgePaint)
        edgePaint.shader = null

        val faceBounds = RectF(
            centerX - faceRadius,
            centerY - faceRadius,
            centerX + faceRadius,
            centerY + faceRadius
        )
        edgePaint.strokeWidth = 1.25f * density
        edgePaint.color = Color.argb(78, 77, 74, 68)
        canvas.drawArc(faceBounds, 184f, 171f, false, edgePaint)
        edgePaint.strokeWidth = 1.25f * density
        edgePaint.color = Color.argb(178, 255, 255, 252)
        canvas.drawArc(faceBounds, 4f, 171f, false, edgePaint)
    }

    private fun drawClassicCenterButton(canvas: Canvas) {
        val groove = 2.8f * density
        val press = if (centerButtonPressed) 1f else 0f

        // The centre key is another shallow well, only slightly clearer than
        // the wheel floor.
        glassPaint.shader = LinearGradient(
            centerX - minR,
            centerY - minR,
            centerX + minR,
            centerY + minR,
            Color.argb((142 + 22f * press).toInt(), 132, 129, 121),
            Color.argb((218 - 20f * press).toInt(), 255, 255, 252),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, minR + groove, glassPaint)

        glassPaint.shader = LinearGradient(
            centerX - minR,
            centerY - minR,
            centerX + minR,
            centerY + minR,
            intArrayOf(
                Color.argb((196 - 14f * press).toInt(), 229, 227, 219),
                Color.argb((184 - 14f * press).toInt(), 241, 239, 232),
                Color.argb((174 - 10f * press).toInt(), 248, 246, 239)
            ),
            floatArrayOf(0f, 0.54f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, minR, glassPaint)

        acrylicNoisePaint.alpha = 6
        canvas.drawCircle(centerX, centerY, minR, acrylicNoisePaint)
        glassPaint.shader = null

        edgePaint.strokeWidth = 0.65f * density
        edgePaint.color = Color.argb((84 + 24f * press).toInt(), 91, 87, 80)
        canvas.drawCircle(centerX, centerY, minR, edgePaint)

        val centerBounds = RectF(
            centerX - minR,
            centerY - minR,
            centerX + minR,
            centerY + minR
        )
        edgePaint.strokeWidth = 0.95f * density
        edgePaint.color = Color.argb((78 + 18f * press).toInt(), 70, 67, 61)
        canvas.drawArc(centerBounds, 184f, 171f, false, edgePaint)
        edgePaint.color = Color.argb((164 - 42f * press).toInt(), 255, 255, 252)
        canvas.drawArc(centerBounds, 4f, 171f, false, edgePaint)
    }

    private fun drawClassicTouchButton(canvas: Canvas, x: Float, y: Float, index: Int) {
        val press = if (pressedButtonIndex == index) 1f else 0f
        val rimWidth = 2.8f * density
        val faceRadius = buttonRadius - rimWidth

        // Recessed touch well: shaded inner wall at upper-left, translucent rim
        // at lower-right. The rim does not cast an exterior shadow.
        glassPaint.shader = LinearGradient(
            x - buttonRadius,
            y - buttonRadius,
            x + buttonRadius,
            y + buttonRadius,
            intArrayOf(
                Color.argb((152 + 20f * press).toInt(), 137, 133, 125),
                Color.argb(104, 206, 203, 195),
                Color.argb((218 - 24f * press).toInt(), 255, 255, 252)
            ),
            floatArrayOf(0f, 0.50f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, buttonRadius, glassPaint)

        // A slightly darker, almost planar cavity floor avoids any neumorphic
        // convex highlight. Pressing deepens it by changing shader alpha.
        glassPaint.shader = LinearGradient(
            x - faceRadius,
            y - faceRadius,
            x + faceRadius,
            y + faceRadius,
            intArrayOf(
                Color.argb(
                    (206 + 10f * press).toInt(),
                    (226 - 8f * press).toInt(),
                    (224 - 8f * press).toInt(),
                    (216 - 8f * press).toInt()
                ),
                Color.argb(
                    (194 + 8f * press).toInt(),
                    (237 - 7f * press).toInt(),
                    (235 - 7f * press).toInt(),
                    (228 - 7f * press).toInt()
                ),
                Color.argb(
                    (184 + 6f * press).toInt(),
                    (244 - 6f * press).toInt(),
                    (242 - 6f * press).toInt(),
                    (235 - 6f * press).toInt()
                )
            ),
            floatArrayOf(0f, 0.56f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, faceRadius, glassPaint)

        acrylicNoisePaint.alpha = 6
        canvas.drawCircle(x, y, faceRadius, acrylicNoisePaint)
        glassPaint.shader = null

        val faceBounds = RectF(x - faceRadius, y - faceRadius, x + faceRadius, y + faceRadius)
        edgePaint.strokeWidth = 0.6f * density
        edgePaint.color = Color.argb((98 + 22f * press).toInt(), 91, 87, 80)
        canvas.drawCircle(x, y, faceRadius, edgePaint)
        edgePaint.strokeWidth = 1.0f * density
        edgePaint.color = Color.argb((82 + 18f * press).toInt(), 70, 67, 61)
        canvas.drawArc(
            faceBounds,
            184f,
            171f,
            false,
            edgePaint
        )
        edgePaint.color = Color.argb((164 - 42f * press).toInt(), 255, 255, 252)
        canvas.drawArc(
            faceBounds,
            4f,
            171f,
            false,
            edgePaint
        )
    }

    private var startPoint: TouchPoint = emptyTouchPoint()
    private var prevPoint: TouchPoint = emptyTouchPoint()
    private var touchTimer: Timer? = null
    
    // 用于记录顶部按钮区域的长按状态
    private var buttonLongClickState: Int = -1 // -1: 无, 0: PREV, 1: MENU, 2: PAUSE, 3: NEXT

    private fun cancelTimer() {
        touchTimer?.cancel()
        touchTimer = null
        buttonLongClickState = -1
    }

    private fun setTimer(curPoint: TouchPoint, buttonIndex: Int = -1) {
        if (!enable) {
            return
        }
        val timerTask = object : TimerTask() {
            override fun run() {
                ThreadUtil.runOnUiThread(Runnable {
                    onTouch()
                    if (startPoint.slided) {
                        return@Runnable
                    }
                    // 优先处理顶部按钮区域的长按
                    if (buttonIndex >= 0 && buttonLongClickState == buttonIndex) {
                        when (buttonIndex) {
                            0 -> onPrevLongClick()
                            1 -> onMenuLongClick()
                            2 -> onPauseLongClick()
                            3 -> onNextLongClick()
                        }
                        cancelTimer()
                        return@Runnable
                    }
                    if (startPoint.inCenter && curPoint.inCenter && (prevPoint.isEmpty() || prevPoint.inCenter)) {
                        onEnterLongClick()
                        cancelTimer()
                    }
                    // 圆盘区域长按不再触发任何功能，只能通过顶部按钮区域触发
                })
            }
        }
        cancelTimer()
        buttonLongClickState = buttonIndex
        touchTimer = Timer().apply {
            schedule(timerTask, 500, 600)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null || !enable) {
            return super.onTouchEvent(event)
        }

        val x = event.x
        val y = event.y
        val curPoint = TouchPoint(
            x,
            y,
            System.currentTimeMillis()
        )

        onTouch()
        
        // 检查是否触摸到屏幕顶部的按钮区域
        val buttonRowY = maxR * 0.5f
        val iconButtonHeight =
            maxOf(Icons.MENU.height, Icons.PREV.height, Icons.PAUSE.height, Icons.NEXT.height)
                .toFloat()
        val buttonHeight = if (classicMaterial) buttonRadius else iconButtonHeight
        val buttonAreaTop = buttonRowY - buttonHeight
        val buttonAreaBottom = buttonRowY + buttonHeight
        
        if (y >= buttonAreaTop && y <= buttonAreaBottom) {
            // 触摸到了按钮区域
            val button1X = buttonCenterX(0)
            val button2X = buttonCenterX(1)
            val button3X = buttonCenterX(2)
            val button4X = buttonCenterX(3)
            val iconButtonWidth =
                maxOf(Icons.MENU.width, Icons.PREV.width, Icons.PAUSE.width, Icons.NEXT.width)
                    .toFloat()
            val buttonWidth = if (classicMaterial) buttonRadius * 2f else iconButtonWidth
            
            // 判断触摸的是哪个按钮
            val touchedButtonIndex = when {
                x >= button1X - buttonWidth/2 && x <= button1X + buttonWidth/2 -> 0 // PREV
                x >= button2X - buttonWidth/2 && x <= button2X + buttonWidth/2 -> 1 // MENU
                x >= button3X - buttonWidth/2 && x <= button3X + buttonWidth/2 -> 2 // PAUSE
                x >= button4X - buttonWidth/2 && x <= button4X + buttonWidth/2 -> 3 // NEXT
                else -> -1
            }
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (touchedButtonIndex >= 0) {
                        pressedButtonIndex = touchedButtonIndex
                        startButtonBacklight()
                        buttonLongClickState = touchedButtonIndex
                        setTimer(curPoint, touchedButtonIndex)
                        postInvalidateOnAnimation()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    cancelTimer()
                    // 检查具体是哪个按钮被点击（短按）
                    when (touchedButtonIndex) {
                        0 -> onPrevClick()
                        1 -> onMenuClick()
                        2 -> onPauseClick()
                        3 -> onNextClick()
                    }
                    pressedButtonIndex = -1
                    postInvalidateOnAnimation()
                }
                MotionEvent.ACTION_CANCEL -> {
                    cancelTimer()
                    pressedButtonIndex = -1
                    postInvalidateOnAnimation()
                }
            }
            return true
        } else if (curPoint.r <= maxR) {
            // 触摸到了圆盘区域，保持原有的滑动功能
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startPoint = curPoint
                    prevPoint.clear()
                    centerButtonPressed = curPoint.inCenter
                    wheelPressed = curPoint.inCircle
                    setTimer(curPoint)
                    postInvalidateOnAnimation()
                }

                MotionEvent.ACTION_MOVE -> {
                    val shouldShowCenterPress =
                        startPoint.inCenter && curPoint.inCenter && !startPoint.slided
                    if (centerButtonPressed != shouldShowCenterPress) {
                        centerButtonPressed = shouldShowCenterPress
                        postInvalidateOnAnimation()
                    }
                    val slideVal = if (prevPoint.isEmpty()) calcSlideVal(
                        startPoint,
                        curPoint
                    ) else calcSlideVal(
                        prevPoint,
                        curPoint
                    )
                    if (startPoint.inCircle && curPoint.inCircle) {
                        if (!prevPoint.isEmpty() && slideVal != 0) {
                            onSlide(slideVal)
                            startPoint.slided = true
                            cancelTimer()
                        }
                    }
                    prevPoint = curPoint
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelTimer()
                    centerButtonPressed = false
                    wheelPressed = false
                    postInvalidateOnAnimation()
                    if (startPoint.slided) {
                        return true
                    }
                    if ((curPoint.t - startPoint.t) <= 400) {
                        if (startPoint.inCenter && curPoint.inCenter) {
                            onEnterClick()
                        }
                    }
                    startPoint.clear()
                    prevPoint.clear()
                }
            }
            return true
        }

        // A finger may leave the visual control before release. Clear the live
        // material state even when the gesture ends outside every hit region.
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            pressedButtonIndex = -1
            centerButtonPressed = false
            wheelPressed = false
            postInvalidateOnAnimation()
        }
        return false
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (event == null || !enable) {
            return super.dispatchTouchEvent(event)
        }
        val handled = super.dispatchTouchEvent(event)
        return handled
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            cancelTimer()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelTimer()
        buttonBacklightStartedAt = 0L
        pressedButtonIndex = -1
        centerButtonPressed = false
        wheelPressed = false
    }

    private fun emptyTouchPoint(): TouchPoint = TouchPoint(0f, 0f, 0L)

    /**
     * 大于0：next，等于0：未移动，小于0：prev
     */
    private fun calcSlideVal(prevPoint: TouchPoint, curPoint: TouchPoint): Int {
        val prevDeg = prevPoint.deg
        val curDeg = curPoint.deg
        val minus = prevDeg - curDeg
        if (abs(minus) >= SLIDE_VAL * 4 || minus == 0) {
            return 0
        }
        if ((prevDeg == 360 - SLIDE_VAL ||
                prevDeg == 360 - SLIDE_VAL * 2 ||
                prevDeg == 360 - SLIDE_VAL * 3) && curDeg == 0
        ) {
            return -1
        } else if (prevDeg == 0 &&
            (curDeg == 360 - SLIDE_VAL ||
                curDeg == 360 - SLIDE_VAL * 2 ||
                curDeg == 360 - SLIDE_VAL * 3)
        ) {
            return 1
        }
        return if (minus > 0) 1 else -1
    }

    inner class TouchPoint constructor(x: Float, y: Float, t: Long) {

        var x = 0f
        var y = 0f
        var t = 0L
        var r = 0f
        var deg = 0

        var inCenter = false
        var inCircle = false
        var slided = false

        init {
            this.x = x - centerX
            this.y = -(y - centerY)
            this.t = t
            this.r = getR(this.x, this.y)
            this.deg = getDeg(this.x, this.y)

            inCenter = r >= 0 && r < minR
            inCircle = r in minR..maxR
        }

        fun clear() {
            x = 0f
            y = 0f
            t = 0L
            r = 0f
            deg = 0
            inCenter = false
            inCircle = false
            slided = false
        }

        private fun getR(x: Float, y: Float): Float {
            return sqrt(x * x + y * y)
        }

        private fun getDeg(x: Float, y: Float): Int {
            var deg: Int = Math.toDegrees(Math.atan2(y.toDouble(), x.toDouble())).toInt()
            if (deg < 0) {
                deg += 360
            }
            deg /= SLIDE_VAL
            deg *= SLIDE_VAL
            return deg
        }

        fun isEmpty(): Boolean {
            return t == 0L
        }

    }

    private fun onEnterClick() {
        if (!enable) {
            return
        }
        if (onTouchListener?.onEnterClick() == true) {
            VolumeUtil.vibrate(this)
        }
    }

    private fun onEnterLongClick() {
        if (!enable) {
            return
        }
        if (onTouchListener?.onEnterLongClick() == true) {
            VolumeUtil.vibrate(this)
        }
    }

    private fun onMenuClick() {
        if (!enable) {
            return
        }
        if (onTouchListener?.onMenuClick() == true) {
            VolumeUtil.vibrate(this)
        }
    }

    private fun onMenuLongClick() {
        if (!enable) {
            return
        }
        if (onTouchListener?.onMenuLongClick() == true) {
            VolumeUtil.vibrate(this)
        }
    }

    private fun onPrevClick() {
        if (!enable) {
            return
        }
        if (onTouchListener?.onPrevClick() == true) {
            VolumeUtil.vibrate(this)
        }
    }

    private fun onPrevLongClick() {
        if (!enable) {
            return
        }
        if (onTouchListener?.onPrevLongClick() == true) {
            VolumeUtil.vibrate(this)
        }
    }

    private fun onNextClick() {
        if (!enable) {
            return
        }
        if (onTouchListener?.onNextClick() == true) {
            VolumeUtil.vibrate(this)
        }
    }

    private fun onNextLongClick() {
        if (!enable) {
            return
        }
        if (onTouchListener?.onNextLongClick() == true) {
            VolumeUtil.vibrate(this)
        }
    }

    private fun onPauseClick() {
        if (!enable) {
            return
        }
        if (onTouchListener?.onPauseClick() == true) {
            VolumeUtil.vibrate(this)
        }
    }

    private fun onPauseLongClick() {
        if (!enable) {
            return
        }
        if (onTouchListener?.onPauseLongClick() == true) {
            VolumeUtil.vibrate(this)
        }
    }

    private fun onSlide(slideVal: Int) {
        if (!enable) {
            return
        }
        val bOnSlide = onTouchListener?.onSlide(slideVal)
        if (bOnSlide == true) {
            VolumeUtil.vibrate(this)
        }
    }

    private fun onTouch() {
        if (!enable) {
            return
        }
        onTouchListener?.onTouch()
    }

    var onTouchListener: OnTouchListener? = null

    interface OnTouchListener {
        fun onSlide(slideVal: Int) = false
        fun onEnterClick() = false
        fun onEnterLongClick() = false
        fun onPrevClick() = false
        fun onPrevLongClick() = false
        fun onNextClick() = false
        fun onNextLongClick() = false
        fun onMenuClick() = false
        fun onMenuLongClick() = false
        fun onPauseClick() = false
        fun onPauseLongClick() = false
        fun onTouch() {}
    }
}
