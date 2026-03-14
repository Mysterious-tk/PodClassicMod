package com.example.podclassic.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlin.math.pow
import kotlin.math.sin

// Conditional debug logging - Phase 1: Quick Wins
// Set to false to disable debug logging in production builds
private const val ENABLE_DEBUG_LOGGING = true

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

    private val composeView = androidx.compose.ui.platform.ComposeView(context).apply {
        setContent {
            CoverFlowContent(
                albums = albums,
                currentIndexState = currentIndexState,
                targetIndexState = targetIndexState,
                animatedIndexState = animatedIndexState,
                lastSlideTimeState = lastSlideTimeState
            )
        }
    }

    init {
        // 设置白色背景，保持一致的视觉效果
        setBackgroundColor(Colors.white)
        // 添加 ComposeView 并设置为填充整个父容器
        addView(
            composeView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        )
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
        suspend fun preloadVisibleImages(albums: List<MusicList>, centerIndex: Int) {
            val range = -3..3
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
                            } else {
                                reflectedBitmapCache[reflectedCacheKey] = null
                            }
                        } catch (e: Exception) {
                            reflectedBitmapCache[reflectedCacheKey] = null
                        }
                    }
                }
            }
        }
    }
}

/**
 * CoverFlow内容组件
 * 使用 smooth animation 实现 Apple 风格的 CoverFlow 滚动效果
 * Phase 2 & 5: Separate animation from layout state, cache density values
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun CoverFlowContent(
    albums: List<MusicList>,
    currentIndexState: androidx.compose.runtime.MutableIntState,
    targetIndexState: androidx.compose.runtime.MutableIntState,
    animatedIndexState: androidx.compose.runtime.MutableFloatState,
    lastSlideTimeState: androidx.compose.runtime.MutableLongState
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current

    // 读取当前目标索引
    val targetIndex by remember {
        derivedStateOf { targetIndexState.intValue }
    }

    // 根据滑动间隔判断是否快速滚动
    // 只分两档：快速滚动跳过动画，正常滚动 400ms
    val currentTime = System.currentTimeMillis()
    val timeSinceLastSlide = currentTime - lastSlideTimeState.longValue
    val animationDuration = if (timeSinceLastSlide < 100) 0 else 400

    // 平滑动画索引 - 使用 tween 动画实现动态时长的平滑过渡
    val animatedIndex by animateFloatAsState(
        targetValue = targetIndex.toFloat(),
        animationSpec = tween(
            durationMillis = animationDuration,  // 动态时长
            easing = EaseInOutCubic
        ),
        label = "coverflow_scroll"
    )

    // 同步动画状态到外部状态
    LaunchedEffect(targetIndex) {
        // 在动画开始时更新时间戳，供下次动画计算使用
        lastSlideTimeState.longValue = currentTime
        animatedIndexState.floatValue = animatedIndex
        // 同步 currentIndex 以便点击时选择正确的专辑
        currentIndexState.intValue = targetIndex.coerceIn(0, albums.size - 1)
    }

    // 使用BoxWithConstraints获取实际可用尺寸
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight

        // Phase 5: 缓存密度相关的计算值
        // 将 configuration.orientation 添加到 key 中，确保横屏时重新计算布局
        val cachedMetrics = remember(containerWidth, containerHeight, density, configuration.orientation) {
            // 显示尺寸150dp，处理分辨率600px（由Compose自动缩放）
            val coverSize = 150.dp
            val coverSizePx = with(density) { coverSize.toPx() }
            val screenWidthPx = with(density) { containerWidth.toPx() }
            val screenHeightPx = with(density) { containerHeight.toPx() }

            // 简化版间距计算：基于Box宽度（完整宽度）均匀分布7张图
            // 第一张图从x=0开始，最后一张图结束于x=screenWidth-coverSize
            val maxOffset = 3f
            // 间距 = (Box宽度 - 封面宽度) / 6
            // 使用 round 确保间距为整数，避免浮点数精度误差
            val calculatedSpacing = kotlin.math.round((screenWidthPx - coverSizePx) / (maxOffset * 2f))

            // Debug log metrics calculation
            debugLog("CoverFlowMetrics") {
                """Metrics Calculation:
                   | containerWidth (dp): $containerWidth
                   | containerHeight (dp): $containerHeight
                   | orientation: ${configuration.orientation}
                   | density: ${density.density}
                   | coverSizePx: $coverSizePx
                   | screenWidthPx: $screenWidthPx
                   | calculatedSpacing: $calculatedSpacing
                """.trimMargin()
            }

            CoverFlowMetrics(
                coverSizeDp = coverSize,
                coverSizePx = coverSizePx,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                itemSpacing = calculatedSpacing,
                layoutParams = CoverFlowLayoutParams(
                    coverSizePx = coverSizePx,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    itemSpacing = calculatedSpacing,
                    arcStrength = 0f,
                    maxRotation = 60f,
                    minScale = 0.85f,
                    maxSideCount = 3,
                    cameraDistance = 1200f,
                    rotationCorrectionFactor = 0.6f  // 校准系数，根据实际效果调整
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


        // CoverFlow区域
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(Colors.white))
        ) {
            // 封面区域 - 始终显示7个位置，不足的用占位图填补
            coverFlowData.forEach { data ->
                if (data.isPlaceholder) {
                    // 占位图使用 displayPos 作为 key
                    key(data.displayPos) {
                        PlaceholderCoverFlowItem(
                            transform = data.transform,
                            coverSize = cachedMetrics.coverSizeDp,
                            position = data.displayPos,
                            layoutCameraDistance = cachedMetrics.layoutParams.cameraDistance
                        )
                    }
                } else {
                    // 显示真实专辑 - 使用 albumIndex 作为 key，确保同一专辑在不同位置时组件被移动而非重建
                    key(data.albumIndex) {
                        val album = albums[data.albumIndex]
                        StableCoverFlowItem(
                            album = album,
                            transform = data.transform,
                            coverSize = cachedMetrics.coverSizeDp,
                            position = data.displayPos,
                            layoutCameraDistance = cachedMetrics.layoutParams.cameraDistance
                        )
                    }
                }
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
                val currentAlbum = if (targetIndex in albums.indices) albums[targetIndex] else null
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
 * 稳定的CoverFlowItem - 使用简单的 Image 组件
 * 只使用 graphicsLayer 进行变换，不使用 Canvas 透视变换
 */
@Composable
private fun StableCoverFlowItem(
    album: MusicList,
    transform: CoverTransform,
    coverSize: androidx.compose.ui.unit.Dp,
    position: Int,
    layoutCameraDistance: Float = 1200f
) {
    val albumId = album.id ?: 0L
    val density = LocalDensity.current

    // 使用带倒影的缓存键
    val reflectedCacheKey = "${albumId}_reflected"

    // 使用手动状态管理而非 produceState
    // 只在 albumId 变化时触发加载，不会因为父组件重组而重复执行
    var bitmapState by remember(albumId) {
        mutableStateOf(reflectedBitmapCache[reflectedCacheKey])
    }

    // 使用 LaunchedEffect 异步加载图片
    LaunchedEffect(albumId) {
        if (bitmapState == null && reflectedBitmapCache[reflectedCacheKey] == null) {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    // 限制缓存大小
                    if (reflectedBitmapCache.size >= MAX_CACHE_SIZE) {
                        val firstKey = reflectedBitmapCache.keys.first()
                        reflectedBitmapCache.remove(firstKey)?.recycle()
                    }

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
                        reflectedBmp
                    } else {
                        reflectedBitmapCache[reflectedCacheKey] = null
                        null
                    }
                } catch (e: Exception) {
                    debugLog("CoverFlowItem") { "Error loading album image: albumId=$albumId, ${e.message}" }
                    reflectedBitmapCache[reflectedCacheKey] = null
                    null
                }
            }
            bitmapState = loaded
        } else if (reflectedBitmapCache[reflectedCacheKey] != null) {
            bitmapState = reflectedBitmapCache[reflectedCacheKey]
        }
    }

    // 将像素位置转换为Dp
    val xOffsetDp = with(density) { transform.x.toDp() }
    val yOffsetDp = with(density) { transform.y.toDp() }

    // 计算倒影后的总高度（原图 + 倒影 = 原图的1.5倍）
    val originalSizeDp = 150.dp
    val reflectedHeightDp = originalSizeDp * 1.5f

    // 使用简单的 graphicsLayer 变换
    Box(
        modifier = Modifier
            .offset(x = xOffsetDp, y = yOffsetDp)
            .coverFlowTransform(transform, layoutCameraDistance)
    ) {
        bitmapState?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = album.title,
                modifier = Modifier
                    .size(width = coverSize, height = reflectedHeightDp)
                    .background(Color.Transparent),
                contentScale = ContentScale.Fit,
                alignment = Alignment.TopCenter
            )
        } ?: run {
            // 显示占位符 - 使用深灰色背景，同样需要倒影空间
            Column(
                modifier = Modifier.size(reflectedHeightDp)
            ) {
                // 原图部分
                Box(
                    modifier = Modifier
                        .size(originalSizeDp)
                        .background(Color.DarkGray)
                )
                // 倒影部分
                Box(
                    modifier = Modifier
                        .size(originalSizeDp)
                        .graphicsLayer {
                            scaleY = -1f
                            translationY = size.height
                        }
                        .background(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.DarkGray,
                                    0.5f to Color.DarkGray.copy(alpha = 0.5f),
                                    1.0f to Color.Black
                                )
                            )
                        )
                )
            }
        }
    }
}

// Phase 4: 优化的 transform 修饰符 - 增强版
// 添加 rotationX (倾斜效果), scaleX/scaleY (透视压缩), shadowElevation (深度阴影)
// 所有元素统一使用中心旋转，与位置计算保持一致，避免视觉偏移
// cameraDistance 参数保留以向后兼容，但实际使用 transform.cameraDistance
private fun Modifier.coverFlowTransform(
    transform: CoverTransform,
    cameraDistance: Float  // 保留此参数以向后兼容，但不再使用
) = this.then(
    Modifier.graphicsLayer {
        // 使用独立的 scaleX/scaleY 实现透视压缩效果
        scaleX = transform.scaleX * transform.scale
        scaleY = transform.scaleY * transform.scale
        translationX = transform.translationX
        translationY = transform.translationY
        rotationY = transform.rotationY
        rotationX = transform.rotationX  // 轻微前倾，增强3D深度感
        shadowElevation = transform.shadowElevation  // 添加阴影增强深度感
        transformOrigin = TransformOrigin(
            pivotFractionX = transform.transformOriginX,
            pivotFractionY = transform.transformOriginY
        )
        this.cameraDistance = transform.cameraDistance  // 使用transform中的值
    }.zIndex(transform.zIndex).alpha(transform.alpha)
)

/**
 * 占位图片组件 - 用于填补不足7张的位置
 * 使用简单的 Compose UI，只使用 graphicsLayer 进行变换
 */
@Composable
private fun PlaceholderCoverFlowItem(
    transform: CoverTransform,
    coverSize: androidx.compose.ui.unit.Dp,
    position: Int,
    layoutCameraDistance: Float = 1200f
) {
    val density = LocalDensity.current

    // 将像素位置转换为Dp
    val xOffsetDp = with(density) { transform.x.toDp() }
    val yOffsetDp = with(density) { transform.y.toDp() }

    // 计算倒影后的总高度（原图 + 倒影 = 原图的1.5倍）
    val originalSizeDp = 150.dp
    val reflectedHeightDp = originalSizeDp * 1.5f

    // 使用简单的 graphicsLayer 变换
    Box(
        modifier = Modifier
            .offset(x = xOffsetDp, y = yOffsetDp)
            .coverFlowTransform(transform, layoutCameraDistance)
    ) {
        Column(
            modifier = Modifier.size(reflectedHeightDp)
        ) {
            // 原图部分
            Box(
                modifier = Modifier
                    .size(originalSizeDp)
                    .background(
                        color = Color.DarkGray.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
                    )
            ) {
                Text(
                    text = "♪",
                    color = Color.Gray.copy(alpha = 0.5f),
                    fontSize = 48.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            // 倒影部分
            Box(
                modifier = Modifier
                    .size(originalSizeDp)
                    .graphicsLayer {
                        scaleY = -1f
                        translationY = size.height
                    }
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.DarkGray.copy(alpha = 0.5f),
                                0.5f to Color.DarkGray.copy(alpha = 0.25f),
                                1.0f to Color.Transparent
                            )
                        )
                    )
            )
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
    val cameraDistance: Float = 1200f    // 透视距离：每项独立的透视距离，离中心越远越小
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
    val rotationCorrectionFactor: Float = 0.6f  // 3D旋转偏移校正系数
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
 * 增强效果：
 * - 增大递减幅度（200f -> 250f），透视效果更强
 * - offset=1: 950f, offset=2: 700f, offset=3: 450f
 *
 * @param offset 距离中心的偏移量（-3 到 3）
 * @param params 布局参数
 * @return 透视距离（像素）
 */
private fun calculateCameraDistance(offset: Float, params: CoverFlowLayoutParams): Float {
    val absOffset = abs(offset)
    if (absOffset < 0.1f) return params.cameraDistance  // 中心使用默认值

    // 基础距离
    val baseDistance = params.cameraDistance  // 1200f

    // 增强透视：更大幅度的递减
    // offset=1: -250, offset=2: -500, offset=3: -750
    val distanceReduction = absOffset * 250f  // 从200f增加到250f

    return (baseDistance - distanceReduction).coerceAtLeast(300f)
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

    // 使用内容区域的中心（不是屏幕中心），因为间距是基于contentWidth计算的
    val contentCenterX = params.contentLeft + (params.contentRight - params.contentLeft) / 2f
    val centerLeft = contentCenterX - params.coverSizePx / 2f

    // X位置 = 中心左边缘 + (偏移 - 动画进度) * 间距
    // pos=0(offset=-3): x=0 (最左，贴box左边缘)
    // pos=3(offset=0): centerLeft (中心)
    // pos=6(offset=3): x=contentRight-coverSize (最右，贴box右边缘)
    // 包含动画进度，实现平滑过渡
    // 注意：不再使用params.extraOffsetX，改用动态translationX补偿3D旋转偏移
    val animationOffset = animatedIndex - targetIndex

    // 基础位置：使用线性间距（确保边界正确）
    val baseX = centerLeft + (offsetFromCenter - animationOffset) * params.itemSpacing

    // 侧边收紧：将左3张(0,1,2)和右3张(4,5,6)向边缘收紧
    // 通过向边缘平移产生中心留白效果
    val sideTightenAmount = 25f * density.density  // 25dp 收紧量
    val sideAdjustment = when {
        offsetFromCenter <= -1 -> -sideTightenAmount  // 左侧(0,1,2)向左收紧
        offsetFromCenter >= 1 -> sideTightenAmount   // 右侧(4,5,6)向右收紧
        else -> 0f                                    // 中心(3)保持
    }
    val finalX = baseX + sideAdjustment

    // Y位置 = 垂直中心
    val finalY = params.centerY - params.coverSizePx / 2f

    // 3. 3D变换参数 - 增强版：添加倾斜、透视压缩、阴影

    // 浮点偏移量（用于变换属性平滑动画）
    // = 整数偏移 - 动画偏移（与位置计算相同），范围约 -3.5 到 3.5
    // 这使得旋转、缩放、透明度等变换属性能平滑过渡
    // 关键：使用与位置计算相同的公式，确保变换与视觉位置同步
    val floatOffsetFromCenter = offsetFromCenter - animationOffset

    // 首先计算绝对偏移量，后续计算会用到
    val absOffsetFromCenter = abs(offsetFromCenter.toFloat())
    val absFloatOffset = abs(floatOffsetFromCenter)  // 浮点绝对偏移，用于平滑插值

    // 旋转角度：还原为 70°，适中的透视变形
    // 近的那条竖线更长，远的那条竖线更短
    val rotationY = when {
        floatOffsetFromCenter > 0.1f -> 70f * minOf(absFloatOffset, 1f)
        floatOffsetFromCenter < -0.1f -> -70f * minOf(absFloatOffset, 1f)
        else -> 0f
    }

    // X轴旋转：中心中性姿态（与0.5f旋转轴对齐），侧边前倾产生深度感
    // 中心不再后仰，减少突出感，与旋转轴平行
    val rotationX = when {
        absFloatOffset < 0.5f -> 0f   // 中心中性姿态（与旋转轴平行）
        else -> 2f                     // 侧边前倾保持深度感
    }

    // 增强透视距离差值，产生更明显的梯形效果
    // 中心1200 -> 边缘250 的更强对比
    val perspectiveDistance = (params.cameraDistance - absFloatOffset * 350f)
        .coerceAtLeast(200f)

    val skewY = 0f
    val scale = 1.0f  // 基础缩放保持100%

    // 新增：独立的X/Y缩放，模拟透视压缩效果
    // 使用平滑插值：从中心到边缘逐渐压缩
    // 略微缩小中心缩放，减少突出感
    val t = minOf(absFloatOffset, 1f)  // 0 到 1 的插值因子
    val baseScaleX = 0.96f  // 从 1.0 降低到 0.96，减少中心突出感
    val baseScaleY = 0.98f  // 从 1.0 降低到 0.98，减少中心突出感
    val scaleX = baseScaleX - t * 0.08f   // 0.96 → 0.88 的平滑插值
    val scaleY = baseScaleY - t * 0.06f   // 0.98 → 0.92 的平滑插值

    // 新增：阴影高度 - 中心无阴影，侧边有阴影增强深度感
    // 使用平滑插值：从中心到边缘阴影逐渐加深
    val shadowElevation = t * 16f  // 0 → 16 的平滑插值

    // 增强透明度：中心完全不透明，边缘逐渐半透明
    // 使用平滑插值实现渐变效果
    val alpha = when {
        isPlaceholder -> 0.3f
        absFloatOffset < 1f -> 1f
        absFloatOffset < 2f -> 1f - (absFloatOffset - 1f) * 0.15f  // 1.0 → 0.85
        else -> 0.7f
    }
    // z-index 层级计算：进入侧的图片应该有最高层级
    // 基础层级：靠近中心层级更高
    val baseZIndex = 100f - absFloatOffset * 10f

    // 方向加成：进入侧的图片有额外层级加成
    // 向左滚动（animationOffset > 0）：右侧图片进入 → 右侧图片层级加成
    // 向右滚动（animationOffset < 0）：左侧图片进入 → 左侧图片层级加成
    // 使用 animationOffset 作为阈值，只在动画进行中应用加成
    val directionBonus = when {
        animationOffset > 0.5f && floatOffsetFromCenter > 0 -> {
            // 向左滚动 + 右侧图片：越靠右加成越大 (8, 16, 24...)
            minOf(floatOffsetFromCenter, 3f) * 8f
        }
        animationOffset < -0.5f && floatOffsetFromCenter < 0 -> {
            // 向右滚动 + 左侧图片：越靠左加成越大 (8, 16, 24...)
            minOf(absFloatOffset, 3f) * 8f
        }
        else -> 0f
    }

    val zIndex = baseZIndex + directionBonus

    // 统一使用中心轴旋转，所有唱片绕自身中心旋转
    // 产生更强的悬浮感和立体效果
    val transformOriginX = 0.5f

    // 统一中心轴旋转时的translationX补偿
    // 左侧项目向左平移（负值），右侧项目向右平移（正值），使视觉边缘贴齐
    // 计算公式：width/2 * (1 - cos(rotationY))
    // 当rotationY=70°时，cos(70°)≈0.342，补偿量≈width/2 * 0.658
    val coverHalfWidth = params.coverSizePx / 2f
    val rotationCos = cos(Math.toRadians(abs(rotationY).toDouble())).toFloat()
    val translationX = when {
        floatOffsetFromCenter < -0.1f -> -coverHalfWidth * (1f - rotationCos)  // 左侧向左
        floatOffsetFromCenter > 0.1f -> coverHalfWidth * (1f - rotationCos)   // 右侧向右
        else -> 0f  // 中心无平移
    }

    // Y轴位置调整：所有图片保持在同一水平位置（无Y轴偏移）
    val translationY = 0f

    // Debug logging for position 0 and 1
    if (displayPos <= 1 && ENABLE_DEBUG_LOGGING) {
        val d = density.density
        Log.d("CoverFlowTranslation", """
            Position $displayPos Debug:
            | offsetFromCenter: $offsetFromCenter
            | rotationY: $rotationY°
            | transformOriginX: $transformOriginX
            | finalX: $finalX (${finalX / d}dp)
            | Expected x (relative to Box): ${finalX / d}dp
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
            cameraDistance = perspectiveDistance  // 使用计算的透视距离
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
