package com.example.game.core

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.ViewGroup
import com.example.game.util.Colors
import com.example.podclassic.util.ThreadUtil
import java.util.*

class GameView : ViewGroup {
    companion object {
        const val FPS = 120L
    }

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
        init()
    }

    fun init() {
        setBackgroundColor(Color.WHITE)
    }

    private var max_x = 0
    private var max_y = 0

    private val objectList = ObjectList()

    fun addObject(o: Object) {
        objectList.add(o)
        addView(o.view)
    }

    fun reset() {
        stop()
        objectList.clear()
        removeAllViews()
    }

    fun removeObject(o: Object) {
        objectList.remove(o)
        removeView(o.view)
    }

    fun moveObject(o: Object, offsetX: Int, offsetY: Int) {
        o.x += offsetX
        o.y += offsetY
    }

    fun autoMoveObject(o: Object, velX: Int, velY: Int) {
        o.autoMove.velX = velX
        o.autoMove.velY = velY
    }

    var timer: Timer? = null

    fun start() {
        timer?.cancel()
        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                ThreadUtil.runOnUiThread { refresh() }

            }
        }, 0, 1000 / FPS)

    }

    fun stop() {
        //animator?.pause()
        timer?.cancel()
        timer = null
    }

    private fun refreshObject(o: Object) {
        if (!o.visibility) {
            return
        }

        if (o.autoMove.moving()) {
            o.x += o.autoMove.velX
            o.y += o.autoMove.velY
        }

        if (o.limited) {
            if (o.x < 0) {
                o.x = 0
                o.onLimitedListener?.onLimited(width, height, o)
            } else if (o.x + o.width > width) {
                o.x = width - o.width
                o.onLimitedListener?.onLimited(width, height, o)
            }

            if (o.y < 0) {
                o.y = 0
                o.onLimitedListener?.onLimited(width, height, o)
            } else if (o.y + o.height > height) {
                o.y = height - o.height
                o.onLimitedListener?.onLimited(width, height, o)
            }
        }

        if (o.onHitListener != null) {
            for (i in 0 until objectList.size()) {
                val obj = objectList.get(i)
                if (obj == o) {
                    continue
                }
                if (!obj.visibility) {
                    continue
                }
                if (!obj.hitable) {
                    continue
                }
                if (obj.x + obj.width < o.x) {
                    continue
                }
                if (obj.x > o.x + o.width) {
                    continue
                }
                if (obj.y > o.y + o.height) {
                    continue
                }
                if (obj.y + obj.height < o.y) {
                    continue
                }
                if (o.x + o.width >= obj.x && o.x <= obj.x) {
                    o.x = obj.x - 1 - o.width
                }
                if (o.x <= obj.x + obj.width && o.x + o.width >= obj.x + obj.width) {
                    o.x = obj.x + obj.width + 1
                }
                if (o.y + o.height >= obj.y && o.y <= obj.y) {
                    o.y = obj.y - 1 - o.height
                }
                if (o.y <= obj.y + obj.height && o.y + o.height >= obj.y + obj.height) {
                    o.y = obj.y + obj.height + 1
                }

                o.onHitListener?.onHit(o, obj)
            }
        }
        o.view.layout(o.x, o.y, o.x + o.width, o.y + o.height)
    }

    fun refresh() {
        for (i in 0 until objectList.size()) {
            refreshObject(objectList.get(i))
        }
        gameChecker?.checkEnd()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        max_x = measuredWidth
        max_y = measuredHeight
        if (measuredWidth / measuredHeight > 16 / 9) {
            max_x = measuredHeight / 9 * 16
        }
        setMeasuredDimension(max_x, max_y)
    }

    val paint = Paint()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.shader = Colors.getShader(
            width / 2f,
            0f,
            width / 2f,
            height.toFloat(),
            Colors.background_1,
            Colors.background_2
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    interface GameChecker {
        fun checkEnd()
    }

    var gameChecker: GameChecker? = null

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        refresh()
    }
}