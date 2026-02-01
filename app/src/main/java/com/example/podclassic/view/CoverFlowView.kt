package com.example.podclassic.view

import android.animation.Animator
import android.animation.ValueAnimator
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
import com.example.podclassic.view.MusicListView
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
        private const val CENTER_OFFSET = 7
        private const val DEFAULT_DURATION = 300L
        private const val MIN_DURATION = 10L
        private const val SWIPE_THRESHOLD = 60f
        private const val FLING_VELOCITY_THRESHOLD = 300f

        private val threadPoolExecutor =
            ThreadPoolExecutor(10, 10, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue())
    }

    // 数据相关
    private val albums = MediaStoreUtil.getAlbumList()
    private var index = -CENTER_OFFSET

    // 视图相关
    private val imageViews = ArrayList<ImageView>(MAX_SIZE)
    private val album = TextView(context)
    private val artist = TextView(context)

    // 布局相关
    private var imageWidth = 0
    private var halfImageWidth = 0
    private var imageBottom = 0
    private var imageCenter = 0
    private var imagePadding = 0
    private var textHeight = 0
    private var centerY = 0

    // 触摸操作相关
    private lateinit var gestureDetector: GestureDetector
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isTouching = false
    private var currentOffset = 0

    // 转动圆盘相关
    private var animator: ValueAnimator? = null
    private var duration = DEFAULT_DURATION
    private var slides = 0
    private var lastSlideVal = 0
    private val startValues = Array(MAX_SIZE) { 0 }
    private val interpolator = DecelerateInterpolator(1.5f)

    init {
        initViews()
        initGestureDetector()
        initTouchListener()
    }

    private fun initViews() {
        // 初始化图片视图
        for (i in 0 until MAX_SIZE) {
            val imageView = ImageView(context)
            imageViews.add(imageView)
            addView(imageView)
        }

        // 初始化文本视图
        album.gravity = Gravity.CENTER_HORIZONTAL
        artist.gravity = Gravity.CENTER_HORIZONTAL
        addView(album)
        addView(artist)

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
            MotionEvent.ACTION_DOWN -> handleActionDown(event)
            MotionEvent.ACTION_MOVE -> handleActionMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleActionUp(event)
        }
        return true
    }

    private fun handleActionDown(event: MotionEvent) {
        touchStartX = event.x
        touchStartY = event.y
        isTouching = true
        currentOffset = 0
    }

    private fun handleActionMove(event: MotionEvent) {
        if (!isTouching) return

        val deltaX = event.x - touchStartX
        val deltaY = event.y - touchStartY

        // 检查是否是垂直移动，避免误触
        if (abs(deltaY) > abs(deltaX) * 0.5f) {
            return
        }

        // 跟随手指实时滑动
        updatePositionByDrag(deltaX)
    }

    private fun handleActionUp(event: MotionEvent) {
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
        resetTouchState()
    }
    
    private fun finishDrag(deltaX: Float, velocity: Float) {
        val direction = if (deltaX > 0) -1 else 1
        
        // 根据速度调整动画持续时间，实现惯性减速效果
        val velocityMagnitude = abs(velocity)
        val minDuration = MIN_DURATION
        val maxDuration = DEFAULT_DURATION
        
        // 速度越快，动画持续时间越短，实现惯性减速效果
        duration = (maxDuration - min((velocityMagnitude / 10), (maxDuration - minDuration).toFloat())).toLong()
        duration = duration.coerceIn(minDuration, maxDuration)
        
        // 检查边界，确保不会超出专辑列表范围
        val newCenterIndex = index + CENTER_OFFSET + direction
        if (newCenterIndex in 0 until albums.size) {
            // 调用 slide 方法处理滑动，确保与其他滑动操作一致
            slide(direction)
        }
    }

    private fun resetTouchState() {
        isTouching = false
        currentOffset = 0
    }

    private fun handleCenterClick() {
        if (animator?.isRunning == true) {
            // 动画正在运行，取消动画并重置
            cancelAnimation()
            requestLayout()
        } else {
            // 动画没有运行，进入歌单
            enter()
        }
    }

    private fun updatePositionByDrag(deltaX: Float) {
        // 计算偏移量（限制范围）
        val maxOffset = imagePadding * 2
        val offset = deltaX.toInt().coerceIn(-maxOffset, maxOffset)
        currentOffset = offset

        // 实时更新所有图片位置和旋转
        for (i in imageViews.indices) {
            val imageView = imageViews[i]
            val baseCenterX = imageCenter + (i - CENTER_OFFSET) * imagePadding
            updateImageViewPosition(baseCenterX + offset, imageView)
        }
    }

    private fun snapBackToPosition() {
        // 创建回弹动画
        val startOffset = currentOffset
        val snapAnimator = ValueAnimator.ofInt(startOffset, 0).apply {
            duration = 250
            interpolator = OvershootInterpolator(0.3f)
            addUpdateListener { animation ->
                val offset = animation.animatedValue as Int
                for (i in imageViews.indices) {
                    val imageView = imageViews[i]
                    val baseCenterX = imageCenter + (i - CENTER_OFFSET) * imagePadding
                    updateImageViewPosition(baseCenterX + offset, imageView)
                }
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator?) {}
                override fun onAnimationEnd(animation: Animator?) {
                    // 回弹动画结束后，触发一个动画，让专辑封面对正屏幕
                    alignCoversToCenter()
                }
                override fun onAnimationCancel(animation: Animator?) {}
                override fun onAnimationRepeat(animation: Animator?) {}
            })
        }
        snapAnimator.start()
    }

    // 转动圆盘操作处理
    override fun slide(slideVal: Int): Boolean {
        // 计算新的中心索引
        val newCenterIndex = index + CENTER_OFFSET + slideVal

        // 检查边界
        if (newCenterIndex < 0 || newCenterIndex >= albums.size) {
            return false
        }

        if (animator?.isRunning == true) {
            handleAnimationInProgress(slideVal)
            return true
        }

        // 直接更新索引
        index += slideVal
        
        // 直接更新所有图片数据，包括屏幕范围外的图片
        // 保存所有图片的当前旋转角度
        val rotations = FloatArray(imageViews.size)
        val zValues = FloatArray(imageViews.size)
        for (i in imageViews.indices) {
            rotations[i] = imageViews[i].rotationY
            zValues[i] = imageViews[i].z
        }
        
        // 更新专辑数据
        for (i in imageViews.indices) {
            val imageView = imageViews[i]
            val offset = i - CENTER_OFFSET
            val albumIndex = index + CENTER_OFFSET + offset
            if (albumIndex in 0 until albums.size) {
                imageView.bindItem(albums[albumIndex])
                // 恢复旋转角度
                imageView.rotationY = rotations[i]
                imageView.z = zValues[i]
            } else {
                imageView.bindItem(null)
            }
        }
        
        // 刷新中心文本，确保专辑名与封面对应
        refreshCenterText()
        
        // 开始动画，动画结束后会更新文本
        return loadAnimation(slideVal)
    }

    private fun handleAnimationInProgress(slideVal: Int) {
        // 计算新的中心索引
        val newCenterIndex = index + CENTER_OFFSET + slideVal

        // 检查边界
        if (newCenterIndex < 0 || newCenterIndex >= albums.size) {
            return
        }

        // 取消当前动画
        cancelAnimation()

        // 直接更新索引
        index += slideVal
        
        // 直接更新所有图片数据，包括屏幕范围外的图片
        // 保存所有图片的当前旋转角度
        val rotations = FloatArray(imageViews.size)
        val zValues = FloatArray(imageViews.size)
        for (i in imageViews.indices) {
            rotations[i] = imageViews[i].rotationY
            zValues[i] = imageViews[i].z
        }
        
        // 更新专辑数据
        for (i in imageViews.indices) {
            val imageView = imageViews[i]
            val offset = i - CENTER_OFFSET
            val albumIndex = index + CENTER_OFFSET + offset
            if (albumIndex in 0 until albums.size) {
                imageView.bindItem(albums[albumIndex])
                // 恢复旋转角度
                imageView.rotationY = rotations[i]
                imageView.z = zValues[i]
            } else {
                imageView.bindItem(null)
            }
        }
        
        // 刷新中心文本，确保专辑名与封面对应
        refreshCenterText()

        // 开始新动画，移动距离与索引更新一致
        loadAnimation(slideVal)
    }

    private fun loadAnimation(slideVal: Int): Boolean {
        // 创建动画
        animator = ValueAnimator.ofInt(0, -slideVal * imagePadding).apply {
            addListener(animationListener)
            interpolator = this@CoverFlowView.interpolator
            duration = this@CoverFlowView.duration
        }

        // 记录起始位置
        for (i in imageViews.indices) {
            startValues[i] = imageViews[i].centerX()
        }

        // 记录滑动方向
        lastSlideVal = slideVal

        // 添加更新监听器
        animator?.addUpdateListener { animation ->
            val currentValue = animation.animatedValue as Int
            for (i in imageViews.indices) {
                val imageView = imageViews[i]
                updateImageViewPosition(startValues[i] + currentValue, imageView)
            }
        }

        // 开始动画，动画结束后会根据实际显示的图片更新文本
        animator?.start()
        return true
    }

    private val animationListener = object : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator?) {}

        override fun onAnimationEnd(animation: Animator?) {
            // 触发一个动画，让专辑封面对正屏幕
            alignCoversToCenter()
        }

        override fun onAnimationCancel(animation: Animator?) {
            resetAnimationState()
        }
        override fun onAnimationRepeat(animation: Animator?) {}
    }

    private fun cancelAnimation() {
        animator?.cancel()
        resetAnimationState()
    }

    private fun resetAnimationState() {
        animator = null
        slides = 0
        duration = DEFAULT_DURATION
    }

    // 工具方法
    private fun calculateFlingSlideCount(velocityX: Float): Int {
        return when {
            abs(velocityX) > FLING_VELOCITY_THRESHOLD * 4 -> 4
            abs(velocityX) > FLING_VELOCITY_THRESHOLD * 3 -> 3
            abs(velocityX) > FLING_VELOCITY_THRESHOLD * 2 -> 2
            abs(velocityX) > FLING_VELOCITY_THRESHOLD -> 1
            else -> 0
        }
    }

    private fun flingSlide(direction: Int, count: Int) {
        // 计算新的中心索引
        val newCenterIndex = index + CENTER_OFFSET + direction * count

        // 检查边界
        if (newCenterIndex < 0 || newCenterIndex >= albums.size) {
            return
        }

        // 直接更新索引
        index += direction * count
        
        // 直接更新所有图片数据，包括屏幕范围外的图片
        // 保存所有图片的当前旋转角度
        val rotations = FloatArray(imageViews.size)
        val zValues = FloatArray(imageViews.size)
        for (i in imageViews.indices) {
            rotations[i] = imageViews[i].rotationY
            zValues[i] = imageViews[i].z
        }
        
        // 更新专辑数据
        for (i in imageViews.indices) {
            val imageView = imageViews[i]
            val offset = i - CENTER_OFFSET
            val albumIndex = index + CENTER_OFFSET + offset
            if (albumIndex in 0 until albums.size) {
                imageView.bindItem(albums[albumIndex])
                // 恢复旋转角度
                imageView.rotationY = rotations[i]
                imageView.z = zValues[i]
            } else {
                imageView.bindItem(null)
            }
        }
        
        // 刷新中心文本，确保专辑名与封面对应
        refreshCenterText()
        
        // 开始动画，移动距离与索引更新一致
        loadAnimation(direction * count)
    }

    private fun updateImageViewPosition(centerX: Int, imageView: ImageView) {
        // 计算与中心的距离，用于确定层级和透明度
        val distance = abs(imageCenter - centerX)
        
        // 先设置缩放和位置
        val scale = -distance / 4
        val left = centerX - halfImageWidth - scale
        val top = centerY - halfImageWidth - scale
        val right = centerX + halfImageWidth + scale
        val bottom = centerY + halfImageWidth + scale
        
        // 只在位置真正改变时才更新布局
        if (imageView.left != left || imageView.top != top || imageView.right != right || imageView.bottom != bottom) {
            imageView.layout(left, top, right, bottom)
        }
        
        // 计算旋转角度
        val temp = (imageCenter - centerX).toFloat()
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
    }

    private fun setRotationY(imageView: ImageView) {
        val centerX = imageView.centerX()
        val temp = (imageCenter - centerX).toFloat()

        // 只有当位置变化时才更新旋转和z轴
        val lastCenterX = imageView.getTag(R.id.tag_last_center_x) as? Int
        if (lastCenterX != centerX) {
            imageView.setTag(R.id.tag_last_center_x, centerX)

            if (temp == 0f) {
                imageView.rotationY = 0f
            } else {
                val x = temp / width.toFloat()
                val rotation = sgn(x) * min(abs(x) * 200f, 40f)
                imageView.rotationY = rotation
            }
            imageView.z = -abs(temp)
        }
    }

    private fun updateAlpha(imageView: ImageView) {
        val centerX = imageView.centerX()
        val distance = abs(imageCenter - centerX)
        val maxDistance = imagePadding * 3 // 最大距离，可根据实际情况调整
        
        // 距离中心越远，透明度越低，最低为0.7f
        val alpha = 1.0f - (distance.toFloat() / maxDistance) * 0.3f
        imageView.alpha = alpha.coerceIn(0.7f, 1.0f)
    }

    private fun scaleImageView(centerX: Int, imageView: ImageView) {
        val scale = -abs(imageCenter - centerX) / 4
        val left = centerX - halfImageWidth - scale
        val top = centerY - halfImageWidth - scale
        val right = centerX + halfImageWidth + scale
        val bottom = centerY + halfImageWidth + scale

        // 只有当位置真正改变时才更新布局
        if (imageView.left != left || imageView.top != top || imageView.right != right || imageView.bottom != bottom) {
            imageView.layout(left, top, right, bottom)
        }
    }

    private fun refreshAlbums() {
        // 使用新的 refreshAlbumsData 方法更新图片数据
        refreshAlbumsData()
        
        // 根据索引更新文本，确保文本与图片同步
        refreshCenterText()
    }

    private fun refreshAlbumsWithoutResetRotation() {
        // 更新所有图片数据，但保持当前的旋转角度
        for (i in imageViews.indices) {
            val imageView = imageViews[i]
            val offset = i - CENTER_OFFSET
            val albumIndex = index + CENTER_OFFSET + offset
            if (albumIndex in 0 until albums.size) {
                // 保存当前的旋转角度
                val currentRotationY = imageView.rotationY
                val currentZ = imageView.z
                imageView.bindItem(albums[albumIndex])
                // 恢复旋转角度（bindItem可能会重置）
                imageView.rotationY = currentRotationY
                imageView.z = currentZ
            } else {
                imageView.bindItem(null)
            }
        }
    }

    private fun updateCenterText() {
        val centerIndex = index + CENTER_OFFSET
        if (centerIndex in 0 until albums.size) {
            album.text = albums[centerIndex].title
            artist.text = albums[centerIndex].subtitle
        }
    }

    private fun refreshCenterText() {
        // 找到当前显示在中心位置的唱片
        var closestImageView: ImageView? = null
        var minDistance = Int.MAX_VALUE
        
        for (imageView in imageViews) {
            val distance = abs(imageView.centerX() - imageCenter)
            if (distance < minDistance) {
                minDistance = distance
                closestImageView = imageView
            }
        }
        
        // 从当前显示在中心位置的唱片中获取对应的album对象
        val album = closestImageView?.getTag(R.id.tag_album) as? MusicList
        if (album != null) {
            // 更新专辑名和艺术家名
            this.album.text = album.title
            this.artist.text = album.subtitle
            
            // 更新索引，确保下次进入时使用正确的索引
            val albumIndex = albums.indexOfFirst { it.id == album.id }
            if (albumIndex != -1) {
                index = albumIndex - CENTER_OFFSET
            }
        } else {
            // 如果没有找到对应的专辑，尝试根据index计算
            val centerIndex = index + CENTER_OFFSET
            if (centerIndex in 0 until albums.size) {
                val centerAlbum = albums[centerIndex]
                this.album.text = centerAlbum.title
                this.artist.text = centerAlbum.subtitle
            }
        }
    }
    
    private fun refreshAlbumsData() {
        // 更新所有图片数据
        for (i in imageViews.indices) {
            val imageView = imageViews[i]
            // 正确计算每个图片位置对应的专辑索引
            val offset = i - CENTER_OFFSET
            val albumIndex = index + CENTER_OFFSET + offset
            if (albumIndex in 0 until albums.size) {
                imageView.bindItem(albums[albumIndex])
            } else {
                imageView.bindItem(null)
            }
        }
    }

    private fun alignCoversToCenter() {
        // 检查是否已经有动画在运行，如果有，就直接返回
        if (animator?.isRunning == true) {
            return
        }
        
        // 找到当前显示在中心位置的唱片
        var closestImageView: ImageView? = null
        var minDistance = Int.MAX_VALUE
        var closestIndex = 0
        
        // 记录所有图片的初始位置和当前绑定的专辑
        val initialPositions = IntArray(imageViews.size)
        val currentAlbums = ArrayList<MusicList?>(imageViews.size)
        for ((i, imageView) in imageViews.withIndex()) {
            val distance = abs(imageView.centerX() - imageCenter)
            if (distance < minDistance) {
                minDistance = distance
                closestImageView = imageView
                closestIndex = i
            }
            initialPositions[i] = imageView.centerX()
            currentAlbums.add(imageView.getTag(R.id.tag_album) as? MusicList)
        }
        
        // 计算需要调整的偏移量
        val targetIndex = closestIndex - CENTER_OFFSET
        val offset = targetIndex * imagePadding
        
        // 计算当前中心封面的旋转角度，用于自适应动画时间
        val currentRotation = closestImageView?.rotationY ?: 0f
        val rotationAngle = abs(currentRotation)
        
        // 如果旋转角度很小（小于5度），则认为已经接近对正，使用最小动画时间
        val isAlmostAligned = rotationAngle < 5f
        
        // 计算自适应动画时间：根据旋转角度和移动距离，确保动画平滑
        val baseDuration = if (isAlmostAligned) 200L else 300L // 基础动画时间，接近对正时使用更短时间
        val rotationDuration = if (isAlmostAligned) 50L else (rotationAngle * 3).toLong() // 旋转角度越大，动画时间越长
        val distanceDuration = (abs(offset) / imagePadding * 80).toLong() // 移动距离越长，动画时间越长
        val totalDuration = max(baseDuration, min(rotationDuration + distanceDuration, 800L)) // 限制最大动画时间
        
        // 如果偏移量很小且已经接近对正，则不需要动画
        if (abs(offset) < 10 && isAlmostAligned) {
            // 直接设置为对正状态
            closestImageView?.rotationY = 0f
            closestImageView?.z = 0f
            resetAnimationState()
            return
        }
        
        // 创建动画，让专辑封面对正屏幕
        val alignAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = totalDuration
            interpolator = DecelerateInterpolator(1.2f)
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                for (i in imageViews.indices) {
                    val imageView = imageViews[i]
                    // 计算当前位置：从初始位置平滑过渡到目标位置
                    val targetCenterX = imageCenter + (i - CENTER_OFFSET) * imagePadding - offset
                    val currentCenterX = (initialPositions[i] + (targetCenterX - initialPositions[i]) * progress).toInt()
                    updateImageViewPosition(currentCenterX, imageView)
                }
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator?) {}
                override fun onAnimationEnd(animation: Animator?) {
                    // 动画结束后，确保中心封面完全对正屏幕
                    closestImageView?.rotationY = 0f
                    closestImageView?.z = 0f
                    
                    // 刷新专辑名，确保与封面匹配
                    refreshCenterText()
                    
                    resetAnimationState()
                }
                override fun onAnimationCancel(animation: Animator?) {
                    resetAnimationState()
                }
                override fun onAnimationRepeat(animation: Animator?) {}
            })
        }
        
        // 保存当前动画引用
        animator = alignAnimator
        alignAnimator.start()
    }

    private fun sgn(x: Float): Float {
        return when {
            x < 0f -> -1f
            x > 0f -> 1f
            else -> 0f
        }
    }

    private fun s(x: Float): Float {
        return sqrt(x) / sqrt(x + 1)
    }

    // 布局相关
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        imageWidth = measuredHeight / 4 * 3
        halfImageWidth = imageWidth / 2
        imageBottom = measuredHeight / 2 + halfImageWidth

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

        imageCenter = measuredWidth / 2
        centerY = imageBottom - halfImageWidth
        textHeight = measuredHeight / 10
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (!changed) {
            return
        }

        // 布局图片
        for (i in imageViews.indices) {
            val imageView = imageViews[i]
            val baseCenterX = imageCenter + (i - CENTER_OFFSET) * imagePadding
            updateImageViewPosition(baseCenterX, imageView)
        }

        // 布局文本
        artist.layout(0, bottom - textHeight, width, bottom)
        album.layout(0, artist.top - textHeight, width, artist.top)

        // 刷新数据
        refreshAlbums()
    }

    // 界面交互
    override fun enter(): Boolean {
        // 优先根据index计算要进入的歌单，确保与圆盘滚动方式一致
        var targetAlbum: MusicList? = null
        val centerIndex = index + CENTER_OFFSET
        
        if (centerIndex in 0 until albums.size) {
            targetAlbum = albums[centerIndex]
        } else if (albums.isNotEmpty()) {
            // 如果中心索引超出范围，使用最近的边界专辑
            targetAlbum = if (centerIndex < 0) {
                albums.first()
            } else {
                albums.last()
            }
        }
        
        // 如果根据index没有找到对应的专辑，再尝试根据图片位置计算
        if (targetAlbum == null) {
            // 找到当前显示在中心位置的唱片
            var closestImageView: ImageView? = null
            var minDistance = Int.MAX_VALUE
            
            for (imageView in imageViews) {
                val distance = abs(imageView.centerX() - imageCenter)
                if (distance < minDistance) {
                    minDistance = distance
                    closestImageView = imageView
                }
            }
            
            // 从当前显示在中心位置的唱片中获取对应的album对象
            targetAlbum = closestImageView?.getTag(R.id.tag_album) as? MusicList
            
            // 如果没有找到对应的专辑，遍历所有可见的图片，找到最接近中心的且有专辑数据的图片
            if (targetAlbum == null) {
                var bestImageView: ImageView? = null
                var bestDistance = Int.MAX_VALUE
                
                for (imageView in imageViews) {
                    val distance = abs(imageView.centerX() - imageCenter)
                    val album = imageView.getTag(R.id.tag_album) as? MusicList
                    if (album != null && distance < bestDistance) {
                        bestDistance = distance
                        bestImageView = imageView
                        targetAlbum = album
                    }
                }
            }
        }
        
        // 进入这个album对应的歌单
        if (targetAlbum != null) {
            Core.addView(MusicListView(context, targetAlbum))
        }
        return true
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

    // 内部ImageView类
    private class ImageView(context: Context) :
        androidx.appcompat.widget.AppCompatImageView(context) {

        private var album: MusicList? = null
        private val defaultBitmap by lazy { getReflectBitmap(Icons.DEFAULT.bitmap) }
        private var runnable: Runnable? = null

        fun centerX(): Int {
            return (left + right) / 2
        }

        fun bindItem(album: MusicList?) {
            if (album != null && album == this.album) {
                // 即使数据相同也要更新tag，确保外部能获取到正确的专辑
                setTag(R.id.tag_album, album)
                return
            }
            this.album = album

            // 设置tag以便外部获取当前绑定的专辑
            setTag(R.id.tag_album, album)

            if (album == null) {
                setImageBitmap(null)
                return
            }

            // 移除旧任务
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
                    if (album == this@ImageView.album) {
                        // 保存当前的旋转角度
                        val currentRotationY = rotationY
                        val currentZ = z
                        if (bitmap != null) {
                            val temp = getReflectBitmap(bitmap)
                            bitmap.recycle()
                            setImageBitmap(temp)
                        } else {
                            setImageBitmap(defaultBitmap)
                        }
                        // 恢复旋转角度
                        rotationY = currentRotationY
                        z = currentZ
                    } else {
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