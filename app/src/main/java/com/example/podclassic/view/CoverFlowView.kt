package com.example.podclassic.view

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
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
import kotlin.math.sqrt

class CoverFlowView(context: Context) : ScreenView, FrameLayout(context) {
    companion object {
        private const val MAX_SIZE = 15
        private const val CENTER_INDEX = MAX_SIZE / 2
        private const val DEFAULT_DURATION = 300L
        private const val MIN_DURATION = 10L
        private const val SWIPE_THRESHOLD = 60f
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
        // 初始化图片视图
        for (i in 0 until MAX_SIZE) {
            val imageView = CoverImageView(context)
            imageViews.add(imageView)
            addView(imageView)
        }

        // 初始化文本视图
        albumTitle.gravity = Gravity.CENTER_HORIZONTAL
        artistName.gravity = Gravity.CENTER_HORIZONTAL
        addView(albumTitle)
        addView(artistName)

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

    private fun initTouchListener() {
        setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            handleTouchEvent(event)
            true
        }
    }

    // 触摸操作处理
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
        val startOffset = currentDragOffset
        val snapAnimator = ValueAnimator.ofInt(startOffset, 0).apply {
            duration = 250
            interpolator = OvershootInterpolator(0.3f)
            addUpdateListener { animation ->
                val offset = animation.animatedValue as Int
                updatePositionByDrag(offset.toFloat())
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator?) {}
                override fun onAnimationEnd(animation: Animator?) {
                    // 回弹动画结束后，确保数据同步
                    updateAllImageViews()
                    updateCenterText()
                }
                override fun onAnimationCancel(animation: Animator?) {}
                override fun onAnimationRepeat(animation: Animator?) {}
            })
        }
        snapAnimator.start()
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

        // 更新中心索引
        currentCenterAlbumIndex = newCenterIndex
        
        // 开始动画
        startSlideAnimation(slideVal)
        
        return true
    }

    private fun startSlideAnimation(slideVal: Int) {
        // 创建动画
        animator = ValueAnimator.ofInt(0, -slideVal * imagePadding).apply {
            duration = animationDuration
            interpolator = this@CoverFlowView.interpolator
            addUpdateListener { animation ->
                val currentValue = animation.animatedValue as Int
                for (i in imageViews.indices) {
                    val imageView = imageViews[i]
                    val relativeIndex = i - CENTER_INDEX
                    val albumIndex = currentCenterAlbumIndex + relativeIndex
                    val baseCenterX = imageCenterX + relativeIndex * imagePadding
                    updateImageViewPosition(baseCenterX + currentValue, imageView, albumIndex)
                }
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator?) {}
                override fun onAnimationEnd(animation: Animator?) {
                    // 动画结束后，确保所有数据同步
                    currentDragOffset = 0
                    updateAllImageViews()
                    updateCenterText()
                    animator = null
                }
                override fun onAnimationCancel(animation: Animator?) {
                    animator = null
                }
                override fun onAnimationRepeat(animation: Animator?) {}
            })
        }
        animator?.start()
    }

    private fun updateImageViewPosition(centerX: Int, imageView: CoverImageView, albumIndex: Int) {
        // 计算与中心的距离，用于确定层级和透明度
        val distance = abs(imageCenterX - centerX)
        
        // 先设置缩放和位置
        val scale = -distance / 4
        val left = centerX - halfImageWidth - scale
        val top = imageCenterY - halfImageWidth - scale
        val right = centerX + halfImageWidth + scale
        val bottom = imageCenterY + halfImageWidth + scale
        
        // 只在位置真正改变时才更新布局
        if (imageView.left != left || imageView.top != top || imageView.right != right || imageView.bottom != bottom) {
            imageView.layout(left, top, right, bottom)
        }
        
        // 计算旋转角度
        val temp = (imageCenterX - centerX).toFloat()
        if (temp == 0f) {
            imageView.rotationY = 0f
        } else {
            val x = temp / width.toFloat()
            val rotation = sgn(x) * min(abs(x) * 200f, 40f)
            imageView.rotationY = rotation
        }
        
        // 设置 z 轴顺序，距离中心越近，z 值越大，显示在前面
        imageView.z = -distance.toFloat()
        
        // 设置透明度，距离中心越远，透明度越低
        val maxDistance = imagePadding * 3
        val alpha = 1.0f - (distance.toFloat() / maxDistance) * 0.3f
        imageView.alpha = alpha.coerceIn(0.7f, 1.0f)
        
        // 更新ImageView的数据
        if (albumIndex in 0 until albums.size) {
            imageView.bindItem(albums[albumIndex])
        } else {
            imageView.bindItem(null)
        }
    }

    private fun updateAllImageViews() {
        // 更新所有图片数据和位置
        for (i in imageViews.indices) {
            val imageView = imageViews[i]
            val relativeIndex = i - CENTER_INDEX
            val albumIndex = currentCenterAlbumIndex + relativeIndex
            val baseCenterX = imageCenterX + relativeIndex * imagePadding
            updateImageViewPosition(baseCenterX, imageView, albumIndex)
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

        imageWidth = measuredHeight / 4 * 3
        halfImageWidth = imageWidth / 2

        // 根据屏幕宽高比动态调整imagePadding，确保在横屏下也有合适的间距
        val screenRatio = measuredWidth.toFloat() / measuredHeight.toFloat()
        imagePadding = if (screenRatio > 1.5f) {
            // 横屏模式，使用更大的间距
            (measuredWidth - imageWidth) / 5
        } else if (screenRatio > 1.0f) {
            // 轻微横屏模式
            (measuredWidth - imageWidth) / 4
        } else {
            // 竖屏模式
            (measuredWidth - imageWidth) / 4
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

    override fun onViewDelete() {
        animator?.cancel()
        threadPoolExecutor.queue.clear()
    }

    // 内部CoverImageView类
    private class CoverImageView(context: Context) : 
        androidx.appcompat.widget.AppCompatImageView(context) {

        private var album: MusicList? = null
        private val defaultBitmap by lazy { getReflectBitmap(Icons.DEFAULT.bitmap) }
        private var runnable: Runnable? = null

        fun bindItem(album: MusicList?) {
            // 设置tag以便外部获取当前绑定的专辑
            setTag(R.id.tag_album, album)
            
            // 如果专辑对象相同，直接返回，避免不必要的加载
            if (album != null && album == this.album) {
                return
            }
            this.album = album

            if (album == null) {
                // 清空图片
                setImageBitmap(null)
                return
            }

            // 移除旧任务，避免线程池中有多个任务处理同一ImageView
            if (runnable != null) {
                threadPoolExecutor.remove(runnable)
                runnable = null
            }

            // 加载图片
            runnable = Runnable {
                val bitmap = MediaUtil.getAlbumImage(album.id ?: 0L)
                if (Thread.currentThread().isInterrupted) {
                    bitmap?.recycle()
                    return@Runnable
                }
                ThreadUtil.runOnUiThread {
                    // 再次检查专辑是否仍然相同，避免UI线程处理过时的任务
                    if (album == this@CoverImageView.album) {
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

        private fun getReflectBitmap(bitmap: Bitmap): Bitmap {
            val reflectionGap = 0
            val width = bitmap.width
            val height = bitmap.height
            val matrix = Matrix()
            matrix.preScale(1f, -1f)
            val reflectionImage = 
                Bitmap.createBitmap(bitmap, 0, height / 2, width, height / 2, matrix, false)
            val bitmap4Reflection = 
                Bitmap.createBitmap(width, height + height / 2, Bitmap.Config.ARGB_8888)
            val canvasRef = Canvas(bitmap4Reflection)

            canvasRef.drawBitmap(bitmap, 0f, 0f, null)
            canvasRef.drawRect(
                0f,
                height.toFloat(),
                width.toFloat(),
                height + reflectionGap.toFloat(),
                Paint().apply { isAntiAlias = true }
            )
            canvasRef.drawBitmap(reflectionImage, 0f, height + reflectionGap.toFloat(), null)
            val paint = Paint()
            val shader = LinearGradient(
                0f,
                bitmap.height.toFloat(),
                0f,
                bitmap4Reflection.height.toFloat() + reflectionGap,
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
                (bitmap4Reflection.height + reflectionGap).toFloat(),
                paint
            )
            return bitmap4Reflection
        }
    }
}