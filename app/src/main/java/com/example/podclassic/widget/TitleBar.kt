package com.example.podclassic.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.LinearGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.BatteryManager
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.example.podclassic.base.Observer
import com.example.podclassic.service.MediaPresenter
import com.example.podclassic.storage.SPManager
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

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val statusRect = RectF()
    private var glassShader: Shader? = null
    private var glossShader: Shader? = null
    private val density = resources.displayMetrics.density
    private val themeId get() = SPManager.getInt(SPManager.Theme.SP_NAME)
    private val isIpod3rdTheme get() = themeId == SPManager.Theme.IPOD_3RD.id
    private val isClassic3gTheme get() = SPManager.Theme.isClassic3g(themeId)
    private val usesThirdGenerationLayout
        get() = SPManager.Theme.usesThirdGenerationLayout(themeId)
    private val batteryVerticalInset
        get() =
        if (isIpod3rdTheme) (2f * density).toInt() else (2f * density).toInt()
    private val batteryWidthRatio get() = if (usesThirdGenerationLayout) 2.2f else 1.8f

    private val title = TextView(context)
    private val playState = ImageView(context)
    private val battery = BatteryView(context, usesThirdGenerationLayout)

    private val observer = Observer()
    private val onDataChangeListener = object : LiveData.OnDataChangeListener {
        override fun onDataChange() {
            onPlayStateChange()
        }
    }

    init {
        // 玻璃效果下使用透明背景
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setWillNotDraw(false)
        // A full-width label can center screen titles while the clock remains left aligned.
        addView(
            title,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.CENTER
                setMargins(0, 0, 0, 0)
            })
        // 电池在右边
        addView(
            battery,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.END
                setMargins(0, batteryVerticalInset, DEFAULT_PADDING, batteryVerticalInset)
            })
        // 播放状态图标在电池左边
        addView(
            playState,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setMargins(0, 0, DEFAULT_PADDING * 7, 0)
            })

        // iPod Classic 风格的标题文字
        title.textSize = if (usesThirdGenerationLayout) 12.5f else 15f
        title.setTextColor(Colors.text)
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        if (!isClassic3gTheme) {
            title.setShadowLayer(0.7f, 0f, density * 0.5f, Color.argb(190, 255, 255, 255))
        }
        playState.scaleType = ImageView.ScaleType.CENTER_INSIDE
        playState.setPadding((2f * density).toInt(), (2f * density).toInt(), (2f * density).toInt(), (2f * density).toInt())
        playState.imageAlpha = 220
        if (isClassic3gTheme) {
            playState.setColorFilter(Color.rgb(9, 39, 67))
        }

        MediaPresenter.playState.addObserver(observer, onDataChangeListener)
        observer.enable = true
    }
    
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateLayoutForOrientation()
        battery.updateBatteryStatus()  // 配置变化时更新电池状态
    }
    
    private fun updateLayoutForOrientation() {
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        
        // 更新电池布局
        val batteryParams = battery.layoutParams as LayoutParams
        batteryParams.setMargins(
            0,
            batteryVerticalInset,
            if (isLandscape) DEFAULT_PADDING / 2 else DEFAULT_PADDING,
            batteryVerticalInset
        )
        battery.layoutParams = batteryParams
        updatePlayStateMargin(height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updatePlayStateMargin(h)
        updateShaders(w, h)
    }

    private fun updateShaders(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        glassShader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            if (isClassic3gTheme) {
                intArrayOf(
                    Color.rgb(169, 211, 236),
                    Color.rgb(139, 193, 226),
                    Color.rgb(112, 171, 211)
                )
            } else if (isIpod3rdTheme) {
                intArrayOf(
                    Color.argb(202, 255, 255, 255),
                    Color.argb(154, 248, 250, 253),
                    Color.argb(128, 228, 233, 241)
                )
            } else {
                intArrayOf(
                    Color.argb(184, 255, 255, 255),
                    Color.argb(132, 248, 250, 253),
                    Color.argb(108, 224, 231, 241)
                )
            },
            floatArrayOf(0f, 0.46f, 1f),
            Shader.TileMode.CLAMP
        )
        glossShader = LinearGradient(
            -w * 0.08f, 0f, w * 0.72f, h.toFloat(),
            intArrayOf(Color.argb(118, 255, 255, 255), Color.argb(30, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 0.38f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    fun refreshTheme() {
        title.textSize = if (usesThirdGenerationLayout) 12.5f else 15f
        title.setTextColor(Colors.text)
        if (isClassic3gTheme) {
            title.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            playState.setColorFilter(Color.rgb(9, 39, 67))
        } else {
            title.setShadowLayer(
                0.7f,
                0f,
                density * 0.5f,
                Color.argb(190, 255, 255, 255)
            )
            playState.clearColorFilter()
        }
        battery.compact = usesThirdGenerationLayout
        updateLayoutForOrientation()
        updateShaders(width, height)
        invalidate()
        battery.invalidate()
    }

    /** Keep the playback state immediately to the left of the variable-width battery. */
    private fun updatePlayStateMargin(titleBarHeight: Int) {
        if (titleBarHeight <= 0) return

        val batteryParams = battery.layoutParams as LayoutParams
        val batteryHeight =
            (titleBarHeight - batteryParams.topMargin - batteryParams.bottomMargin).coerceAtLeast(0)
        val batteryWidth = (batteryHeight * batteryWidthRatio).toInt()
        val playStateParams = playState.layoutParams as LayoutParams
        playStateParams.setMargins(
            0,
            0,
            batteryParams.rightMargin + batteryWidth + DEFAULT_PADDING / 2,
            0
        )
        playState.layoutParams = playStateParams
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
        title.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        title.setPadding(DEFAULT_PADDING, 0, (84f * density).toInt(), 0)
        registerTimeBroadcastReceiver()
    }

    fun showTitle(title: String) {
        showTime = false
        unregisterTimeBroadcastReceiver()
        this.title.gravity = Gravity.CENTER
        val reservedStatusWidth = (78f * density).toInt()
        this.title.setPadding(reservedStatusWidth, 0, reservedStatusWidth, 0)
        this.title.text = title
    }

    private var batteryBroadcastReceiverRegistered = false
    private var timeBroadcastReceiverRegistered = false
    private var showTime = false

    private fun onStart() {
        //MediaPlayer.addOnMediaChangeListener(this)
        MediaPresenter.playState.addObserver(observer, onDataChangeListener)

        registerBatteryBroadcastReceiver()
        battery.updateBatteryStatus()  // 主动查询当前电池状态
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
        if (width <= 0 || height <= 0) return

        if (isClassic3gTheme) {
            drawClassicLcdTitleBar(canvas)
            return
        }

        // Translucent material with a directional highlight instead of an opaque metal strip.
        paint.shader = glassShader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = glossShader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        // The transport and battery read as one compact floating status surface.
        val capsuleInset = 2f * density
        statusRect.set(
            width - 76f * density,
            capsuleInset,
            width - 4f * density,
            height - capsuleInset
        )
        statusPaint.shader = LinearGradient(
            statusRect.left, statusRect.top, statusRect.left, statusRect.bottom,
            Color.argb(76, 255, 255, 255), Color.argb(34, 210, 220, 234), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(statusRect, statusRect.height() / 2f, statusRect.height() / 2f, statusPaint)
        statusPaint.shader = null
        statusPaint.style = Paint.Style.STROKE
        statusPaint.strokeWidth = 0.65f * density
        statusPaint.color = Color.argb(110, 255, 255, 255)
        statusRect.inset(0.35f * density, 0.35f * density)
        canvas.drawRoundRect(statusRect, statusRect.height() / 2f, statusRect.height() / 2f, statusPaint)
        statusPaint.style = Paint.Style.FILL

        edgePaint.color = Color.argb(176, 255, 255, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), 0.65f * density, edgePaint)
        edgePaint.shader = LinearGradient(
            0f, 0f, width.toFloat(), 0f,
            intArrayOf(Color.TRANSPARENT, Color.argb(96, 64, 78, 96), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, height - 0.75f * density, width.toFloat(), height.toFloat(), edgePaint)
        edgePaint.shader = null
    }

    private fun drawClassicLcdTitleBar(canvas: Canvas) {
        paint.shader = glassShader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        edgePaint.color = Color.argb(110, 218, 242, 252)
        canvas.drawRect(0f, 0f, width.toFloat(), 0.55f * density, edgePaint)
        edgePaint.color = Color.argb(190, 9, 42, 70)
        canvas.drawRect(
            0f,
            height - 0.8f * density,
            width.toFloat(),
            height.toFloat(),
            edgePaint
        )
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
            battery.applyBatteryStatus(intent)
        }
    }

    class BatteryView(context: Context, compact: Boolean = false) : View(context) {

        var compact: Boolean = compact
            set(value) {
                if (field == value) return
                field = value
                requestLayout()
                invalidate()
            }

        private val paint by lazy { Paint(Paint.ANTI_ALIAS_FLAG) }
        private val chargingPath = Path()

        var isCharging = false
        var batteryLevel = 100

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val classic3g = SPManager.Theme.isClassic3g(
                SPManager.getInt(SPManager.Theme.SP_NAME)
            )
            val padding = height * if (compact) 0.27f else 0.2f

            val batteryBodyRight = width - padding * 3
            val radius = (height - padding * 2f) * 0.25f
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.color =
                if (classic3g) Color.argb(20, 4, 30, 52)
                else Color.argb(42, 20, 28, 38)
            canvas.drawRoundRect(0f, padding, batteryBodyRight, height - padding, radius, radius, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (height * 0.07f).coerceAtLeast(1f)
            paint.color =
                if (classic3g) Color.rgb(6, 34, 58)
                else Color.argb(175, 52, 61, 72)
            canvas.drawRoundRect(0f, padding, batteryBodyRight, height - padding, radius, radius, paint)
            paint.style = Paint.Style.FILL
            paint.color =
                if (classic3g) Color.rgb(6, 34, 58)
                else Color.argb(160, 52, 61, 72)
            canvas.drawRoundRect(
                width - padding * 3 - 2f,
                padding * 1.5f,
                width - padding * 2.5f + 2f,
                height - padding * 1.5f,
                4f,
                4f,
                paint
            )
            val batteryWidth = batteryLevel.coerceIn(0, 100) * batteryBodyRight / 100f

            paint.shader = if (classic3g) {
                null
            } else when {
                (batteryLevel <= 20) -> Colors.getShader(
                    0f,
                    0f,
                    0f,
                    height / 1.8f,
                    Colors.theme_white,
                    Colors.battery_red
                )
                (batteryLevel <= 40) -> Colors.getShader(
                    0f,
                    0f,
                    0f,
                    height / 1.8f,
                    Colors.theme_white,
                    Colors.battery_yellow
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
            if (classic3g) {
                paint.color = Color.rgb(7, 39, 66)
            }
            if (batteryWidth > 4f) {
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

            paint.shader = null
            paint.color =
                if (classic3g) Color.argb(38, 207, 235, 249)
                else Color.argb(128, 255, 255, 255)
            canvas.drawRoundRect(
                2f,
                padding + 2f,
                (batteryWidth - 2f).coerceAtLeast(2f),
                padding + (height - padding * 2f) * 0.34f,
                radius,
                radius,
                paint
            )

            if (isCharging) {
                drawChargingIndicator(canvas, batteryBodyRight, padding)
            }
        }

        private fun drawChargingIndicator(canvas: Canvas, batteryBodyRight: Float, padding: Float) {
            val centerX = batteryBodyRight / 2f
            val top = padding + 3f
            val bottom = height - padding - 3f
            val indicatorHeight = (bottom - top).coerceAtLeast(1f)
            val halfWidth = indicatorHeight * 0.22f

            chargingPath.reset()
            chargingPath.moveTo(centerX + halfWidth * 0.25f, top)
            chargingPath.lineTo(centerX - halfWidth, top + indicatorHeight * 0.55f)
            chargingPath.lineTo(centerX - halfWidth * 0.15f, top + indicatorHeight * 0.55f)
            chargingPath.lineTo(centerX - halfWidth * 0.25f, bottom)
            chargingPath.lineTo(centerX + halfWidth, top + indicatorHeight * 0.42f)
            chargingPath.lineTo(centerX + halfWidth * 0.15f, top + indicatorHeight * 0.42f)
            chargingPath.close()

            paint.shader = null
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            canvas.drawPath(chargingPath, paint)
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
            setMeasuredDimension((height * if (compact) 2.2f else 1.8f).toInt(), height)
        }

        fun refreshBattery() {
            invalidate()
        }

        fun applyBatteryStatus(statusIntent: Intent) {
            val scale = statusIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            batteryLevel = (statusIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) * 100 / scale)
                .coerceIn(0, 100)

            val plugged = statusIntent.getIntExtra(
                BatteryManager.EXTRA_PLUGGED,
                0
            )
            val status = statusIntent.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            )
            isCharging = plugged != 0 ||
                status == BatteryManager.BATTERY_STATUS_CHARGING
            refreshBattery()
        }

        fun updateBatteryStatus() {
            val batteryStatus: Intent? = try {
                context.registerReceiver(
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )
            } catch (e: Exception) {
                null
            }

            batteryStatus?.let {
                applyBatteryStatus(it)
            }
        }
    }
}
