package com.example.podclassic.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.podclassic.util.ThreadUtil
import com.example.podclassic.util.VolumeUtil
import com.example.podclassic.values.Colors
import com.example.podclassic.values.Icons
import com.example.podclassic.values.Values
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

    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attributeSet: AttributeSet?) : super(context, attributeSet)

    private val paint = Paint()

    var enable = true
        set(value) {
            if (!value) {
                cancelTimer()
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
        centerY = y / 2f

        val center = min(centerX, centerY)

        maxR = center * (center / (center + abs(centerX - centerY) + Values.screenWidth / 12))

        minR = maxR / 16 * 5

    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        paint.isAntiAlias = true
        paint.color = colorController
        canvas.drawCircle(centerX, centerY, maxR, paint)
        paint.color = colorButton
        canvas.drawCircle(centerX, centerY, minR, paint)

        // 按钮位置调整：在屏幕顶部，形成一行
        val buttonRowY = maxR * 0.5f // 按钮行位于屏幕顶部，距离圆盘顶部一定距离
        val buttonSpacing = width / 5f // 按钮之间的间距

        // 计算四个按钮的X坐标，形成一行
        val button1X = buttonSpacing
        val button2X = buttonSpacing * 2
        val button3X = buttonSpacing * 3
        val button4X = buttonSpacing * 4

        // 绘制四个按钮
        canvas.drawBitmap(
            Icons.PREV.bitmap,
            button1X - Icons.PREV.width / 2,
            buttonRowY - Icons.PREV.height / 2,
            paint
        )
        canvas.drawBitmap(
            Icons.MENU.bitmap,
            button2X - Icons.MENU.width / 2,
            buttonRowY - Icons.MENU.height / 2,
            paint
        )
        canvas.drawBitmap(
            Icons.PAUSE.bitmap,
            button3X - Icons.PAUSE.width / 2,
            buttonRowY - Icons.PAUSE.height / 2,
            paint
        )
        canvas.drawBitmap(
            Icons.NEXT.bitmap,
            button4X - Icons.NEXT.width / 2,
            buttonRowY - Icons.NEXT.height / 2,
            paint
        )

    }

    private var startPoint: TouchPoint = TouchPoint.emptyTouchPoint()
    private var prevPoint: TouchPoint = TouchPoint.emptyTouchPoint()
    private var touchTimer: Timer? = null

    private fun cancelTimer() {
        touchTimer?.cancel()
        touchTimer = null
    }

    private fun setTimer(curPoint: TouchPoint) {
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
                    if (startPoint.inCenter && curPoint.inCenter && (prevPoint.isEmpty() || prevPoint.inCenter)) {
                        onEnterLongClick()
                        cancelTimer()
                    } else if (startPoint.inCircle && curPoint.inCircle && (prevPoint.isEmpty() || prevPoint.inCircle)) {
                        when (startPoint.deg) {
                            in 45..135 -> onMenuLongClick()
                            in 135..225 -> onPrevLongClick()
                            in 225..315 -> onPauseLongClick()
                            in 315..360 -> onNextLongClick()
                            in 0..45 -> onNextLongClick()
                        }
                    }
                })
            }
        }
        cancelTimer()
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
            
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    // 检查具体是哪个按钮被点击
                    if (x >= button1X - buttonWidth/2 && x <= button1X + buttonWidth/2) {
                        // 上一首按钮
                        onPrevClick()
                    } else if (x >= button2X - buttonWidth/2 && x <= button2X + buttonWidth/2) {
                        // 菜单按钮
                        onMenuClick()
                    } else if (x >= button3X - buttonWidth/2 && x <= button3X + buttonWidth/2) {
                        // 播放/暂停按钮
                        onPauseClick()
                    } else if (x >= button4X - buttonWidth/2 && x <= button4X + buttonWidth/2) {
                        // 下一首按钮
                        onNextClick()
                    }
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
