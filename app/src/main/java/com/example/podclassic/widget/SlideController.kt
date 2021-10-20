package com.example.podclassic.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.podclassic.`object`.Core
import com.example.podclassic.`object`.MediaPlayer
import com.example.podclassic.util.Colors
import com.example.podclassic.util.Icons.MENU
import com.example.podclassic.util.Icons.NEXT
import com.example.podclassic.util.Icons.PAUSE
import com.example.podclassic.util.Icons.PREV
import com.example.podclassic.util.ThreadUtil
import com.example.podclassic.util.Values
import com.example.podclassic.util.VolumeUtil
import java.util.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt


class SlideController : View {

    companion object {
        private const val SLIDE_VAL = 18
        private var centerX = 0f
        private var centerY = 0f

        private var minR = 0f
        private var maxR = 0f

    }

    constructor(context : Context?) : super(context)
    constructor(context: Context?, attributeSet: AttributeSet?) : super(context, attributeSet)

    private val paint = Paint()

    private var lock = false

    fun lock(lock : Boolean) {
        this.lock = lock
        if (lock) {
            cancelTimer()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val x = MeasureSpec.getSize(widthMeasureSpec)
        val y = MeasureSpec.getSize(heightMeasureSpec)
        centerX = x / 2f
        centerY = y / 2f

        val center = min(centerX, centerY)

        maxR = center * (center / (center + abs(centerX - centerY) + Values.screenWidth / 12))

        //maxR = min(maxR, center - Values.screenWidth / 12)

        /*
        when {
            MainActivity.screenRatio <= 17f / 9f && MainActivity.screenRatio >= 16f / 9f -> {
                maxR = maxR / 8 * 7
            }
            MainActivity.screenRatio > 1f / 2f -> {
                maxR = maxR / 4 * 3
            }
            MainActivity.screenRatio <= 1f / 2f && MainActivity.screenRatio > 17f / 9f -> {
                maxR = maxR / 6 * 5
            }
        }

         */

        minR = maxR / 16 * 5

        setMeasuredDimension(x, y)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        paint.isAntiAlias = true
        paint.color = Colors.controller
        canvas?.drawCircle(centerX, centerY, maxR, paint)
        paint.color = Colors.color_primary
        canvas?.drawCircle(centerX, centerY, minR, paint)

        val r = (minR + maxR) / 2

        canvas?.drawBitmap(MENU.bitmap, centerX - MENU.width / 2, centerY - r - PAUSE.height - MENU.height / 2 , paint)
        canvas?.drawBitmap(PREV.bitmap, (centerX - r) - PREV.width / 2 * 3, centerY - PREV.height / 2, paint)
        canvas?.drawBitmap(NEXT.bitmap, (centerX + r) + NEXT.width / 2, centerY - NEXT.height / 2, paint)
        canvas?.drawBitmap(PAUSE.bitmap, centerX - PAUSE.width / 2, centerY + r + PAUSE.height / 2, paint)

    }

    private var startPoint : TouchPoint =
        TouchPoint.emptyTouchPoint()
    private var prevPoint : TouchPoint =
        TouchPoint.emptyTouchPoint()
    private var touchTimer : Timer? = null

    private fun cancelTimer() {
        touchTimer?.cancel()
        touchTimer = null
    }

    private fun setTimer(curPoint : TouchPoint) {
        if (lock) {
            return
        }
        val timerTask = object : TimerTask() {
            override fun run() {
                ThreadUtil.runOnUiThread(Runnable {
                    Core.wake()
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
        touchTimer = Timer()
        touchTimer?.schedule(timerTask, 500, 600)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null || lock) {
            return super.onTouchEvent(event)
        }

        val curPoint = TouchPoint(
            event.x,
            event.y,
            System.currentTimeMillis()
        )

        if (curPoint.r > maxR) {
            return super.onTouchEvent(event)
        }
        Core.wake()
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
                        //如果slideVal = 0 不会调用slide方法
                        slide(slideVal)
                        startPoint.slided= true
                        cancelTimer()
                    }
                }
                prevPoint = curPoint
            }

            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {
                cancelTimer()
                if (startPoint.slided) {
                    return true
                }
                if ((curPoint.t - startPoint.t) <= 400) {
                    if (startPoint.inCenter && curPoint.inCenter) {
                        onEnterClick()
                    } else if (startPoint.inCircle && curPoint.inCircle) {
                        when (startPoint.deg) {
                            in 45..135 -> { onMenuClick() }
                            in 135..225 -> { onPrevClick() }
                            in 225..315 -> { onPauseClick() }
                            in 315..360 -> { onNextClick() }
                            in 0..45 -> { onNextClick() }
                        }
                    }
                }
                startPoint.clear()
                prevPoint.clear()
            }
        }
        return true
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

    class TouchPoint constructor(x : Float, y : Float, t : Long) {

        companion object {

            fun emptyTouchPoint() : TouchPoint {
                return TouchPoint(
                    0f,
                    0f,
                    0L
                )
            }

            /**
             * 大于0：next，等于0：未移动，小于0：prev
             */
            private fun calcSlideVal(prevDeg : Int, curDeg : Int) : Int {
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

            fun calcSlideVal(prevPoint : TouchPoint, curPoint : TouchPoint) : Int {

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

        private fun getR(x : Float, y : Float) : Float {
            return sqrt(x * x + y * y)
        }

        private fun getDeg(x : Float, y : Float) : Int {
            var deg : Int = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toInt()
            if (deg < 0) {
                deg += 360
            }
            deg /= SLIDE_VAL
            deg *= SLIDE_VAL
            return deg
        }

        fun isEmpty() : Boolean {
            return t == 0L
        }

    }

    private fun onEnterClick() {
        if (lock) {
            return
        }
        if (onTouchListener?.onEnterClick() == true) {
            VolumeUtil.vibrate(this)
        }
    }

    private fun onMenuClick() {
        if (lock) {
            return
        }
        if (Core.removeView()) {
            VolumeUtil.vibrate(this)
        }
    }

    private fun onPrevClick() {
        if (lock) {
            return
        }
        MediaPlayer.prev()
        VolumeUtil.vibrate(this)
    }

    private fun onNextClick() {
        if (lock) {
            return
        }
        MediaPlayer.next()
        VolumeUtil.vibrate(this)
    }


    private fun slide(slideVal : Int) {
        if (lock) {
            return
        }
        if (onTouchListener?.onSlide(slideVal) == true) {
            VolumeUtil.vibrate(this)
        }
    }


    private fun onPauseClick() {
        if (lock) {
            return
        }
        MediaPlayer.pause()
        VolumeUtil.vibrate(this)
    }

    private fun onEnterLongClick() {
        if (lock) {
            return
        }
        if (onTouchListener?.onEnterLongClick() == true) {
            VolumeUtil.vibrate(this)
        }
    }

    private fun onNextLongClick() {
        if (lock) {
            return
        }
        MediaPlayer.forward()
        VolumeUtil.vibrate(this)
    }

    private fun onPauseLongClick() {}

    private fun onPrevLongClick() {
        if (lock) {
            return
        }
        MediaPlayer.backward()
        VolumeUtil.vibrate(this)
    }

    private fun onMenuLongClick() {
        if (lock) {
            return
        }
        Core.home()
        cancelTimer()
        VolumeUtil.vibrate(this)
    }

    var onTouchListener : OnTouchListener? = null

    interface OnTouchListener {
        fun onEnterClick() : Boolean
        fun onEnterLongClick() : Boolean
        fun onSlide(slideVal: Int) : Boolean
    }
}