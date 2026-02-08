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
        private const val MAX_SIZE = 15
        private const val CENTER_INDEX = MAX_SIZE / 2
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
            duration = 250
            interpolator = OvershootInterpolator(0.3f)
            addUpdateListener { animation ->
                val offset = animation.animatedValue as Float
                updatePositionByDrag(offset)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // 回弹动画结束后，确保数据同步
                    updateAllImageViews()
                    updateCenterText()
                    animator = null
                }
                override fun onAnimationCancel(animation: Animator) {
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
        val maxOffset = imagePadding * 2
        val offset = deltaX.toInt().coerceIn(-maxOffset, maxOffset)
        currentDragOffset = offset

        // 实时更新所有图片位置和旋转
        for (i in imageViews.indices) {
            val imageView = imageViews[i]
            val relativeIndex = i - CENTER_INDEX
            val albumIndex = currentCenterAlbumIndex + relativeIndex
            val baseCenterX = imageCenterX + relativeIndex * imagePadding
            updateImageViewPosition(baseCenterX + offset, imageView, albumIndex)
        }
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
        // 只绑定可见范围内的ImageView（中心前后3个），减少图片加载数量
        val visibleRange = 3
        for (i in imageViews.indices) {
            val relativeIndex = i - CENTER_INDEX
            // 只绑定可见范围内的ImageView
            if (relativeIndex in -visibleRange..visibleRange) {
                val imageView = imageViews[i]
                // 使用滑动前的中心索引计算专辑索引
                val albumIndex = oldCenterIndex + relativeIndex
                // 只绑定数据，不更新位置
                if (albumIndex in 0 until albums.size) {
                    imageView.bindItem(albums[albumIndex])
                } else {
                    imageView.bindItem(null)
                }
            }
        }
        
        // 找到中心ImageView
        val centerImageView = imageViews[CENTER_INDEX]
        
        // 记录中心ImageView的初始状态
        val initialScale = centerImageView.scaleX
        val initialAlpha = centerImageView.alpha
        
        // 使用 ValueAnimator 实现动画，与 snapBackToPosition 保持一致
        val targetValue = -slideVal * imagePadding
        
        animator = ValueAnimator.ofFloat(0f, targetValue.toFloat()).apply {
            duration = animationDuration
            // 使用线性插值器，让动画更流畅
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val currentValue = animation.animatedValue as Float

                // 更新所有图片位置
                for (i in imageViews.indices) {
                    val imageView = imageViews[i]
                    val relativeIndex = i - CENTER_INDEX
                    val baseCenterX = imageCenterX + relativeIndex * imagePadding
                    val currentCenterX = baseCenterX + currentValue.toInt()

                    if (i == CENTER_INDEX) {
                        // 中心专辑：只更新位置，不更新缩放（保持 0.7f）
                        updateImageViewPositionForAnim(currentCenterX, imageView)
                        // 滚动过程中主专辑保持缩小状态（0.7f），丝滑滚动
                        imageView.scaleX = 0.7f
                        imageView.scaleY = 0.7f
                        imageView.alpha = 0.9f
                    } else {
                        // 其他专辑：正常更新位置和缩放
                        updateImageViewPosition(currentCenterX, imageView, 0)
                    }
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // 动画结束后，更新中心文本（唱片名）
                    currentDragOffset = 0
                    updateCenterText()

                    // 找到实际在中心位置的 ImageView（根据当前绑定的专辑数据）
                    val centerAlbum = albums.getOrNull(currentCenterAlbumIndex)
                    var actualCenterImageView: CoverImageView? = null

                    // 确保所有专辑先重置为 0.7f
                    for (imageView in imageViews) {
                        imageView.animate().cancel()
                        imageView.scaleX = 0.7f
                        imageView.scaleY = 0.7f
                        imageView.alpha = 0.9f

                        // 检查是否是当前中心专辑
                        if (imageView.getTag(R.id.tag_album) == centerAlbum) {
                            actualCenterImageView = imageView
                        }
                    }

                    // 主专辑从 0.7 放大到 1.0
                    actualCenterImageView?.animate()
                        ?.scaleX(1.0f)
                        ?.scaleY(1.0f)
                        ?.alpha(1.0f)
                        ?.setDuration(200)
                        ?.setInterpolator(DecelerateInterpolator())
                        ?.start()

                    // 更新边界外的图片视图数据（visibleRange范围外的），避免显示旧图片
                    updateBoundaryImageViews()

                    animator = null
                }
                override fun onAnimationCancel(animation: Animator) {
                    // 动画取消时也需要更新边界图片
                    currentDragOffset = 0
                    updateBoundaryImageViews()
                    animator = null
                }
            })
        }
        animator?.start()
    }

    private fun updateImageViewPosition(centerX: Int, imageView: CoverImageView, @Suppress("UNUSED_PARAMETER") albumIndex: Int) {
        // 计算与中心的距离，用于确定层级和透明度
        val distance = abs(imageCenterX - centerX)
        val direction = if (centerX < imageCenterX) -1 else if (centerX > imageCenterX) 1 else 0
        
        // 添加尺寸渐变效果
        // 主专辑旁边的所有专辑统一使用 0.7f 缩放系数
        val scale = if (distance > 0) 0.7f else 1.0f
        
        // 调整旋转效果：保持明显但不过度
        val rotationAngle = if (distance > 0) {
            val maxRotation = 45f
        val rotationFactor = min(distance.toFloat() / imagePadding, 1.0f)
        -direction * maxRotation * rotationFactor
        } else {
            0f
        }
        
        // 计算透明度：距离中心越近，透明度越高
        // 主唱片透明度0.9，其他唱片最高0.7，最低0.6
        val alpha = if (distance == 0) {
            0.9f
        } else {
            val alphaRange = imagePadding * 2
            if (alphaRange > 0) {
                (0.7f - (distance.toFloat() / alphaRange) * 0.1f).coerceIn(0.6f, 0.7f)
            } else {
                0.7f
            }
        }
        
        // 添加透视效果：远离中心的专辑封面添加X轴平移
        val perspectiveOffset = if (distance > 0) {
            val perspectiveFactor = min(distance.toFloat() / (imagePadding * 1.5f), 1.0f)
            direction * halfImageWidth * 0.3f * perspectiveFactor // 减小透视偏移
        } else {
            0f
        }
        
        // 计算布局位置（考虑透视效果）
        val actualHalfWidth = max(1, halfImageWidth)
        val scaledHalfWidth = actualHalfWidth * scale
        val left = centerX + perspectiveOffset - scaledHalfWidth
        val top = imageCenterY - scaledHalfWidth
        val right = centerX + perspectiveOffset + scaledHalfWidth
        val bottom = imageCenterY + scaledHalfWidth
        
        // 只在位置真正改变时才更新布局
        val leftInt = left.toInt()
        val topInt = top.toInt()
        val rightInt = right.toInt()
        val bottomInt = bottom.toInt()
        
        if (imageView.left != leftInt || imageView.top != topInt || imageView.right != rightInt || imageView.bottom != bottomInt) {
            imageView.layout(leftInt, topInt, rightInt, bottomInt)
        }
        
        // 设置其他属性
        imageView.scaleX = scale
        imageView.scaleY = scale
        imageView.rotationY = rotationAngle
        // 调整Z轴深度差异
        imageView.z = -distance.toFloat() * 2
        imageView.alpha = alpha
    }
    
    private fun updateImageViewPositionAndData(centerX: Int, imageView: CoverImageView, albumIndex: Int) {
        // 更新位置
        updateImageViewPosition(centerX, imageView, albumIndex)

        // 更新ImageView的数据
        if (albumIndex in 0 until albums.size) {
            imageView.bindItem(albums[albumIndex])
        } else {
            imageView.bindItem(null)
        }
    }

    /**
     * 动画过程中使用：只更新位置，不更新缩放和透明度
     * 用于中心专辑在滚动动画中保持固定缩放
     */
    private fun updateImageViewPositionForAnim(centerX: Int, imageView: CoverImageView) {
        // 计算与中心的距离，用于确定层级
        val distance = abs(imageCenterX - centerX)
        val direction = if (centerX < imageCenterX) -1 else if (centerX > imageCenterX) 1 else 0

        // 添加透视效果：远离中心的专辑封面添加X轴平移
        val perspectiveOffset = if (distance > 0) {
            val perspectiveFactor = min(distance.toFloat() / (imagePadding * 1.5f), 1.0f)
            direction * halfImageWidth * 0.3f * perspectiveFactor
        } else {
            0f
        }

        // 计算布局位置（使用固定缩放 0.7f 计算）
        val actualHalfWidth = max(1, halfImageWidth)
        val scaledHalfWidth = actualHalfWidth * 0.7f
        val left = centerX + perspectiveOffset - scaledHalfWidth
        val top = imageCenterY - scaledHalfWidth
        val right = centerX + perspectiveOffset + scaledHalfWidth
        val bottom = imageCenterY + scaledHalfWidth

        // 只在位置真正改变时才更新布局
        val leftInt = left.toInt()
        val topInt = top.toInt()
        val rightInt = right.toInt()
        val bottomInt = bottom.toInt()

        if (imageView.left != leftInt || imageView.top != topInt || imageView.right != rightInt || imageView.bottom != bottomInt) {
            imageView.layout(leftInt, topInt, rightInt, bottomInt)
        }

        // 只更新旋转和Z轴，不更新缩放和透明度（由动画控制）
        val rotationAngle = if (distance > 0) {
            val maxRotation = 45f
            val rotationFactor = min(distance.toFloat() / imagePadding, 1.0f)
            -direction * maxRotation * rotationFactor
        } else {
            0f
        }
        imageView.rotationY = rotationAngle
        imageView.z = -distance.toFloat() * 2
    }

    private fun updateAllImageViews() {
        // 更新所有图片数据和位置
        // 主专辑与两边专辑的间距更大，两边专辑之间更紧密
        val centerGap = (imagePadding * 1.3f).toInt()  // 主专辑与旁边专辑的间距
        for (i in imageViews.indices) {
            val imageView = imageViews[i]
            val relativeIndex = i - CENTER_INDEX
            val albumIndex = currentCenterAlbumIndex + relativeIndex
            // 计算位置：主专辑旁边的使用更大的间距，其他的使用标准间距
            val baseCenterX = when (relativeIndex) {
                0 -> imageCenterX
                1, -1 -> imageCenterX + relativeIndex * centerGap
                else -> {
                    val direction = if (relativeIndex > 0) 1 else -1
                    imageCenterX + direction * centerGap + (relativeIndex - direction) * imagePadding
                }
            }
            updateImageViewPositionAndData(baseCenterX, imageView, albumIndex)
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

    private fun updateBoundaryImageViews() {
        // 只更新visibleRange范围外的图片视图数据，避免显示旧图片
        val visibleRange = 3
        for (i in imageViews.indices) {
            val relativeIndex = i - CENTER_INDEX
            // 只处理visibleRange范围外的ImageView
            if (relativeIndex !in -visibleRange..visibleRange) {
                val imageView = imageViews[i]
                val albumIndex = currentCenterAlbumIndex + relativeIndex
                // 只绑定数据，不更新位置（避免闪烁）
                if (albumIndex in 0 until albums.size) {
                    imageView.bindItem(albums[albumIndex])
                } else {
                    imageView.bindItem(null)
                }
            }
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

        imageWidth = measuredHeight / 4 * 3
        halfImageWidth = imageWidth / 2

        // 根据屏幕宽高比动态调整imagePadding，确保在横屏下也有合适的间距
        val screenRatio = measuredWidth.toFloat() / measuredHeight.toFloat()
        imagePadding = if (screenRatio > 1.5f) {
            (measuredWidth - imageWidth) / 5
        } else if (screenRatio > 1.0f) {
            (measuredWidth - imageWidth) / 4
        } else {
            // 竖屏：增大间距，让主专辑与两边专辑之间留出更多空隙
            (measuredWidth - imageWidth) / 3
        }
        
        // 确保 imagePadding 至少为 50，避免间距过小导致的滑动问题
        imagePadding = max(imagePadding, 50)

        imageCenterX = measuredWidth / 2
        imageCenterY = measuredHeight / 2
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
