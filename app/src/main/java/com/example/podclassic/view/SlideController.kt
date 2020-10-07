package com.example.podclassic.view

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
import com.example.podclassic.util.VolumeUtil
import java.util.*
import kotlin.concurrent.timer
import kotlin.math.abs
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
        //var y = MeasureSpec.getSize(heightMeasureSpec)
        centerX = x / 2f
        centerY = centerX

        minR = centerX / 16 * 5
        maxR = centerX

        setMeasuredDimension(x, x)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        paint.isAntiAlias = true
        paint.color = Colors.controller
        canvas?.drawCircle(centerX, centerY, centerX, paint)
        paint.color = Colors.color_primary
        canvas?.drawCircle(centerX, centerY, centerX / 16 * 5, paint)

        canvas?.drawBitmap(MENU.bitmap, centerX - MENU.width / 2, (centerY / 3) - MENU.height , paint)
        canvas?.drawBitmap(PREV.bitmap, (centerX / 4) - PREV.width, centerY - PREV.height / 2, paint)
        canvas?.drawBitmap(NEXT.bitmap, ( - centerX / 4 + 2 * centerX), centerY - NEXT.height / 2, paint)
        canvas?.drawBitmap(PAUSE.bitmap, centerX - PAUSE.width / 2, ( - centerY / 4 + 2 * centerY), paint)

    }

    private var startPoint : TouchPoint = TouchPoint.emptyTouchPoint()
    private var prevPoint : TouchPoint = TouchPoint.emptyTouchPoint()
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
                        onEnterLongClicked()
                        cancelTimer()
                    } else if (startPoint.inCircle && curPoint.inCircle && (prevPoint.isEmpty() || prevPoint.inCircle)) {
                        when (startPoint.deg) {
                            in 45..135 -> onMenuLongClicked()
                            in 135..225 -> onPrevLongClicked()
                            in 225..315 -> onPauseLongClicked()
                            in 315..360 -> onNextLongClicked()
                            in 0..45 -> onNextLongClicked()
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

        val curPoint = TouchPoint(event.x, event.y, System.currentTimeMillis())

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
                val slideVal = if (prevPoint.isEmpty()) TouchPoint.calcSlideVal(startPoint, curPoint) else TouchPoint.calcSlideVal(prevPoint, curPoint)
                if (startPoint.inCircle && curPoint.inCircle) {
                    if (!prevPoint.isEmpty() && slideVal != 0) {
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
                        onEnterClicked()
                    } else if (startPoint.inCircle && curPoint.inCircle) {
                        when (startPoint.deg) {
                            in 45..135 -> { onMenuClicked() }
                            in 135..225 -> { onPrevClicked() }
                            in 225..315 -> { onPauseClicked() }
                            in 315..360 -> { onNextClicked() }
                            in 0..45 -> { onNextClicked() }
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

            fun emptyTouchPoint() : TouchPoint{
                return TouchPoint(0f, 0f, 0L)
            }

            /**
             * 大于0：next，等于0：未移动，小于0：prev
             */
            private fun calcSlideVal(prevDeg : Int, curDeg : Int) : Int {
                if (abs(prevDeg - curDeg) >= SLIDE_VAL * 4) {
                    return 0
                }
                if ((prevDeg == 360 - SLIDE_VAL || prevDeg == 360 - SLIDE_VAL * 2 || prevDeg == 360 - SLIDE_VAL * 3) && curDeg == 0) {
                    return -1
                } else if (prevDeg == 0 && (curDeg == 360 - SLIDE_VAL || curDeg == 360 - SLIDE_VAL * 2 || curDeg == 360 - SLIDE_VAL * 3)) {
                    return 1
                }
                return  prevDeg - curDeg
            }

            fun calcSlideVal(prevPoint : TouchPoint, curPoint : TouchPoint) : Int {

                return calcSlideVal(prevPoint.deg, curPoint.deg)
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
            var deg : Int = Math.toDegrees(Math.atan2(y.toDouble(), x.toDouble())).toInt()
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

    private fun onEnterClicked() {
        if (lock) {
            return
        }
        if (onTouchListener?.onEnterClick() == true) {
            VolumeUtil.vibrate()
        }
    }

    private fun onMenuClicked() {
        if (lock) {
            return
        }
        if (Core.removeView()) {
            VolumeUtil.vibrate()
        }
    }

    private fun onPrevClicked() {
        if (lock) {
            return
        }
        MediaPlayer.prev()
        VolumeUtil.vibrate()
    }

    private fun onNextClicked() {
        if (lock) {
            return
        }
        MediaPlayer.next()
        VolumeUtil.vibrate()
    }


    private fun slide(slideVal : Int) {
        if (lock) {
            return
        }
        if (onTouchListener?.onSlide(slideVal) == true) {
            VolumeUtil.vibrate()
        }
    }


    private fun onPauseClicked() {
        if (lock) {
            return
        }
        MediaPlayer.pause()
        VolumeUtil.vibrate()
    }

    private fun onEnterLongClicked() {
        if (lock) {
            return
        }
        if (onTouchListener?.onEnterLongClick() == true) {
            VolumeUtil.vibrate()
        }
    }

    private fun onNextLongClicked() {
        if (lock) {
            return
        }
        MediaPlayer.forward()
        VolumeUtil.vibrate()
    }

    private fun onPauseLongClicked() {}

    private fun onPrevLongClicked() {
        if (lock) {
            return
        }
        MediaPlayer.backward()
        VolumeUtil.vibrate()
    }

    private fun onMenuLongClicked() {
        if (lock) {
            return
        }
        Core.home()
        cancelTimer()
        VolumeUtil.vibrate()
    }

    var onTouchListener : OnTouchListener? = null

    interface OnTouchListener {
        fun onEnterClick() : Boolean
        fun onEnterLongClick() : Boolean
        fun onSlide(slideVal: Int) : Boolean
    }
}