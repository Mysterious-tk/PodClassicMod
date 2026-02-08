package com.example.podclassic.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.os.BatteryManager
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.example.podclassic.base.Observer
import com.example.podclassic.service.MediaPresenter
import com.example.podclassic.util.LiveData
import com.example.podclassic.values.Colors
import com.example.podclassic.values.Icons
import com.example.podclassic.values.Values
import com.example.podclassic.values.Values.DEFAULT_PADDING
import java.text.SimpleDateFormat
import java.util.*


class TitleBar : FrameLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet)

    private val paint = Paint()

    private val title = TextView(context)
    private val playState = ImageView(context)
    private val battery = BatteryView(context)

    private val observer = Observer()
    private val onDataChangeListener = object : LiveData.OnDataChangeListener {
        override fun onDataChange() {
            onPlayStateChange()
        }
    }

    init {
        // iPod Classic 风格的银色金属质感背景
        setBackgroundColor(Colors.background_light)
        addView(
            playState,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.START
                setMargins(DEFAULT_PADDING, 0, 0, 0)
            })
        addView(
            battery,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.END
                setMargins(0, DEFAULT_PADDING / 2, DEFAULT_PADDING, DEFAULT_PADDING / 2)
            })
        addView(
            title,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            })

        // iPod Classic 风格的标题文字
        title.textSize = 17f
        title.setTextColor(Colors.text)
        title.setPadding(
            DEFAULT_PADDING * 8,
            DEFAULT_PADDING / 2,
            DEFAULT_PADDING * 8,
            DEFAULT_PADDING / 2
        )
        // 添加微妙的阴影效果
        title.setShadowLayer(0.5f, 0f, 1f, Colors.white)

        MediaPresenter.playState.addObserver(observer, onDataChangeListener)
        observer.enable = true
    }

    private val simpleDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private fun onPlayStateChange() {
        if (MediaPresenter.isPlaying()) {
            playState.setImageDrawable(Icons.PLAY_BLUE.drawable)
        } else {
            playState.setImageDrawable(if (MediaPresenter.getCurrent() == null) null else Icons.PAUSE_BLUE.drawable)
        }
    }

    fun showTime() {
        showTime = true
        registerTimeBroadcastReceiver()
    }

    fun showTitle(title: String) {
        showTime = false
        unregisterTimeBroadcastReceiver()
        this.title.text = title
    }

    private var batteryBroadcastReceiverRegistered = false
    private var timeBroadcastReceiverRegistered = false
    private var showTime = false

    private fun onStart() {
        //MediaPlayer.addOnMediaChangeListener(this)
        MediaPresenter.playState.addObserver(observer, onDataChangeListener)

        registerBatteryBroadcastReceiver()
        if (showTime) {
            registerTimeBroadcastReceiver()
        }
        observer.enable = true
        onPlayStateChange()
    }

    private fun onStop() {
        MediaPresenter.playState.removeObserver(observer)
        //MediaPlayer.removeOnMediaChangeListener(this)
        unregisterBatteryBroadcastReceiver()
        unregisterTimeBroadcastReceiver()
        observer.enable = false
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            onStart()
        } else {
            onStop()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        onStop()
        MediaPresenter.playState.removeObserver(observer)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // iPod Classic 风格：银色金属质感渐变
        paint.shader = Colors.getShader(
            0f,
            0f,
            0f,
            height.toFloat(),
            Colors.background_light,  // 顶部亮银
            Colors.background_dark_1  // 底部稍暗
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        
        // 顶部高光线
        paint.color = Colors.white
        canvas.drawRect(0f, 0f, width.toFloat(), 1f, paint)
        
        // 底部分隔线 - iPod Classic 风格的双线
        paint.color = Colors.background_dark_2
        canvas.drawRect(0f, height - 2f, width.toFloat(), height - 1f, paint)
        paint.color = Colors.divider_light
        canvas.drawRect(0f, height - 1f, width.toFloat(), height.toFloat(), paint)
    }

    private fun registerBatteryBroadcastReceiver() {
        if (!batteryBroadcastReceiverRegistered) {
            val intentFilter = IntentFilter()
            intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED)
            context.registerReceiver(batteryBroadcastReceiver, intentFilter)
            batteryBroadcastReceiverRegistered = true
        }
    }

    private fun unregisterBatteryBroadcastReceiver() {
        if (batteryBroadcastReceiverRegistered) {
            context.unregisterReceiver(batteryBroadcastReceiver)
            batteryBroadcastReceiverRegistered = false
        }
    }

    private fun registerTimeBroadcastReceiver() {
        if (!timeBroadcastReceiverRegistered) {
            val intentFilter = IntentFilter()
            intentFilter.addAction(Intent.ACTION_TIME_TICK)
            context.registerReceiver(timeBroadcastReceiver, intentFilter)
            timeBroadcastReceiverRegistered = true
            title.text = simpleDateFormat.format(Date())
        }
    }

    private fun unregisterTimeBroadcastReceiver() {
        if (timeBroadcastReceiverRegistered) {
            context.unregisterReceiver(timeBroadcastReceiver)
            timeBroadcastReceiverRegistered = false
        }
    }

    private val timeBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            title.text = simpleDateFormat.format(Date())
        }
    }

    private val batteryBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            battery.batteryLevel =
                intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) * 100 / intent.getIntExtra(
                    BatteryManager.EXTRA_SCALE,
                    100
                )
            battery.isCharging = (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) != 0).or(
                intent.getIntExtra(
                    BatteryManager.EXTRA_STATUS,
                    -1
                ) == BatteryManager.BATTERY_STATUS_CHARGING
            )
            battery.refreshBattery()
        }
    }

    class BatteryView(context: Context) : View(context) {

        private val paint by lazy { Paint() }

        var isCharging = false
        var batteryLevel = 100

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val padding = height / 5f

            paint.color = Colors.line
            paint.shader =
                null//Colors.getShader(width / 2f, 0f, width / 2f, height.toFloat(), Colors.line, Colors.line)


            canvas.drawRoundRect(0f, padding, width - padding * 3, height - padding, 4f, 4f, paint)
            canvas.drawRoundRect(
                width - padding * 3 - 2f,
                padding * 1.5f,
                width - padding * 2.5f + 2f,
                height - padding * 1.5f,
                4f,
                4f,
                paint
            )
            //canvas?.drawRoundRect(0f, PADDING, width - PADDING * 5, measuredHeight - PADDING, 4f, 4f, paint)
            //canvas?.drawRoundRect(measuredWidth - PADDING * 5 - 2, PADDING * 1.5f, measuredWidth - PADDING * 6 - 2, measuredHeight - PADDING * 1.5f, 4f, 4f, paint)
            val batteryWidth = batteryLevel * (width - padding * 3) / 100f

            paint.shader = when {
                (isCharging && batteryLevel != 100) -> Colors.getShader(
                    0f,
                    0f,
                    0f,
                    height / 1.8f,
                    Colors.theme_white,
                    Colors.battery_yellow
                )
                (batteryLevel <= 20) -> Colors.getShader(
                    0f,
                    0f,
                    0f,
                    height / 1.8f,
                    Colors.theme_white,
                    Colors.battery_red
                )
                else -> Colors.getShader(
                    0f,
                    0f,
                    0f,
                    height / 1.8f,
                    Colors.theme_white,
                    Colors.battery_green
                )
            }
            canvas.drawRoundRect(
                2f,
                padding + 2,
                batteryWidth - 2,
                height - padding - 2,
                4f,
                4f,
                paint
            )
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)


            /*
            when {
                Values.screenWidth >= Values.RESOLUTION_HIGH -> setMeasuredDimension(160, height)
                Values.screenWidth <= Values.RESOLUTION_LOW -> setMeasuredDimension(60, height)
                else -> setMeasuredDimension(Values.screenWidth, height)
            }
             */
            // 使用固定宽度，避免横屏时被拉长
            setMeasuredDimension((height * 1.8f).toInt(), height)
        }

        fun refreshBattery() {
            invalidate()
        }
    }
}