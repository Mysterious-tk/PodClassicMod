package com.example.podclassic.widget

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import com.example.podclassic.base.ScreenView
import com.example.podclassic.view.MainView

/**
 * iOS 26 风格玻璃效果屏幕容器
 * 支持 API 31+ 使用渐变绘制实现玻璃效果
 * 低版本使用普通样式
 */
class Screen(context: Context, attributeSet: AttributeSet?) : LinearLayout(context, attributeSet) {

    private val mainView = MainView(context)
    var currentView: ScreenView = mainView

    private val screenLayout = ScreenLayout(context)
    private val titleBar = TitleBar(context)

    // 玻璃效果参数
    private val cornerRadius = 24f
    private val isGlassEffectSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // 渐变画笔 - 用于光泽效果
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
    }

    // 边框画笔
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    // 背景渐变 - 半透明毛玻璃效果
    private val glassBackgroundGradient = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(
            Color.argb((255 * 0.75).toInt(), 250, 250, 252),
            Color.argb((255 * 0.65).toInt(), 245, 245, 248),
            Color.argb((255 * 0.60).toInt(), 240, 240, 245),
            Color.argb((255 * 0.55).toInt(), 235, 235, 242)
        )
    ).apply {
        cornerRadius = this@Screen.cornerRadius
    }

    init {
        // 设置背景为透明
        setBackgroundColor(Color.TRANSPARENT)
        orientation = VERTICAL

        // 添加标题栏 - 使用weight=1保持原始比例
        addView(titleBar, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        // 添加屏幕布局 - 使用weight=9保持原始比例
        addView(screenLayout, LayoutParams(LayoutParams.MATCH_PARENT, 0, 9f))

        // 设置圆角裁剪
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View?, outline: Outline?) {
                outline?.setRoundRect(0, 0, width, height, cornerRadius)
            }
        }
        clipToOutline = true

        // 设置视图变化监听器
        screenLayout.onViewChangeListener = object : ScreenLayout.OnViewChangeListener {
            override fun onViewDelete(view: View) {
                (view as ScreenView).onViewDelete()
                view.getObserver()?.onViewDelete()
            }

            override fun onViewCreate(view: View) {
                (view as ScreenView).onViewCreate()
                view.getObserver()?.onViewCreate()
            }

            override fun onViewAdd(view: View) {
                (view as ScreenView).onViewAdd()
                view.getObserver()?.onViewAdd()
            }

            override fun onViewRemove(view: View) {
                (view as ScreenView).onViewRemove()
                view.getObserver()?.onViewRemove()
            }
        }

        screenLayout.add(mainView as View)
        setTitle()
    }

    override fun dispatchDraw(canvas: Canvas) {
        // 先绘制玻璃背景效果
        if (isGlassEffectSupported) {
            drawGlassBackground(canvas)
        } else {
            // 低版本使用普通背景
            drawNormalBackground(canvas)
        }

        // 绘制内容（子视图）- 保持清晰
        super.dispatchDraw(canvas)

        // 在内容之上绘制光泽和边框效果
        if (isGlassEffectSupported) {
            drawGlossOverlay(canvas)
            drawBorder(canvas)
        }
    }

    /**
     * 绘制玻璃背景 - 使用渐变模拟毛玻璃效果
     */
    private fun drawGlassBackground(canvas: Canvas) {
        // 绘制基础渐变背景
        glassBackgroundGradient.setBounds(0, 0, width, height)
        glassBackgroundGradient.draw(canvas)
    }

    /**
     * 绘制普通背景（低版本兼容）
     */
    private fun drawNormalBackground(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        canvas.drawRoundRect(
            0f, 0f, width.toFloat(), height.toFloat(),
            cornerRadius, cornerRadius, paint
        )
    }

    /**
     * 绘制光泽叠加层 - 模拟iOS 26的玻璃光泽
     */
    private fun drawGlossOverlay(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()

        // 顶部高光渐变 - 模拟光源从上方照射
        val topGlossGradient = LinearGradient(
            0f, 0f,
            0f, height * 0.35f,
            intArrayOf(
                Color.argb((255 * 0.50).toInt(), 255, 255, 255),
                Color.argb((255 * 0.20).toInt(), 255, 255, 255),
                Color.argb((255 * 0.05).toInt(), 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.3f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )

        glossPaint.shader = topGlossGradient
        canvas.drawRoundRect(
            0f, 0f, width, height,
            cornerRadius, cornerRadius,
            glossPaint
        )

        // 对角线光泽 - 增加立体感
        val diagonalGloss = LinearGradient(
            0f, 0f,
            width * 0.6f, height * 0.9f,
            intArrayOf(
                Color.argb((255 * 0.15).toInt(), 255, 255, 255),
                Color.argb((255 * 0.05).toInt(), 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )

        glossPaint.shader = diagonalGloss
        canvas.drawRoundRect(
            0f, 0f, width, height,
            cornerRadius, cornerRadius,
            glossPaint
        )

        // 底部微反射
        val bottomReflection = LinearGradient(
            0f, height * 0.75f,
            0f, height,
            intArrayOf(
                Color.argb(0, 255, 255, 255),
                Color.argb((255 * 0.06).toInt(), 255, 255, 255),
                Color.argb((255 * 0.10).toInt(), 220, 225, 235)
            ),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )

        glossPaint.shader = bottomReflection
        canvas.drawRoundRect(
            0f, 0f, width, height,
            cornerRadius, cornerRadius,
            glossPaint
        )
    }

    /**
     * 绘制玻璃边框
     */
    private fun drawBorder(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()

        // 外边框 - 半透明白色
        borderPaint.color = Color.argb((255 * 0.65).toInt(), 255, 255, 255)
        borderPaint.strokeWidth = 1.5f
        canvas.drawRoundRect(
            1f, 1f, width - 1f, height - 1f,
            cornerRadius - 1f, cornerRadius - 1f,
            borderPaint
        )

        // 内边框 - 更亮的线条增加深度感
        borderPaint.color = Color.argb((255 * 0.35).toInt(), 255, 255, 255)
        borderPaint.strokeWidth = 1f
        canvas.drawRoundRect(
            3f, 3f, width - 3f, height - 3f,
            cornerRadius - 3f, cornerRadius - 3f,
            borderPaint
        )

        // 顶部高光边框 - 更亮
        val topHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.argb((255 * 0.60).toInt(), 255, 255, 255)
        }

        val path = android.graphics.Path().apply {
            moveTo(cornerRadius, 1.5f)
            lineTo(width - cornerRadius, 1.5f)
        }
        canvas.drawPath(path, topHighlight)
    }

    private fun setTitle() {
        val title = currentView.getTitle()
        if (title == null) {
            titleBar.showTime()
        } else {
            titleBar.showTitle(title)
        }
    }

    private fun removeView(): ScreenView? {
        return if (currentView == mainView) {
            null
        } else {
            val view = screenLayout.remove()
            currentView = view as ScreenView
            currentView
        }
    }

    private fun getCurrent(): ScreenView {
        return currentView
    }

    private fun clearViews() {
        screenLayout.removeAll()
        currentView = mainView
        screenLayout.add(mainView as View)
    }

    override fun addView(child: View) {
        screenLayout.add(child)
        currentView = child as ScreenView
    }

    fun add(view: ScreenView): Boolean {
        if (view.getLaunchMode() == ScreenView.LAUNCH_MODE_SINGLE) {
            screenLayout.removeInstanceOf(view.javaClass)
        }
        addView(view as View)
        setTitle()
        // 通知MainActivity更新布局
        (context as? com.example.podclassic.activity.MainActivity)?.initView()
        return true
    }

    fun remove(): Boolean {
        val view = removeView()
        return if (view != null) {
            setTitle()
            // 通知MainActivity更新布局
            (context as? com.example.podclassic.activity.MainActivity)?.initView()
            true
        } else {
            false
        }
    }

    fun get(): ScreenView {
        return currentView
    }

    fun home(): Boolean {
        clearViews()
        setTitle()
        // 通知MainActivity更新布局
        (context as? com.example.podclassic.activity.MainActivity)?.initView()
        return true
    }
}
