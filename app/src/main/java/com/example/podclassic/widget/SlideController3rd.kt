package com.example.podclassic.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
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
        private var centerX = 0f
        private var centerY = 0f

        private var minR = 0f
        private var maxR = 0f

        private const val WHEEL_DIAMETER_DP = 217f
        private const val WHEEL_BOTTOM_MARGIN_DP = 64f
        private const val CONTROL_BUTTON_DIAMETER_DP = 56f
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
    private var wheelBaseShader: Shader? = null
    private var wheelReflectionShader: Shader? = null
    private var wheelDepthShader: Shader? = null
    private var wheelEdgeShader: Shader? = null
    private var buttonRadius = 0f
    private var density = 1f
    private var buttonBacklightStartedAt = 0L

    var enable = true
        set(value) {
            if (!value) {
                cancelTimer()
                buttonBacklightStartedAt = 0L
                invalidate()
            }
            field = value
        }

    var colorController: Int = Color.RED
    var colorButton: Int = Color.BLACK

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val x = MeasureSpec.getSize(widthMeasureSpec)
        val y = MeasureSpec.getSize(heightMeasureSpec)
        centerX = x / 2f
        density = resources.displayMetrics.density
        val requestedRadius = WHEEL_DIAMETER_DP * density / 2f
        val bottomMargin = WHEEL_BOTTOM_MARGIN_DP * density

        // 3G 转盘保持 217dp 直径，以 64dp 底边距为顶部按钮留出更多呼吸空间。
        // 极窄或极矮的窗口中才缩小，避免转盘被裁切。
        maxR = min(
            requestedRadius,
            min(centerX, (y - bottomMargin).coerceAtLeast(0f) / 2f)
        )
        centerY = (y - bottomMargin - maxR).coerceAtLeast(maxR)

        minR = maxR / 16 * 5

        buttonRadius = CONTROL_BUTTON_DIAMETER_DP * density / 2f
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

        drawLiquidGlassWheel(canvas)

        drawRecessedCenterButton(canvas)

        // 按钮位置调整：在屏幕顶部，形成一行
        val buttonRowY = maxR * 0.5f // 按钮行位于屏幕顶部，距离圆盘顶部一定距离
        val buttonSpacing = width / 5f // 按钮之间的间距

        // 计算四个按钮的X坐标，形成一行
        val button1X = buttonSpacing
        val button2X = buttonSpacing * 2
        val button3X = buttonSpacing * 3
        val button4X = buttonSpacing * 4

        drawGlassButton(canvas, button1X, buttonRowY)
        drawGlassButton(canvas, button2X, buttonRowY)
        drawGlassButton(canvas, button3X, buttonRowY)
        drawGlassButton(canvas, button4X, buttonRowY)

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

    private fun drawBacklitIcon(
        canvas: Canvas,
        bitmap: Bitmap,
        centerX: Float,
        centerY: Float
    ) {
        val left = centerX - bitmap.width / 2f
        val top = centerY - bitmap.height / 2f
        val intensity = currentBacklightIntensity()
        if (intensity <= 0f) {
            canvas.drawBitmap(bitmap, left, top, iconPaint)
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
        canvas.drawBitmap(bitmap, left, top, iconBacklightPaint)
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

    private fun drawLiquidGlassWheel(canvas: Canvas) {
        val rimWidth = 6f * density
        val surfaceRadius = (maxR - rimWidth).coerceAtLeast(0f)

        // The outer lip belongs to the raised enclosure surrounding the wheel.
        glassPaint.style = Paint.Style.FILL
        glassPaint.shader = wheelEdgeShader
        canvas.drawCircle(centerX, centerY, maxR, glassPaint)

        glassPaint.shader = wheelBaseShader
        canvas.drawCircle(centerX, centerY, surfaceRadius, glassPaint)
        glassPaint.shader = wheelReflectionShader
        canvas.drawCircle(centerX, centerY, surfaceRadius, glassPaint)
        glassPaint.shader = wheelDepthShader
        canvas.drawCircle(centerX, centerY, surfaceRadius, glassPaint)
        glassPaint.shader = null

        val rimBounds = RectF(
            centerX - maxR + density,
            centerY - maxR + density,
            centerX + maxR - density,
            centerY + maxR - density
        )
        edgePaint.shader = null
        edgePaint.strokeWidth = 1.6f * density
        edgePaint.color = Color.argb(190, 255, 184, 194)
        canvas.drawArc(rimBounds, 185f, 170f, false, edgePaint)
        edgePaint.color = Color.argb(190, 22, 0, 4)
        canvas.drawArc(rimBounds, 5f, 170f, false, edgePaint)

        val insetBounds = RectF(
            centerX - surfaceRadius,
            centerY - surfaceRadius,
            centerX + surfaceRadius,
            centerY + surfaceRadius
        )
        edgePaint.strokeWidth = 4.2f * density
        edgePaint.color = Color.argb(150, 38, 0, 7)
        canvas.drawArc(insetBounds, 180f, 180f, false, edgePaint)
        edgePaint.strokeWidth = 1.15f * density
        edgePaint.color = Color.argb(122, 255, 125, 140)
        canvas.drawArc(insetBounds, 0f, 180f, false, edgePaint)
    }

    private fun drawRecessedCenterButton(canvas: Canvas) {
        val lipWidth = 3.5f * density
        glassPaint.style = Paint.Style.FILL
        glassPaint.shader = LinearGradient(
            centerX,
            centerY - minR - lipWidth,
            centerX,
            centerY + minR + lipWidth,
            Color.argb(210, 255, 112, 126),
            Color.argb(230, 48, 0, 8),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, minR + lipWidth, glassPaint)
        glassPaint.shader = RadialGradient(
            centerX + minR * 0.18f,
            centerY + minR * 0.22f,
            minR * 1.25f,
            Color.argb(255, 28, 29, 33),
            colorButton,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, minR, glassPaint)
        glassPaint.shader = null

        val bounds = RectF(centerX - minR, centerY - minR, centerX + minR, centerY + minR)
        edgePaint.strokeWidth = 3.2f * density
        edgePaint.color = Color.argb(190, 0, 0, 0)
        canvas.drawArc(bounds, 180f, 180f, false, edgePaint)
        edgePaint.strokeWidth = density
        edgePaint.color = Color.argb(105, 255, 255, 255)
        canvas.drawArc(bounds, 0f, 180f, false, edgePaint)
    }

    private fun drawGlassButton(canvas: Canvas, x: Float, y: Float) {
        val rimWidth = 3.5f * density
        val surfaceRadius = buttonRadius - rimWidth

        // A restrained raised lip surrounds the lower glass face. The broad
        // radial transition avoids a metallic, cut-out looking ring.
        glassPaint.shader = RadialGradient(
            x - buttonRadius * 0.22f,
            y - buttonRadius * 0.26f,
            buttonRadius * 1.48f,
            intArrayOf(
                Color.argb(112, 232, 236, 243),
                Color.argb(82, 99, 106, 119),
                Color.argb(132, 18, 20, 26)
            ),
            floatArrayOf(0f, 0.56f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, buttonRadius, glassPaint)

        // One continuous concave surface: dark center, softly lifted shoulder,
        // then a darker inner wall. There is no 50% horizontal color stop.
        glassPaint.shader = RadialGradient(
            x,
            y + surfaceRadius * 0.08f,
            surfaceRadius * 1.05f,
            intArrayOf(
                Color.argb(224, 18, 21, 29),
                Color.argb(214, 31, 35, 45),
                Color.argb(188, 62, 68, 81),
                Color.argb(226, 25, 28, 36)
            ),
            floatArrayOf(0f, 0.38f, 0.74f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, surfaceRadius, glassPaint)

        // Soft upper reflection fades through the entire face instead of ending
        // at the equator as the previous semicircular highlight did.
        glassPaint.shader = RadialGradient(
            x,
            y - surfaceRadius * 0.76f,
            surfaceRadius * 1.28f,
            intArrayOf(
                Color.argb(58, 255, 255, 255),
                Color.argb(20, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.46f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, surfaceRadius, glassPaint)

        // A low, diffuse shadow supplies depth without a straight lower edge.
        glassPaint.shader = RadialGradient(
            x,
            y + surfaceRadius * 1.12f,
            surfaceRadius * 1.18f,
            intArrayOf(
                Color.argb(72, 0, 0, 0),
                Color.argb(24, 0, 0, 0),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, surfaceRadius, glassPaint)
        glassPaint.shader = null

        // Complete circular edges stay continuous at the left and right sides.
        edgePaint.shader = null
        edgePaint.strokeWidth = 1.15f * density
        edgePaint.color = Color.argb(92, 214, 220, 230)
        canvas.drawCircle(x, y, buttonRadius - edgePaint.strokeWidth / 2f, edgePaint)

        edgePaint.strokeWidth = 2.4f * density
        edgePaint.color = Color.argb(76, 5, 7, 11)
        canvas.drawCircle(x, y, surfaceRadius - edgePaint.strokeWidth / 2f, edgePaint)
        edgePaint.strokeWidth = 0.65f * density
        edgePaint.color = Color.argb(54, 238, 242, 248)
        canvas.drawCircle(x, y, surfaceRadius - edgePaint.strokeWidth / 2f, edgePaint)
    }

    private var startPoint: TouchPoint = TouchPoint.emptyTouchPoint()
    private var prevPoint: TouchPoint = TouchPoint.emptyTouchPoint()
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
        val buttonHeight = maxOf(Icons.MENU.height, Icons.PREV.height, Icons.PAUSE.height, Icons.NEXT.height).toFloat()
        val buttonAreaTop = buttonRowY - buttonHeight
        val buttonAreaBottom = buttonRowY + buttonHeight
        
        if (y >= buttonAreaTop && y <= buttonAreaBottom) {
            // 触摸到了按钮区域
            val buttonSpacing = width / 5f
            val button1X = buttonSpacing
            val button2X = buttonSpacing * 2
            val button3X = buttonSpacing * 3
            val button4X = buttonSpacing * 4
            val buttonWidth = maxOf(Icons.MENU.width, Icons.PREV.width, Icons.PAUSE.width, Icons.NEXT.width).toFloat()
            
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
                        startButtonBacklight()
                        buttonLongClickState = touchedButtonIndex
                        setTimer(curPoint, touchedButtonIndex)
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
                }
                MotionEvent.ACTION_CANCEL -> {
                    cancelTimer()
                }
            }
            return true
        } else if (curPoint.r <= maxR) {
            // 触摸到了圆盘区域，保持原有的滑动功能
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startPoint = curPoint
                    prevPoint.clear()
                    setTimer(curPoint)
                }

                MotionEvent.ACTION_MOVE -> {
                    val slideVal = if (prevPoint.isEmpty()) TouchPoint.calcSlideVal(
                        startPoint,
                        curPoint
                    ) else TouchPoint.calcSlideVal(
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
    }

    class TouchPoint constructor(x: Float, y: Float, t: Long) {

        companion object {

            fun emptyTouchPoint(): TouchPoint {
                return TouchPoint(
                    0f,
                    0f,
                    0L
                )
            }

            /**
             * 大于0：next，等于0：未移动，小于0：prev
             */
            private fun calcSlideVal(prevDeg: Int, curDeg: Int): Int {
                val minus = prevDeg - curDeg
                if (abs(minus) >= SLIDE_VAL * 4 || minus == 0) {
                    return 0
                }
                if ((prevDeg == 360 - SLIDE_VAL || prevDeg == 360 - SLIDE_VAL * 2 || prevDeg == 360 - SLIDE_VAL * 3) && curDeg == 0) {
                    return -1
                } else if (prevDeg == 0 && (curDeg == 360 - SLIDE_VAL || curDeg == 360 - SLIDE_VAL * 2 || curDeg == 360 - SLIDE_VAL * 3)) {
                    return 1
                }
                return if (minus > 0) 1 else -1
            }

            fun calcSlideVal(prevPoint: TouchPoint, curPoint: TouchPoint): Int {

                return calcSlideVal(
                    prevPoint.deg,
                    curPoint.deg
                )
            }
        }

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
