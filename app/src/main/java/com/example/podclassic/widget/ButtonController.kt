package com.example.podclassic.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import com.example.podclassic.`object`.Core
import com.example.podclassic.`object`.MediaPlayer
import com.example.podclassic.util.Values
import com.example.podclassic.util.VolumeUtil
import kotlin.math.abs
import kotlin.math.atan2

import kotlin.math.min
import kotlin.math.sqrt

class ButtonController : androidx.appcompat.widget.AppCompatImageView {
    constructor(context : Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var centerX = 0f
    private var centerY = 0f
    private var center = 0f
    private var minR = 0f
    private var maxR = 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val x = MeasureSpec.getSize(widthMeasureSpec)
        val y = MeasureSpec.getSize(heightMeasureSpec)

        val width = min(x, y)
        centerX = width / 2f
        centerY = centerX
        center = centerX


        maxR = width / 2f
        minR = maxR / 16 * 5

        setMeasuredDimension(width, width)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return super.onTouchEvent(event)
        }


        if (event.action != MotionEvent.ACTION_DOWN) {
            return super.onTouchEvent(event)
        }
        val x = event.x - centerX
        val y = event.y - centerY

        val r = sqrt(x * x + y * y)
        if (r > maxR) {
            return super.onTouchEvent(event)
        }

        var deg = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toInt()
        if (deg < 0) {
            deg += 360
        }


        val inCenter = r >= 0 && r < minR
        val inCircle = r in minR..maxR
        if (inCenter) {
            if (MediaPlayer.getPlayList().size == 0) {
                MediaPlayer.shufflePlay()
            } else {
                MediaPlayer.pause()
            }
        } else if (inCircle) {
            when (deg) {
                in 45..135 -> { VolumeUtil.volumeDown() }
                in 135..225 -> { MediaPlayer.prev() }
                in 225..315 -> { VolumeUtil.volumeUp() }
                in 315..360 -> { MediaPlayer.next() }
                in 0..45 -> { MediaPlayer.next() }
            }
        }
        VolumeUtil.vibrate(this)
        return true
    }
}