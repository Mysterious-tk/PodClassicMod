package com.example.podclassic.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import com.example.podclassic.R
import com.example.podclassic.base.Core
import com.example.podclassic.base.ScreenView
import com.example.podclassic.bean.MusicList
import com.example.podclassic.util.MediaStoreUtil
import com.example.podclassic.util.MediaUtil
import com.example.podclassic.util.ThreadUtil
import com.example.podclassic.values.Icons
import com.example.podclassic.values.Strings
import com.example.podclassic.widget.TextView
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CoverFlowView(context: Context) : ScreenView, FrameLayout(context) {
    companion object {
        // iPod Classic真实CoverFlow参数
        // 旋转角度：中间0°，内侧15°，中间30°，外侧45°
        private const val ROTATION_ANGLE_INNER = 15f   // 紧邻中间
        private const val ROTATION_ANGLE_MIDDLE = 30f  // 中间层
        private const val ROTATION_ANGLE_OUTER = 45f   // 最外侧
        
        // 缩放比例：中间1.0，内侧0.85，中间0.70，外侧0.55
        private const val SCALE_CENTER = 1.0f
        private const val SCALE_INNER = 0.85f
        private const val SCALE_MIDDLE = 0.70f
        private const val SCALE_OUTER = 0.55f
        
        // Z轴深度：中间0，内侧30，中间70，外侧120
        private const val Z_DEPTH_INNER = 30f
        private const val Z_DEPTH_MIDDLE = 70f
        private const val Z_DEPTH_OUTER = 120f
        
        // 唱片间距（均匀间距）
        private const val COVER_SPACING = 0.65f

        private const val MAX_SIZE = 7
        private const val CENTER_INDEX = MAX_SIZE / 2  // 3
        private const val DEFAULT_DURATION = 300L
        private const val MIN_DURATION = 10L
        private const val SWIPE_THRESHOLD = 60f
        @Suppress("unused")
        private const val FLING_VELOCITY_THRESHOLD = 300f

        private val threadPoolExecutor = 
            ThreadPoolExecutor(10, 10, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue())
    }

    // 数据相关
    private val albums = MediaStoreUtil.getAlbumList()
    private var currentCenterAlbumIndex = 0

    // 视图相关
    private val imageViews = ArrayList<CoverImageView>(MAX_SIZE)
    private val albumTitle = TextView(context)
    private val artistName = TextView(context)

    // 布局相关
    private var imageWidth = 0
    private var halfImageWidth = 0
    private var imageCenterX = 0
    private var imageCenterY = 0
    private var imagePadding = 0
    private var textHeight = 0

    // 触摸操作相关
    private lateinit var gestureDetector: GestureDetector
    private var isTouching = false
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var currentDragOffset = 0

    // 动画相关
    private var animator: ValueAnimator? = null
    private var animationDuration = DEFAULT_DURATION
    private val interpolator = DecelerateInterpolator(1.5f)

    init {
        initViews()
        initGestureDetector()
        initTouchListener()
    }

    private fun initViews() {
        // 禁用裁剪，确保旋转后的图片能完整显示
        setClipChildren(false)
        setClipToPadding(false)

        // 初始化文本视图（先添加，确保在底层）
        albumTitle.gravity = Gravity.CENTER_HORIZONTAL
        artistName.gravity = Gravity.CENTER_HORIZONTAL
        addView(albumTitle)
        addView(artistName)

        // 初始化图片视图（后添加，确保在上层，不会被文本挡住）
        repeat(MAX_SIZE) {
            val imageView = CoverImageView(context)
            imageViews.add(imageView)
            addView(imageView)
        }

        // 允许点击和获取焦点
        isClickable = true
        isFocusable = true
    }

    private fun initGestureDetector() {
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false

                val diffX = e2.x - e1.x
                if (abs(diffX) > SWIPE_THRESHOLD) {
                    // 普通滑动
                    slide(if (diffX > 0) -1 else 1)
                    return true
                }
                return false
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initTouchListener() {
        setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            handleTouchEvent(event)
            true
        }
    }

    // 触摸操作处理
    @Suppress("SameReturnValue")
    private fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                isTouching = true
                currentDragOffset = 0
                // 取消正在运行的动画
                animator?.cancel()
                animator = null
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isTouching) return true

                val deltaX = event.x - touchStartX
                val deltaY = event.y - touchStartY

                // 检查是否是垂直移动，避免误触
                if (abs(deltaY) > abs(deltaX) * 0.5f) {
                    return true
                }

                // 跟随手指实时滑动
                updatePositionByDrag(deltaX)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val deltaX = event.x - touchStartX
                val deltaTime = event.eventTime - event.downTime
                val velocity = deltaX / deltaTime * 1000 // 转换为像素/秒
                
                // 计算拖动距离的绝对值
                val absDeltaX = abs(deltaX)
                
                // 如果滑动距离超过了阈值，或者拖动距离超过了imagePadding的一半，就滑动到下一张唱片
                if (absDeltaX > SWIPE_THRESHOLD || absDeltaX > imagePadding / 2) {
                    finishDrag(deltaX, velocity)
                } else {
                    snapBackToPosition()
                    handleCenterClick()
                }
                isTouching = false
            }
        }
        return true
    }
    
    private fun finishDrag(deltaX: Float, velocity: Float) {
        val direction = if (deltaX > 0) -1 else 1
        
        // 根据速度调整动画持续时间，实现惯性减速效果
        val velocityMagnitude = abs(velocity)
        val minDuration = MIN_DURATION
        val maxDuration = DEFAULT_DURATION
        
        // 速度越快，动画持续时间越短，实现惯性减速效果
        animationDuration = (maxDuration - min((velocityMagnitude / 10), (maxDuration - minDuration).toFloat())).toLong()
        animationDuration = animationDuration.coerceIn(minDuration, maxDuration)
        
        // 检查边界，确保不会超出专辑列表范围
        val newCenterIndex = currentCenterAlbumIndex + direction
        if (newCenterIndex in 0 until albums.size) {
            // 调用 slide 方法处理滑动
            slide(direction)
        } else {
            // 超出边界，回弹到原位
            snapBackToPosition()
        }
    }

    private fun snapBackToPosition() {
        // 创建回弹动画
        val startOffset = currentDragOffset.toFloat()
        
        animator = ValueAnimator.ofFloat(startOffset, 0f).apply {
            duration = 300
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { animation ->
                val offset = animation.animatedValue as Float
                
                // 回弹过程中使用iPod Classic风格动态效果
                for (i in imageViews.indices) {
                    val imageView = imageViews[i]
                    val relativeIndex = i - CENTER_INDEX
                    
                    // 计算基础X位置（均匀间距）
                    val baseCenterX = imageCenterX + relativeIndex * imageWidth * COVER_SPACING
                    val currentCenterX = baseCenterX + offset
                    
                    // 计算与中心的距离
                    val distanceFromCenter = abs(currentCenterX - imageCenterX)
                    val normalizedDistance = distanceFromCenter / (imageWidth * COVER_SPACING)
                    
                    // 获取目标属性
                    val (targetScale, targetRotation, targetZ, targetAlpha) = getCoverProperties(normalizedDistance, relativeIndex)
                    
                    // 应用属性
                    imageView.scaleX = targetScale
                    imageView.scaleY = targetScale
                    imageView.rotationY = targetRotation
                    imageView.alpha = targetAlpha
                    imageView.z = targetZ
                    
                    // 计算布局位置
                    val actualHalfWidth = max(1, halfImageWidth)
                    val scaledHalfWidth = actualHalfWidth * targetScale
                    
                    // 以边为轴的旋转偏移计算
                    val pivotOffset = when {
                        relativeIndex == 0 -> 0f
                        relativeIndex < 0 -> {
                            val shift = scaledHalfWidth * (1 - kotlin.math.cos(Math.toRadians(targetRotation.toDouble()))).toFloat()
                            shift
                        }
                        else -> {
                            val shift = scaledHalfWidth * (1 - kotlin.math.cos(Math.toRadians((-targetRotation).toDouble()))).toFloat()
                            -shift
                        }
                    }
                    
                    val left = (currentCenterX + pivotOffset - scaledHalfWidth).toInt()
                    val top = (imageCenterY - scaledHalfWidth).toInt()
                    val right = (currentCenterX + pivotOffset + scaledHalfWidth).toInt()
                    val bottom = (imageCenterY + scaledHalfWidth).toInt()
                    
                    imageView.layout(left, top, right, bottom)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentDragOffset = 0
                    updateAllImageViews()
                    updateCenterText()
                    animator = null
                }
                override fun onAnimationCancel(animation: Animator) {
                    currentDragOffset = 0
                    updateAllImageViews()
                    animator = null
                }
            })
        }
        animator?.start()
    }

    private fun handleCenterClick() {
        // 进入歌单
        enter()
    }

    private fun updatePositionByDrag(deltaX: Float) {
        // 计算偏移量（限制范围）
        val maxOffset = imageWidth * COVER_SPACING
        val offset = deltaX.coerceIn(-maxOffset, maxOffset)
        currentDragOffset = offset.toInt()

        // 实时更新所有图片位置和旋转
        for (i in imageViews.indices) {
            val imageView = imageViews[i]
            val relativeIndex = i - CENTER_INDEX
            
            // 计算基础X位置（均匀间距）
            val baseCenterX = imageCenterX + relativeIndex * imageWidth * COVER_SPACING
            val currentCenterX = baseCenterX + offset
            
            // 计算与中心的距离
            val distanceFromCenter = abs(currentCenterX - imageCenterX)
            val normalizedDistance = distanceFromCenter / (imageWidth * COVER_SPACING)
            
            // 获取目标属性
            val (targetScale, targetRotation, targetZ, targetAlpha) = getCoverProperties(normalizedDistance, relativeIndex)
            
            // 应用属性
            imageView.scaleX = targetScale
            imageView.scaleY = targetScale
            imageView.rotationY = targetRotation
            imageView.alpha = targetAlpha
            imageView.z = targetZ
            
            // 计算布局位置
            val actualHalfWidth = max(1, halfImageWidth)
            val scaledHalfWidth = actualHalfWidth * targetScale
            
            // 以边为轴的旋转偏移计算
            val pivotOffset = when {
                relativeIndex == 0 -> 0f
                relativeIndex < 0 -> {
                    val shift = scaledHalfWidth * (1 - kotlin.math.cos(Math.toRadians(targetRotation.toDouble()))).toFloat()
                    shift
                }
                else -> {
                    val shift = scaledHalfWidth * (1 - kotlin.math.cos(Math.toRadians((-targetRotation).toDouble()))).toFloat()
                    -shift
                }
            }
            
            val left = (currentCenterX + pivotOffset - scaledHalfWidth).toInt()
            val top = (imageCenterY - scaledHalfWidth).toInt()
            val right = (currentCenterX + pivotOffset + scaledHalfWidth).toInt()
            val bottom = (imageCenterY + scaledHalfWidth).toInt()
            
            imageView.layout(left, top, right, bottom)
        }
    }

    // 根据距离获取唱片属性
    private fun getCoverProperties(normalizedDistance: Float, relativeIndex: Int): Quadruple<Float, Float, Float, Float> {
        val targetScale = when {
            normalizedDistance < 0.5f -> SCALE_CENTER
            normalizedDistance < 1.5f -> {
                val t = (normalizedDistance - 0.5f)
                SCALE_CENTER - (SCALE_CENTER - SCALE_INNER) * t
            }
            normalizedDistance < 2.5f -> {
                val t = (normalizedDistance - 1.5f)
                SCALE_INNER - (SCALE_INNER - SCALE_MIDDLE) * t
            }
            else -> {
                val t = (normalizedDistance - 2.5f).coerceIn(0f, 1f)
                SCALE_MIDDLE - (SCALE_MIDDLE - SCALE_OUTER) * t
            }
        }
        
        val targetRotation = when {
            normalizedDistance < 0.5f -> 0f
            normalizedDistance < 1.5f -> {
                val t = (normalizedDistance - 0.5f)
                val maxRot = if (relativeIndex < 0) ROTATION_ANGLE_INNER else -ROTATION_ANGLE_INNER
                maxRot * t
            }
            normalizedDistance < 2.5f -> {
                val t = (normalizedDistance - 1.5f)
                val baseRot = if (relativeIndex < 0) ROTATION_ANGLE_INNER else -ROTATION_ANGLE_INNER
                val maxRot = if (relativeIndex < 0) ROTATION_ANGLE_MIDDLE else -ROTATION_ANGLE_MIDDLE
                baseRot + (maxRot - baseRot) * t
            }
            else -> {
                val t = (normalizedDistance - 2.5f).coerceIn(0f, 1f)
                val baseRot = if (relativeIndex < 0) ROTATION_ANGLE_MIDDLE else -ROTATION_ANGLE_MIDDLE
                val maxRot = if (relativeIndex < 0) ROTATION_ANGLE_OUTER else -ROTATION_ANGLE_OUTER
                baseRot + (maxRot - baseRot) * t
            }
        }
        
        val targetZ = when {
            normalizedDistance < 0.5f -> 0f
            normalizedDistance < 1.5f -> {
                val t = (normalizedDistance - 0.5f)
                -Z_DEPTH_INNER * t
            }
            normalizedDistance < 2.5f -> {
                val t = (normalizedDistance - 1.5f)
                -Z_DEPTH_INNER - (Z_DEPTH_MIDDLE - Z_DEPTH_INNER) * t
            }
            else -> {
                val t = (normalizedDistance - 2.5f).coerceIn(0f, 1f)
                -Z_DEPTH_MIDDLE - (Z_DEPTH_OUTER - Z_DEPTH_MIDDLE) * t
            }
        }
        
        val targetAlpha = when {
            normalizedDistance < 0.5f -> 1.0f
            normalizedDistance < 1.5f -> 0.95f
            normalizedDistance < 2.5f -> 0.85f
            else -> 0.75f
        }
        
        return Quadruple(targetScale, targetRotation, targetZ, targetAlpha)
    }

    // 转动圆盘操作处理
    override fun slide(slideVal: Int): Boolean {
        // 计算新的中心索引
        val newCenterIndex = currentCenterAlbumIndex + slideVal

        // 检查边界
        if (newCenterIndex < 0 || newCenterIndex >= albums.size) {
            return false
        }

        // 记录滑动前的中心索引，用于动画过程中计算正确的专辑索引
        val oldCenterIndex = currentCenterAlbumIndex
        
        // 更新中心索引
        currentCenterAlbumIndex = newCenterIndex
        
        // 开始动画，传入滑动前的中心索引
        startSlideAnimation(slideVal, oldCenterIndex)
        
        return true
    }

    private fun startSlideAnimation(slideVal: Int, oldCenterIndex: Int) {
        // 预绑定动画过程中需要显示的图片数据
        for (i in imageViews.indices) {
            val relativeIndex = i - CENTER_INDEX
            val imageView = imageViews[i]
            val albumIndex = oldCenterIndex + relativeIndex
            if (albumIndex in 0 until albums.size) {
                imageView.bindItem(albums[albumIndex])
            } else {
                imageView.bindItem(null)
            }
        }
        
        // 使用 ValueAnimator 实现动画
        val targetValue = -slideVal * imageWidth * COVER_SPACING
        
        animator = ValueAnimator.ofFloat(0f, targetValue).apply {
            duration = animationDuration
            interpolator = DecelerateInterpolator(1.0f)
            addUpdateListener { animation ->
                val currentValue = animation.animatedValue as Float

                // 更新所有图片位置
                for (i in imageViews.indices) {
                    val imageView = imageViews[i]
                    val relativeIndex = i - CENTER_INDEX
                    
                    // 计算基础X位置（均匀间距）
                    val baseCenterX = imageCenterX + relativeIndex * imageWidth * COVER_SPACING
                    val currentCenterX = baseCenterX + currentValue

                    // 计算与中心的距离
                    val distanceFromCenter = abs(currentCenterX - imageCenterX)
                    val normalizedDistance = distanceFromCenter / (imageWidth * COVER_SPACING)
                    
                    // 获取目标属性
                    val (targetScale, targetRotation, targetZ, targetAlpha) = getCoverProperties(normalizedDistance, relativeIndex)

                    // 应用属性
                    imageView.scaleX = targetScale
                    imageView.scaleY = targetScale
                    imageView.rotationY = targetRotation
                    imageView.alpha = targetAlpha
                    imageView.z = targetZ
                    
                    // 计算布局位置
                    val actualHalfWidth = max(1, halfImageWidth)
                    val scaledHalfWidth = actualHalfWidth * targetScale
                    
                    // 以边为轴的旋转偏移计算
                    val pivotOffset = when {
                        relativeIndex == 0 -> 0f
                        relativeIndex < 0 -> {
                            val shift = scaledHalfWidth * (1 - kotlin.math.cos(Math.toRadians(targetRotation.toDouble()))).toFloat()
                            shift
                        }
                        else -> {
                            val shift = scaledHalfWidth * (1 - kotlin.math.cos(Math.toRadians((-targetRotation).toDouble()))).toFloat()
                            -shift
                        }
                    }
                    
                    val left = (currentCenterX + pivotOffset - scaledHalfWidth).toInt()
                    val top = (imageCenterY - scaledHalfWidth).toInt()
                    val right = (currentCenterX + pivotOffset + scaledHalfWidth).toInt()
                    val bottom = (imageCenterY + scaledHalfWidth).toInt()
                    
                    imageView.layout(left, top, right, bottom)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentDragOffset = 0
                    updateCenterText()
                    updateAllImageViews()
                    animator = null
                }
                override fun onAnimationCancel(animation: Animator) {
                    currentDragOffset = 0
                    updateCenterText()
                    updateAllImageViews()
                    animator = null
                }
            })
        }
        animator?.start()
    }

    // 辅助类用于返回多个值
    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun updateAllImageViews() {
        // iPod Classic真实CoverFlow布局
        // 7张唱片水平排列，以边为轴旋转，形成扇形布局
        
        // 计算每张唱片的属性
        val coverData = Array(7) { index ->
            val relativeIndex = index - CENTER_INDEX  // -3, -2, -1, 0, 1, 2, 3
            val distance = abs(relativeIndex)
            
            // 旋转角度：中间0°，内侧15°，中间30°，外侧45°
            val rotation = when {
                relativeIndex == 0 -> 0f
                relativeIndex < 0 -> when (distance) {
                    1 -> ROTATION_ANGLE_INNER   // 左侧第1张：15°
                    2 -> ROTATION_ANGLE_MIDDLE  // 左侧第2张：30°
                    else -> ROTATION_ANGLE_OUTER // 左侧第3张：45°
                }
                else -> when (distance) {
                    1 -> -ROTATION_ANGLE_INNER   // 右侧第1张：-15°
                    2 -> -ROTATION_ANGLE_MIDDLE  // 右侧第2张：-30°
                    else -> -ROTATION_ANGLE_OUTER // 右侧第3张：-45°
                }
            }
            
            // 缩放比例：中间1.0，内侧0.85，中间0.70，外侧0.55
            val scale = when (distance) {
                0 -> SCALE_CENTER
                1 -> SCALE_INNER
                2 -> SCALE_MIDDLE
                else -> SCALE_OUTER
            }
            
            // Z轴深度：中间0，内侧30，中间70，外侧120
            val zDepth = when (distance) {
                0 -> 0f
                1 -> -Z_DEPTH_INNER
                2 -> -Z_DEPTH_MIDDLE
                else -> -Z_DEPTH_OUTER
            }
            
            // 透明度
            val alpha = when (distance) {
                0 -> 1.0f
                1 -> 0.95f
                2 -> 0.85f
                else -> 0.75f
            }
            
            Triple(rotation, scale, zDepth) to alpha
        }
        
        // 计算基础位置（均匀间距）
        val basePositions = FloatArray(7)
        val actualHalfWidth = max(1, halfImageWidth)
        
        // 中间唱片位置
        basePositions[CENTER_INDEX] = imageCenterX.toFloat()
        
        // 计算各位置的X坐标（均匀间距）
        for (i in 0 until 7) {
            if (i == CENTER_INDEX) continue
            
            val relativeIndex = i - CENTER_INDEX
            basePositions[i] = imageCenterX + relativeIndex * actualHalfWidth * 2 * COVER_SPACING
        }
        
        // 应用布局
        for (i in imageViews.indices) {
            val imageView = imageViews[i]
            val relativeIndex = i - CENTER_INDEX
            val distance = abs(relativeIndex)
            val albumIndex = currentCenterAlbumIndex + relativeIndex
            
            // 绑定数据
            if (albumIndex in 0 until albums.size) {
                imageView.bindItem(albums[albumIndex])
            } else {
                imageView.bindItem(null)
            }
            
            val (attrs, alpha) = coverData[i]
            val (rotation, scale, zDepth) = attrs
            
            // 应用属性
            imageView.scaleX = scale
            imageView.scaleY = scale
            imageView.rotationY = rotation
            imageView.alpha = alpha
            imageView.z = zDepth
            
            // 计算布局位置
            val scaledHalfWidth = actualHalfWidth * scale
            val baseX = basePositions[i]
            
            // 以边为轴的旋转偏移计算
            // 左侧唱片以右边为轴，右侧唱片以左边为轴
            val pivotOffset = when {
                relativeIndex == 0 -> 0f
                relativeIndex < 0 -> {
                    // 左侧唱片：以右边为轴向中心旋转
                    val shift = scaledHalfWidth * (1 - kotlin.math.cos(Math.toRadians(rotation.toDouble()))).toFloat()
                    shift
                }
                else -> {
                    // 右侧唱片：以左边为轴向中心旋转
                    val shift = scaledHalfWidth * (1 - kotlin.math.cos(Math.toRadians((-rotation).toDouble()))).toFloat()
                    -shift
                }
            }
            
            val left = (baseX + pivotOffset - scaledHalfWidth).toInt()
            val top = (imageCenterY - scaledHalfWidth).toInt()
            val right = (baseX + pivotOffset + scaledHalfWidth).toInt()
            val bottom = (imageCenterY + scaledHalfWidth).toInt()
            
            imageView.layout(left, top, right, bottom)
        }
    }

    private fun updateCenterText() {
        // 更新中心文本
        if (currentCenterAlbumIndex in 0 until albums.size) {
            val centerAlbum = albums[currentCenterAlbumIndex]
            albumTitle.text = centerAlbum.title
            artistName.text = centerAlbum.subtitle
        }
    }

    private fun sgn(x: Float): Float {
        return when {
            x < 0f -> -1f
            x > 0f -> 1f
            else -> 0f
        }
    }

    // 布局相关
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        // iPod Classic真实CoverFlow：中间唱片占屏幕高度的约70%
        imageWidth = (measuredHeight * 0.70f).toInt()
        halfImageWidth = imageWidth / 2

        // imagePadding在新布局中不再使用，但保留用于兼容
        imagePadding = (imageWidth * COVER_SPACING).toInt()

        imageCenterX = measuredWidth / 2
        imageCenterY = (measuredHeight * 0.42f).toInt()  // 稍微偏上，给文字留空间
        textHeight = measuredHeight / 10
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (!changed) {
            return
        }

        // 布局图片
        updateAllImageViews()

        // 布局文本
        artistName.layout(0, bottom - textHeight, width, bottom)
        albumTitle.layout(0, artistName.top - textHeight, width, artistName.top)

        // 更新中心文本
        updateCenterText()
    }

    // 界面交互
    override fun enter(): Boolean {
        // 进入当前中心专辑对应的歌单
        if (currentCenterAlbumIndex in 0 until albums.size) {
            val targetAlbum = albums[currentCenterAlbumIndex]
            Core.addView(MusicListView(context, targetAlbum))
            return true
        }
        return false
    }

    override fun enterLongClick(): Boolean {
        return false
    }

    override fun getTitle(): String {
        return Strings.COVER_FLOW
    }

    override fun onViewAdd() {
        // 首次进入时确保所有图片视图数据正确绑定
        updateAllImageViews()
    }

    override fun onViewDelete() {
        animator?.cancel()
        threadPoolExecutor.queue.clear()
    }

    // 内部CoverImageView类
    private class CoverImageView(context: Context) :
        androidx.appcompat.widget.AppCompatImageView(context) {

        init {
            // 使用 FIT_CENTER 保持原始比例展示专辑封面
            scaleType = ScaleType.FIT_CENTER
        }

        private var album: MusicList? = null
        private val defaultBitmap by lazy { getReflectBitmap(Icons.DEFAULT.bitmap) }
        private var runnable: Runnable? = null

        fun bindItem(album: MusicList?) {
            // 设置tag以便外部获取当前绑定的专辑
            setTag(R.id.tag_album, album)
            
            // 如果专辑对象相同，直接返回，避免不必要的加载
            if (album == this.album) {
                return
            }
            
            // 更新当前专辑
            this.album = album

            if (album == null) {
                // 使用默认图片而不是 null，避免 BitmapDrawable created with null Bitmap
                setImageBitmap(defaultBitmap)
                return
            }

            // 移除旧任务，避免线程池中有多个任务处理同一ImageView
            runnable?.let {
                threadPoolExecutor.remove(it)
                runnable = null
            }

            // 加载图片
            val currentAlbum = album
            runnable = Runnable {
                val bitmap = MediaUtil.getAlbumImage(currentAlbum.id ?: 0L)
                if (Thread.currentThread().isInterrupted) {
                    bitmap?.recycle()
                    return@Runnable
                }
                ThreadUtil.runOnUiThread {
                    // 再次检查专辑是否仍然相同，避免UI线程处理过时的任务
                    if (currentAlbum == this@CoverImageView.album) {
                        // 保存当前的旋转角度和z值
                        val currentRotationY = rotationY
                        val currentZ = z
                        if (bitmap != null) {
                            val temp = getReflectBitmap(bitmap)
                            bitmap.recycle()
                            setImageBitmap(temp)
                        } else {
                            setImageBitmap(defaultBitmap)
                        }
                        // 恢复旋转角度和z值
                        rotationY = currentRotationY
                        z = currentZ
                    } else {
                        // 专辑已更改，回收bitmap
                        bitmap?.recycle()
                    }
                }
            }
            threadPoolExecutor.execute(runnable)
        }

        @Suppress("DEPRECATION")
        private fun getReflectBitmap(bitmap: Bitmap): Bitmap {
            val reflectionGap = 0
            val width = bitmap.width
            val height = bitmap.height

            val matrix = Matrix()
            matrix.preScale(1f, -1f)
            @Suppress("DEPRECATION", "UseKtx")
            val reflectionImage =
                Bitmap.createBitmap(bitmap, 0, height / 2, width, height / 2, matrix, false)
            @Suppress("DEPRECATION", "UseKtx")
            val bitmap4Reflection =
                Bitmap.createBitmap(width, height + height / 2 + reflectionGap, Bitmap.Config.ARGB_8888)
            val canvasRef = Canvas(bitmap4Reflection)

            // 绘制专辑封面（保持原始比例）
            canvasRef.drawBitmap(bitmap, 0f, 0f, null)

            // 绘制倒影（附加在专辑封面下方）
            canvasRef.drawRect(
                0f,
                height.toFloat(),
                width.toFloat(),
                (height + reflectionGap).toFloat(),
                Paint().apply { isAntiAlias = true }
            )
            canvasRef.drawBitmap(reflectionImage, 0f, (height + reflectionGap).toFloat(), null)
            reflectionImage.recycle()

            // 为倒影添加渐变透明度
            val paint = Paint()
            val shader = LinearGradient(
                0f,
                (height + reflectionGap).toFloat(),
                0f,
                bitmap4Reflection.height.toFloat(),
                0x70ffffff,
                0x00ffffff,
                Shader.TileMode.CLAMP
            )
            paint.shader = shader
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvasRef.drawRect(
                0f,
                height.toFloat(),
                width.toFloat(),
                bitmap4Reflection.height.toFloat(),
                paint
            )

            return bitmap4Reflection
        }
    }
}
