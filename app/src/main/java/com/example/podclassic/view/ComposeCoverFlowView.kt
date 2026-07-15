package com.example.podclassic.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.example.podclassic.base.Core
import com.example.podclassic.base.ScreenView
import com.example.podclassic.values.Colors
import com.example.podclassic.bean.MusicList
import com.example.podclassic.util.MediaStoreUtil
import com.example.podclassic.util.MediaUtil
import com.example.podclassic.values.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.math.sign

// Conditional debug logging - Phase 1: Quick Wins
// Set to false to disable debug logging in production builds
private const val ENABLE_DEBUG_LOGGING = false

private inline fun debugLog(tag: String, lazyMessage: () -> String) {
    if (ENABLE_DEBUG_LOGGING) {
        Log.d(tag, lazyMessage())
    }
}

// 全局图片缓存 - 使用 LRU 策略避免内存无限增长
private val albumBitmapCache = mutableMapOf<Long, Bitmap?>()
// 倒影图片缓存 - 使用 String 类型的键
private val reflectedBitmapCache = mutableMapOf<String, Bitmap?>()
private const val MAX_CACHE_SIZE = 30

/**
 * Jetpack Compose CoverFlow - 优化版本
 * 使用简单的 graphicsLayer 变换，不使用复杂的 Canvas 透视变换
 */
class ComposeCoverFlowView(context: Context) : FrameLayout(context), ScreenView {

    private val albums = MediaStoreUtil.getAlbumList()

    // 使用MutableState确保Compose能观察到变化
    private val currentIndexState = mutableIntStateOf(
        if (albums.isEmpty()) 0 else 3.coerceAtMost(albums.size - 1)
    )
    private val targetIndexState = mutableIntStateOf(
        if (albums.isEmpty()) 0 else 3.coerceAtMost(albums.size - 1)
    )
    // 浮点动画索引 - 支持平滑插值动画
    private val animatedIndexState = mutableFloatStateOf(
        if (albums.isEmpty()) 0f else 3f.coerceAtMost((albums.size - 1).toFloat())
    )
    // 记录上次滑动时间戳，用于动态计算动画时长
    private val lastSlideTimeState = mutableLongStateOf(0L)
    private val isLandscapeState = mutableStateOf(
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    )

    private lateinit var gestureDetector: GestureDetector

    private val composeView = androidx.compose.ui.platform.ComposeView(context).apply {
        setContent {
            CoverFlowContent(
                albums = albums,
                currentIndexState = currentIndexState,
                targetIndexState = targetIndexState,
                animatedIndexState = animatedIndexState,
                lastSlideTimeState = lastSlideTimeState,
                isLandscape = isLandscapeState.value
            )
        }
    }

    init {
        setBackgroundColor(Colors.white)
        // 添加原生手势处理层
        addTouchHandler()
        // 添加 ComposeView 并设置为填充整个父容器
        addView(
            composeView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        )
    }

    /**
     * 添加原生触摸手势处理
     * 支持：左右滑动切换专辑、点击中间项目进入
     */
    private fun addTouchHandler() {
        val swipeStep = 44f * resources.displayMetrics.density
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        var horizontalDistance = 0f
        var verticalDistance = 0f

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                horizontalDistance = 0f
                verticalDistance = 0f
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                horizontalDistance += distanceX
                verticalDistance += distanceY

                if (abs(horizontalDistance) < touchSlop ||
                    abs(horizontalDistance) <= abs(verticalDistance) * 1.15f
                ) {
                    return true
                }

                while (abs(horizontalDistance) >= swipeStep) {
                    // GestureDetector 的 distanceX：手指向左为正，向右为负。
                    val direction = if (horizontalDistance > 0f) 1 else -1
                    if (!slide(direction)) {
                        horizontalDistance = 0f
                        break
                    }
                    horizontalDistance -= direction * swipeStep
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // 点击中间区域进入专辑
                val centerX = width / 2f
                val centerY = height / 2f
                val tapThreshold = 150f * resources.displayMetrics.density

                val dx = abs(e.x - centerX)
                val dy = abs(e.y - centerY)

                if (dx < tapThreshold && dy < tapThreshold) {
                    return enter()
                }
                return false
            }
        })

        // 确保可以接收点击事件
        isClickable = true
        isFocusable = true
    }

    /**
     * ComposeView 会消费触摸事件，因此在分发给子 View 之前识别手势。
     * CoverFlow 本身没有需要子 View 处理的交互，整页接管事件可保证快划和慢拖都稳定。
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun enter(): Boolean {
        if (currentIndexState.intValue in albums.indices) {
            val targetAlbum = albums[currentIndexState.intValue]
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
        // produceState 会自动处理图片加载，不需要预加载
    }

    override fun onViewDelete() {
    }

    override fun onConfigurationChanged() {
        isLandscapeState.value =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    override fun slide(slideVal: Int): Boolean {
        val newIndex = targetIndexState.intValue + slideVal
        if (newIndex in albums.indices) {
            targetIndexState.intValue = newIndex
            return true
        }
        return false
    }

    companion object {
        // 预加载可见范围的图片（带倒影）
        // 扩展预加载范围到 -3..3，确保所有可见图片都被预加载，减少闪烁
        suspend fun preloadVisibleImages(
            albums: List<MusicList>,
            centerIndex: Int,
            sideCount: Int
        ) {
            Log.d("CoverFlowPreload", "Starting preload for centerIndex=$centerIndex, albums.size=${albums.size}")
            val range = -sideCount..sideCount
            for (offset in range) {
                val index = centerIndex + offset
                if (index in albums.indices) {
                    val albumId = albums[index].id ?: 0L
                    val reflectedCacheKey = "${albumId}_reflected"
                    if (reflectedBitmapCache[reflectedCacheKey] == null) {
                        try {
                            // 限制缓存大小
                            if (reflectedBitmapCache.size >= MAX_CACHE_SIZE) {
                                val firstKey = reflectedBitmapCache.keys.first()
                                reflectedBitmapCache.remove(firstKey)?.recycle()
                            }

                            Log.d("CoverFlowPreload", "Loading albumId=$albumId (offset=$offset)")
                            val originalBmp = MediaUtil.getAlbumImage(albumId)
                            if (originalBmp != null) {
                                // 统一原图大小为600x600，使用CENTER_CROP方式提高质量
                                val normalizedBmp = createSquareBitmapWithCenterCrop(originalBmp, 600)
                                // 创建带倒影的图片
                                val reflectedBmp = createReflectedBitmap(normalizedBmp)
                                // 回收中间图片
                                if (normalizedBmp != originalBmp) {
                                    normalizedBmp.recycle()
                                }
                                reflectedBitmapCache[reflectedCacheKey] = reflectedBmp
                                Log.d("CoverFlowPreload", "Loaded albumId=$albumId: ${reflectedBmp.width}x${reflectedBmp.height}")
                            } else {
                                reflectedBitmapCache[reflectedCacheKey] = null
                                Log.d("CoverFlowPreload", "AlbumId=$albumId has no cover art")
                            }
                        } catch (e: Exception) {
                            reflectedBitmapCache[reflectedCacheKey] = null
                            Log.e("CoverFlowPreload", "Failed to load albumId=$albumId", e)
                        }
                    }
                }
            }
            Log.d("CoverFlowPreload", "Preload complete, cache.size=${reflectedBitmapCache.size}")
        }
    }
}

/**
 * CoverFlow内容组件
 * 使用 smooth animation 实现 Apple 风格的 CoverFlow 滚动效果
 * Phase 2 & 5: Separate animation from layout state, cache density values
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Suppress("NOTICE_TO_DEVELOPERS")
@Composable
private fun CoverFlowContent(
    albums: List<MusicList>,
    currentIndexState: androidx.compose.runtime.MutableIntState,
    targetIndexState: androidx.compose.runtime.MutableIntState,
    animatedIndexState: androidx.compose.runtime.MutableFloatState,
    lastSlideTimeState: androidx.compose.runtime.MutableLongState,
    isLandscape: Boolean
) {
    val density = LocalDensity.current
    val context = LocalContext.current

    // 读取当前目标索引
    val targetIndex by remember {
        derivedStateOf { targetIndexState.intValue }
    }

    // iPod 的切换快速、连续且不会在落点回弹。临界阻尼仍能承接快速滚轮输入，
    // 但去掉了 Compose 默认的“卡片弹簧”观感。
    val animatedIndex by animateFloatAsState(
        targetValue = targetIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 520f
        ),
        label = "coverflow_scroll"
    )

    // 点击与标题都跟随真正到达中心的封面，而不是提前跳到目标专辑。
    LaunchedEffect(animatedIndex) {
        animatedIndexState.floatValue = animatedIndex
        if (albums.isNotEmpty()) {
            currentIndexState.intValue = animatedIndex.roundToInt().coerceIn(albums.indices)
        }
    }

    // 使用BoxWithConstraints获取实际可用尺寸
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight

        // Phase 5: 缓存密度相关的计算值
        // 将 configuration.orientation 添加到 key 中，确保横屏时重新计算布局
        val cachedMetrics = remember(containerWidth, containerHeight, density, isLandscape) {
            // 显示尺寸150dp，处理分辨率600px（由Compose自动缩放）
            val coverSize = 150.dp
            val coverSizePx = with(density) { coverSize.toPx() }
            val screenWidthPx = with(density) { containerWidth.toPx() }
            val screenHeightPx = with(density) { containerHeight.toPx() }
            val isPortrait = !isLandscape

            // ✅ 间距计算：基于布局宽度（ImageView的实际尺寸）
            val maxOffset = 3f
            val calculatedSpacing = (screenWidthPx - coverSizePx) / (maxOffset * 2f)

            // Debug log metrics calculation
            debugLog("CoverFlowMetrics") {
                """Metrics Calculation:
                   | containerWidth (dp): $containerWidth
                   | containerHeight (dp): $containerHeight
                   | isLandscape: $isLandscape
                   | density: ${density.density}
                   | coverSizePx: $coverSizePx (150dp)
                   | screenWidthPx: $screenWidthPx
                   | screenHeightPx: $screenHeightPx
                   | calculatedSpacing: $calculatedSpacing
                   | maxOffset: 3f
                   | Formula: (screenWidth - coverSizePx) / (maxOffset * 2) = ($screenWidthPx - $coverSizePx) / 6
                """.trimMargin()
            }

            CoverFlowMetrics(
                coverSizeDp = coverSize,
                coverSizePx = coverSizePx,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                itemSpacing = calculatedSpacing,
                layoutParams = CoverFlowLayoutParams(
                    coverSizePx = coverSizePx,  // ✅ 使用原始 coverSizePx (150dp)
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    itemSpacing = calculatedSpacing,
                    arcStrength = 0f,
                    maxRotation = 62f,
                    minScale = 0.92f,
                    // 竖屏维持经典的 7 张；横屏利用额外宽度显示 13 张。
                    maxSideCount = if (isPortrait) 3 else 6,
                    cameraDistance = 6000f,
                    rotationCorrectionFactor = 0.6f,  // 校准系数，根据实际效果调整
                    // 竖屏下增加侧边唱片的露出宽度，同时把第一张侧封面拉近中心，
                    // 保证每侧至少能辨认出 2 张，并在边缘看到第 3 张。
                    sideToSideSpacing = if (isPortrait) 0.38f else 0.24f,
                    sideToCenterSpacing = if (isPortrait) 0.58f else 0.72f,
                    unifiedSpacingMultiplier = 1f
                )
            )
        }

        // Phase 2: 布局数据 - 使用稳定的 key 减少不必要的重组
        // 依赖 animatedIndex 确保动画流畅，使用 remember 避免不必要的重新计算
        // 添加 cachedMetrics 到 key，确保横屏旋转时重新计算位置
        val coverFlowData = remember(animatedIndex, cachedMetrics) {
            val data = List(cachedMetrics.layoutParams.displayCount) { displayPos ->
                calculateCoverFlowItem(
                    displayPos = displayPos,
                    centerOffset = cachedMetrics.layoutParams.centerOffset,
                    animatedIndex = animatedIndex,
                    targetIndex = targetIndex,
                    params = cachedMetrics.layoutParams,
                    albumsSize = albums.size,
                    density = density
                )
            }

            // Debug log position 0 transform
            debugLog("CoverFlowLayout") {
                val pos0Data = data.first()
                val pos6Data = data.last()
                """Layout Data (animatedIndex=$animatedIndex, targetIndex=$targetIndex):
                   | Position 0 (leftmost):
                   | x: ${pos0Data.transform.x}px (${pos0Data.transform.x / density.density}dp)
                   | Expected: x=0px (0dp) relative to Box left edge
                   | transformOriginX: ${pos0Data.transform.transformOriginX} (0.0 = rotate around left edge)
                   | rotationY: ${pos0Data.transform.rotationY}
                   |
                   | Position 6 (rightmost):
                   | x: ${pos6Data.transform.x}px (${pos6Data.transform.x / density.density}dp)
                   | transformOriginX: ${pos6Data.transform.transformOriginX} (1.0 = rotate around right edge)
                   | rotationY: ${pos6Data.transform.rotationY}
                """.trimMargin()
            }

            data
        }


        // 缓存完成后显式触发 AndroidView 更新，避免快速滚动后封面保持空白。
        var preloaded by remember { mutableStateOf(false) }
        var imageRevision by remember { mutableIntStateOf(0) }

        LaunchedEffect(targetIndex, cachedMetrics.layoutParams.maxSideCount) {
            withContext(Dispatchers.IO) {
                ComposeCoverFlowView.preloadVisibleImages(
                    albums,
                    targetIndex,
                    cachedMetrics.layoutParams.maxSideCount
                )
            }
            preloaded = true
            imageRevision++
        }

        // CoverFlow区域 - 使用 AndroidView 混合方案
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            // 等待预加载完成后再显示 AndroidView
            if (preloaded) {
                // 使用 AndroidView 嵌入原生容器，支持 Z 轴深度
                AndroidView(
                factory = { ctx ->
                    CoverFlowNativeView(ctx).apply {
                        // 设置容器尺寸
                        val containerWidthPx = with(density) { containerWidth.toPx().toInt() }
                        val containerHeightPx = with(density) { containerHeight.toPx().toInt() }
                        setContainerSize(containerWidthPx, containerHeightPx)
                    }
                },
                update = { nativeView ->
                    nativeView.setVisibleItemCount(coverFlowData.size)
                    // 读取 revision，使图片异步加载完成时 update 会重新执行。
                    @Suppress("UNUSED_VARIABLE")
                    val currentImageRevision = imageRevision
                    // 动画驱动：每次重组时更新原生 View
                    coverFlowData.forEach { data ->
                        val albumId = if (data.isPlaceholder) null else albums[data.albumIndex].id
                        val bitmap = if (data.isPlaceholder) {
                            null
                        } else {
                            // 使用带倒影的缓存
                            val reflectedCacheKey = "${albums[data.albumIndex].id}_reflected"
                            val cached = reflectedBitmapCache[reflectedCacheKey]
                            debugLog("CoverFlowBitmap") {
                                "Pos ${data.displayPos}: albumId=$albumId, cacheKey=$reflectedCacheKey, bitmap=${cached?.let { "loaded(${it.width}x${it.height})" } ?: "null"}"
                            }
                            cached
                        }

                        // 计算原生变换参数
                        // ✅ ImageView的布局尺寸已经是150dp (412.5px)
                        // scaleType = FIT_XY 会自动将bitmap拉伸到布局尺寸
                        // 不需要额外的displayScale缩放因子

                        val transform = CoverFlowNativeView.TransformData(
                            bitmap = bitmap,
                            x = data.transform.x,
                            y = data.transform.y,
                            rotationY = data.transform.rotationY,
                            translationZ = data.transform.translationZ ?: 0f, // Z轴深度！
                            scaleX = data.transform.scaleX * data.transform.scale,
                            scaleY = data.transform.scaleY * data.transform.scale,
                            translationX = data.transform.translationX,
                            translationY = data.transform.translationY,
                            alpha = data.transform.alpha,
                            // ✅ NEW: Pass missing 3D transform properties
                            rotationX = data.transform.rotationX,
                            cameraDistance = data.transform.cameraDistance,
                            transformOriginX = data.transform.transformOriginX,
                            transformOriginY = data.transform.transformOriginY,
                            // ✅ 3D模式缩放补偿：恢复正常值 (1.0f = 无额外缩放)
                            modeScaleCompensation = 1.0f
                        )

                        nativeView.updateImage(data.displayPos, albumId, transform)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            }

            // 专辑信息文字 - 放在底部，使用高zIndex确保显示在封面之上
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .zIndex(200f), // 确保文字始终显示在封面和倒影之上
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val displayIndex = animatedIndex.roundToInt()
                val currentAlbum = if (displayIndex in albums.indices) albums[displayIndex] else null
                currentAlbum?.let { album ->
                    Text(
                        text = album.title ?: "",
                        color = Color.Black,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = album.subtitle ?: "",
                        color = Color.DarkGray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * CoverFlow数据类
 */
private data class CoverFlowData(
    val displayPos: Int,
    val albumIndex: Int,
    val isPlaceholder: Boolean = false,
    val transform: CoverTransform
)

/**
 * Phase 5: 缓存的密度相关指标
 * 避免在每帧重复计算密度转换
 */
private data class CoverFlowMetrics(
    val coverSizeDp: androidx.compose.ui.unit.Dp,
    val coverSizePx: Float,
    val screenWidthPx: Float,
    val screenHeightPx: Float,
    val itemSpacing: Float,
    val layoutParams: CoverFlowLayoutParams
)

/**
 * CoverFlow变换属性数据类
 * 基于CSS的iPod CoverFlow 3D效果实现
 * 增强版：添加rotationX实现倾斜效果，模拟Skia的SKMatrix44透视变换
 */
private data class CoverTransform(
    val x: Float,
    val y: Float,
    val rotationY: Float,
    val rotationX: Float = 0f,           // X轴旋转：轻微前倾，增强3D深度感
    val skewY: Float,
    val translationY: Float,
    val scale: Float,
    val scaleX: Float = 1f,              // X轴缩放：独立控制，增强透视压缩感
    val scaleY: Float = 1f,              // Y轴缩放：独立控制
    val shadowElevation: Float = 0f,     // 阴影高度：增强深度感
    val zIndex: Float,
    val alpha: Float,
    val transformOriginX: Float = 0.5f,  // X轴变换原点：0.5=50%(中心)，0.8=80%(左侧图片)，0.2=20%(右侧图片)
    val transformOriginY: Float = 0.5f,  // Y轴变换原点：始终为50%(垂直居中)
    val translationX: Float = 0f,        // X轴偏移：用于深度效果，侧边封面向外推
    val cameraDistance: Float = 1200f,   // 透视距离：每项独立的透视距离，离中心越远越小
    val translationZ: Float? = null      // ✅ 新增 Z 轴深度（原生 View 专用，Compose 忽略）
)

/**
 * CoverFlow 布局参数 - 集中管理所有可调参数
 *
 * 设计原则：
 * 1. 中心位置固定：中心元素始终在屏幕中心
 * 2. 两侧对称：左右两侧元素位置对称
 * 3. 弧形排列：侧边元素向外推移形成弧形
 * 4. 可调参数：所有布局参数集中在一处，方便调整
 * 5. 边界安全：确保元素不会超出屏幕
 */
private data class CoverFlowLayoutParams(
    // 封面尺寸（像素）
    val coverSizePx: Float,

    // 屏幕尺寸（像素）
    val screenWidthPx: Float,
    val screenHeightPx: Float,

    // 布局参数
    val itemSpacing: Float = 120f,           // 元素之间的基础间距（px）
    val arcStrength: Float = 0.3f,           // 弧形强度：0=平铺, 1=强弧形
    val maxSideCount: Int = 3,               // 每侧显示的最大元素数量

    // 旋转参数
    val maxRotation: Float = 60f,            // 最大旋转角度（度）
    val rotationCurve: Float = 1.0f,         // 旋转曲线：1=线性, >1=缓动

    // 缩放参数
    val minScale: Float = 0.85f,             // 最小缩放比例
    val maxScale: Float = 1.0f,              // 最大缩放比例
    val scaleCurve: Float = 1.0f,            // 缩放曲线

    // 透明度参数
    val minAlpha: Float = 0.6f,              // 最小透明度
    val maxAlpha: Float = 1.0f,              // 最大透明度

    // 3D 参数
    val cameraDistance: Float = 1200f,       // 相机距离
    val perspectiveStrength: Float = 0.3f,   // 透视强度
    val rotationCorrectionFactor: Float = 0.6f,  // 3D旋转偏移校正系数

    // ✅ 新增：非对称间距配置
    // 侧边图片之间的间距倍数（pos0↔pos1, pos1↔pos2, pos4↔pos5, pos5↔pos6）
    val sideToSideSpacing: Float = 0.7f,
    // 侧边到中心的间距倍数（pos2↔pos3, pos3↔pos4）
    val sideToCenterSpacing: Float = 1.5f,
    // 统一间距倍数，用于简化公式
    val unifiedSpacingMultiplier: Float = 1.2f
) {
    // 计算中心位置
    val centerX: Float get() = screenWidthPx / 2f
    val centerY: Float get() = screenHeightPx * 0.4f

    // 内容区域边界 - 使用完整Box宽度（0到screenWidth）
    val contentLeft: Float get() = 0f
    val contentRight: Float get() = screenWidthPx

    // 总显示数量
    val displayCount: Int get() = maxSideCount * 2 + 1
    val centerOffset: Int get() = displayCount / 2

    // 验证参数
    init {
        require(itemSpacing > 0) { "itemSpacing must be positive" }
        require(maxSideCount > 0) { "maxSideCount must be positive" }
        require(minScale in 0f..1f) { "minScale must be in [0, 1]" }
        require(maxScale in 0f..1f) { "maxScale must be in [0, 1]" }
        require(minAlpha in 0f..1f) { "minAlpha must be in [0, 1]" }
        require(maxAlpha in 0f..1f) { "maxAlpha must be in [0, 1]" }
    }
}

/**
 * 计算弧形偏移量
 * 距离中心越远，推移越多，形成弧形效果
 */
private fun calculateArcOffset(offset: Float, params: CoverFlowLayoutParams): Float {
    val absOffset = abs(offset)
    if (absOffset < 0.1f) return 0f

    // 简化的弧形算法：根据距离向两侧推移
    val pushAmount = params.itemSpacing * params.arcStrength * absOffset
    return pushAmount * kotlin.math.sign(offset)
}

/**
 * 计算旋转角度
 * 统一角度：所有侧边图片都旋转70度
 */
private fun calculateRotation(offset: Float, params: CoverFlowLayoutParams): Float {
    return when {
        offset > 0 -> 70f
        offset < 0 -> -70f
        else -> 0f
    }
}

/**
 * 计算透视距离 - 增强版
 * 离中心越远，cameraDistance越小，透视效果越强烈
 *
 * 原理：
 * - cameraDistance 越小 = 相机离物体越近 = 视野越大 = 物体畸变越明显
 * - 中心图片使用默认透视距离（正常透视）
 * - 侧边图片逐级减小透视距离，增强3D深度感
 *
 * 优化效果（2026-03-16）：
 * - 增大基础距离（3000f -> 6000f），透视效果更自然
 * - 减小递减幅度（150f × offset），避免边缘透视过强
 * - offset=0: 6000f, offset=1: 5850f, offset=2: 5700f, offset=3: 5550f -> 1000f
 *
 * @param offset 距离中心的偏移量（-3 到 3）
 * @param params 布局参数
 * @return 透视距离（像素）
 */
private fun calculateCameraDistance(offset: Float, params: CoverFlowLayoutParams): Float {
    val absOffset = abs(offset)
    if (absOffset < 0.1f) return params.cameraDistance  // 中心使用默认值

    // 基础距离 (现在使用 6000f)
    val baseDistance = params.cameraDistance

    // 减小透视：使用更温和的递减幅度，提高最小值
    // 之前: offset=3时降至300f (极强透视)
    // 之后: offset=3时降至1000f+ (温和透视)
    val distanceReduction = absOffset * 150f  // 减小递减幅度

    return (baseDistance - distanceReduction).coerceAtLeast(1000f)  // 提高最小值
}

/**
 * 计算缩放比例
 * 中心最大，向两侧逐渐缩小
 */
private fun calculateScale(offset: Float, params: CoverFlowLayoutParams): Float {
    val absOffset = abs(offset)
    if (absOffset < 0.1f) return params.maxScale

    val t = (absOffset / params.maxSideCount).coerceIn(0f, 1f)
    val easedT = t.pow(params.scaleCurve)
    val scaleRange = params.maxScale - params.minScale

    return params.maxScale - (easedT * scaleRange)
}

/**
 * 计算透明度
 * 中心最不透明，向两侧逐渐变透明
 */
private fun calculateAlpha(offset: Float, params: CoverFlowLayoutParams): Float {
    val absOffset = abs(offset)
    if (absOffset < 0.1f) return params.maxAlpha

    val t = (absOffset / params.maxSideCount).coerceIn(0f, 1f)
    val alphaRange = params.maxAlpha - params.minAlpha

    return params.maxAlpha - (t * alphaRange)
}

/**
 * 计算单个 CoverFlow 项的位置和变换
 *
 * @param displayPos 显示位置（0 到 displayCount-1）
 * @param centerOffset 中心偏移量（通常是 3）
 * @param animatedIndex 动画索引（浮点数）
 * @param displayIndex 当前显示的索引（整数）
 * @param params 布局参数
 * @param albumsSize 专辑列表大小
 * @param density 密度，用于dp转换
 * @return CoverFlowData 包含位置和变换信息
 */
private fun calculateCoverFlowItem(
    displayPos: Int,
    centerOffset: Int,
    animatedIndex: Float,
    targetIndex: Int,
    params: CoverFlowLayoutParams,
    albumsSize: Int,
    density: androidx.compose.ui.unit.Density
): CoverFlowData {

    // 1. 计算专辑索引 - 使用 targetIndex 计算稳定索引（不会跳转）
    val rawAlbumIndex = targetIndex + displayPos - centerOffset
    val inBounds = rawAlbumIndex >= 0 && rawAlbumIndex < albumsSize
    val albumIndex = rawAlbumIndex.coerceIn(0, (albumsSize - 1).coerceAtLeast(0))
    val isPlaceholder = !inBounds

    // 2. 基础平铺计算 - 第4张（pos=3）在中轴，其他往两边铺
    // 相对于中心的偏移：-3, -2, -1, 0, 1, 2, 3
    val offsetFromCenter = displayPos - centerOffset

    // ✅ 以屏幕中心为基准，直接计算每张图片的位置
    // 这样确保中心图片在屏幕正中央，其他图片向两边均匀分布
    // 包含动画进度，实现平滑过渡
    val animationOffset = animatedIndex - targetIndex

    // 浮点偏移量（用于变换属性平滑动画）
    // = 整数偏移 - 动画偏移（与位置计算相同），范围约 -3.5 到 3.5
    // 这使得旋转、缩放、透明度等变换属性能平滑过渡
    val floatOffsetFromCenter = offsetFromCenter - animationOffset

    // X位置 = 屏幕中心 - 图片半宽 + (偏移 - 动画进度) * 间距
    // pos=0(offset=-3): 靠左侧
    // pos=3(offset=0): 屏幕中心
    // pos=6(offset=3): 靠右侧
    // 经典 Cover Flow 不是等距横排：中心封面两侧留出空间，侧边封面则像唱片架一样
    // 紧密叠放。这个连续函数在跨越中心时不会跳帧。
    val absoluteOffset = abs(floatOffsetFromCenter)
    val centerTravel = params.coverSizePx * params.sideToCenterSpacing *
        absoluteOffset.coerceAtMost(1f)
    val stackedTravel = params.coverSizePx * params.sideToSideSpacing *
        (absoluteOffset - 1f).coerceAtLeast(0f)
    val baseX = params.screenWidthPx / 2f - params.coverSizePx / 2f +
        sign(floatOffsetFromCenter) * (centerTravel + stackedTravel)

    // 3. 3D变换参数 - 增强版：添加倾斜、透视压缩、阴影

    // ✅ 直接使用 baseX 作为最终 X 位置，无需额外补偿
    val finalX = baseX


    // Y位置 = 垂直中心
    val finalY = params.centerY - params.coverSizePx / 2f

    // 首先计算绝对偏移量，后续计算会用到
    val absOffsetFromCenter = abs(offsetFromCenter.toFloat())
    val absFloatOffset = abs(floatOffsetFromCenter)  // 浮点绝对偏移，用于平滑插值

    // 封面进入中心时完成“展开”，离开时重新折向侧面。smoothstep 让展开的首尾
    // 都减速，比线性 rotationY 更接近 iPod 的机械式翻转。
    val turnProgress = absoluteOffset.coerceIn(0f, 1f)
    val easedTurn = turnProgress * turnProgress * (3f - 2f * turnProgress)
    val rotationY = -sign(floatOffsetFromCenter) * params.maxRotation * easedTurn

    // X轴旋转：所有位置都保持倾斜
    val rotationX = 0f

    // iOS风格的温和透视距离差值，避免夸张的3D效果
    // 中心6000 -> 边缘5910 -> 最小1000 的温和对比
    val perspectiveDistance = (params.cameraDistance - absFloatOffset * 30f)
        .coerceAtLeast(1000f)

    val skewY = 0f
    val scale = 1.0f  // 基础缩放保持100%

    // ✅ 动态缩放：中心图片完整大小 (1.0)，边缘图片逐渐缩小 (0.85)
    // 为中心图片切换添加缩放动画，增强视觉流畅性
    val t = minOf(absFloatOffset, 1f)  // 0 到 1 的插值因子（用于阴影等效果）
    val scaleAmount = 1.0f - (1.0f - params.minScale) * turnProgress
    val scaleX = scaleAmount
    val scaleY = scaleAmount

    // 新增：阴影高度 - 中心无阴影，侧边有阴影增强深度感
    // 使用平滑插值：从中心到边缘阴影逐渐加深
    val shadowElevation = t * 16f  // 0 → 16 的平滑插值

    // 增强透明度：中心完全不透明，边缘逐渐半透明
    // 使用平滑插值实现渐变效果
    // 淡出起点跟随当前可见数量。原先固定从 2.35 开始淡出，导致横屏新增的
    // 第 4～6 张虽然参与布局，却全部变成透明。
    val fadeStart = params.maxSideCount - 0.65f
    val alpha = if (isPlaceholder) 0f else
        (1f - (absoluteOffset - fadeStart).coerceAtLeast(0f) * 0.65f).coerceIn(0f, 1f)
    val zIndex = 100f - absoluteOffset * 20f
    val transformOriginX = 0.5f

    // translationX补偿已禁用：finalX已正确计算间距，无需额外补偿
    // 注：保留translationX字段用于可能的未来调整
    val translationX = 0f

    // Y轴位置调整：所有图片保持在同一水平位置（无Y轴偏移）
    val translationY = 0f

    // ✅ Z 轴深度计算（原生 View 专用）
    // 越靠边的图片越"远"，增强3D深度感（iOS风格温和深度）
    // 类似 MusicPlayerView3rd 中的: image.z = abs(temp * 3)
    // Android 的 Z 值越大越靠前；原实现方向相反，导致侧边卡片压住中心卡片。
    val translationZ = (params.maxSideCount + 1f - absoluteOffset)
        .coerceAtLeast(0f) * 8f * density.density

    // Debug logging for all positions
    if (ENABLE_DEBUG_LOGGING) {
        val d = density.density
        Log.d("CoverFlowTranslation", """
            Position $displayPos Debug:
            | offsetFromCenter: $offsetFromCenter
            | floatOffsetFromCenter: $floatOffsetFromCenter
            | animationOffset: $animationOffset
            | transformOriginX: $transformOriginX
            | rotationY: $rotationY°
            | translationZ: $translationZ
            | finalX: $finalX (${finalX / d}dp)
        """.trimIndent())
    }

    return CoverFlowData(
        displayPos = displayPos,
        albumIndex = albumIndex,
        isPlaceholder = isPlaceholder,
        transform = CoverTransform(
            x = finalX,
            y = finalY,
            rotationY = rotationY,
            rotationX = rotationX,              // 新增：X轴旋转（倾斜）
            skewY = skewY,
            translationY = translationY,
            scale = scale,
            scaleX = scaleX,                    // 新增：X轴缩放
            scaleY = scaleY,                    // 新增：Y轴缩放
            shadowElevation = shadowElevation,  // 新增：阴影高度
            zIndex = zIndex,
            alpha = alpha,
            transformOriginX = transformOriginX,
            transformOriginY = 0.5f,
            translationX = translationX,
            cameraDistance = perspectiveDistance, // 使用计算的透视距离
            translationZ = translationZ          // ✅ 新增：Z 轴深度（原生 View 专用）
        )
    )
}

/**
 * 创建带倒影的Bitmap
 */
private fun createReflectedBitmap(bitmap: Bitmap): Bitmap {
    val reflectionGap = 0
    val width = bitmap.width
    val height = bitmap.height

    val matrix = Matrix()
    matrix.preScale(1f, -1f)
    val reflectionImage = Bitmap.createBitmap(bitmap, 0, height / 2, width, height / 2, matrix, false)
    val bitmap4Reflection = Bitmap.createBitmap(width, height + height / 2 + reflectionGap, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap4Reflection)

    canvas.drawBitmap(bitmap, 0f, 0f, null)
    canvas.drawBitmap(reflectionImage, 0f, (height + reflectionGap).toFloat(), null)
    reflectionImage.recycle()

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
    canvas.drawRect(
        0f,
        height.toFloat(),
        width.toFloat(),
        bitmap4Reflection.height.toFloat(),
        paint
    )

    return bitmap4Reflection
}

/**
 * 创建统一的正方形Bitmap，使用CENTER_CROP方式
 * 如果原图不是正方形，会裁剪多余部分以完全填充
 * 如果原图小于目标尺寸，会等比放大后再裁剪
 * 如果原图大于目标尺寸，会等比缩小后再裁剪
 *
 * @param source 原始图片
 * @param targetSize 目标尺寸（宽度和高度相同）
 * @return 统一大小后的正方形Bitmap（CENTER_CROP效果）
 */
private fun createSquareBitmapWithCenterCrop(source: Bitmap, targetSize: Int): Bitmap {
    val srcWidth = source.width
    val srcHeight = source.height

    // 如果已经是目标尺寸的正方形，直接返回
    if (srcWidth == targetSize && srcHeight == targetSize) {
        return source
    }

    // 计算缩放比例：使短边等于目标尺寸（CENTER_CROP效果）
    val minSide = minOf(srcWidth, srcHeight).toFloat()
    val scale = targetSize / minSide

    val scaledWidth = (srcWidth * scale).toInt()
    val scaledHeight = (srcHeight * scale).toInt()

    // 创建缩放后的图片
    val scaledBitmap = if (scale != 1f) {
        Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
    } else {
        source
    }

    // 从缩放后图片的中心裁剪出 targetSize x targetSize 的区域
    val left = ((scaledWidth - targetSize) / 2f).coerceAtLeast(0f).toInt()
    val top = ((scaledHeight - targetSize) / 2f).coerceAtLeast(0f).toInt()

    val resultBitmap = Bitmap.createBitmap(
        scaledBitmap,
        left,
        top,
        targetSize,
        targetSize
    )

    // 如果创建了新的缩放bitmap，回收它
    if (scaledBitmap != source) {
        scaledBitmap.recycle()
    }

    return resultBitmap
}
