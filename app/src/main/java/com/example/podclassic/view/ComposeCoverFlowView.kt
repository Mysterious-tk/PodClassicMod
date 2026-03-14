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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.podclassic.base.Core
import com.example.podclassic.base.ScreenView
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
import kotlin.math.pow

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
 * 修复了快速滚动时的闪现问题，添加了 3D 变换效果
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

    private val composeView = androidx.compose.ui.platform.ComposeView(context).apply {
        setContent {
            CoverFlowContent(
                albums = albums,
                currentIndexState = currentIndexState,
                targetIndexState = targetIndexState,
                animatedIndexState = animatedIndexState
            )
        }
    }

    init {
        // 设置黑色背景，避免切换时的闪烁
        setBackgroundColor(android.graphics.Color.BLACK)
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
        suspend fun preloadVisibleImages(albums: List<MusicList>, centerIndex: Int) {
            val range = -2..2
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
    animatedIndexState: androidx.compose.runtime.MutableFloatState
) {
    val density = LocalDensity.current
    val context = LocalContext.current

    // 读取当前目标索引
    val targetIndex by remember {
        derivedStateOf { targetIndexState.intValue }
    }

    // 平滑动画索引 - 使用 spring 动画实现平滑过渡
    val animatedIndex by animateFloatAsState(
        targetValue = targetIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.7f,  // 阻尼，0.7 = 略带弹性
            stiffness = 400f      // 刚度，控制速度
        ),
        label = "coverflow_scroll"
    )

    // Phase 2: 使用动画索引的整数部分作为显示索引（用于布局计算）
    val displayIndex = remember(animatedIndex) {
        derivedStateOf { animatedIndex.toInt() }
    }.value

    // Phase 2: 浮点进度用于平滑变换（0.0 到 1.0）
    val animationProgress = remember(animatedIndex, displayIndex) {
        derivedStateOf { animatedIndex - displayIndex }
    }.value

    // 同步动画状态到外部状态
    LaunchedEffect(displayIndex) {
        animatedIndexState.floatValue = animatedIndex
        // 同步 currentIndex 以便点击时选择正确的专辑
        currentIndexState.intValue = displayIndex.coerceIn(0, albums.size - 1)
    }

    // 使用BoxWithConstraints获取实际可用尺寸
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight

        // Phase 5: 缓存密度相关的计算值
        val cachedMetrics = remember(containerWidth, containerHeight, density) {
            val containerPaddingDp = 24f
            val containerPaddingPx = with(density) { containerPaddingDp.dp.toPx() }

            // 显示尺寸150dp，处理分辨率600px（由Compose自动缩放）
            val coverSize = 150.dp
            val coverSizePx = with(density) { coverSize.toPx() }
            val screenWidthPx = with(density) { containerWidth.toPx() }
            val screenHeightPx = with(density) { containerHeight.toPx() }

            // 简化版间距计算：基于Box宽度（完整宽度）均匀分布7张图
            // 第一张图从x=0开始，最后一张图结束于x=screenWidth-coverSize
            val maxOffset = 3f
            // 间距 = (Box宽度 - 封面宽度) / 6
            val calculatedSpacing = (screenWidthPx - coverSizePx) / (maxOffset * 2f)

            CoverFlowMetrics(
                coverSizeDp = coverSize,
                coverSizePx = coverSizePx,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                containerPaddingPx = containerPaddingPx,
                itemSpacing = calculatedSpacing,
                layoutParams = CoverFlowLayoutParams(
                    containerPaddingPx = containerPaddingPx,
                    coverSizePx = coverSizePx,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    itemSpacing = calculatedSpacing,
                    arcStrength = 0f,
                    maxRotation = 60f,
                    minScale = 0.85f,
                    maxSideCount = 3,
                    cameraDistance = 1200f
                )
            )
        }


        // Phase 2: 布局数据现在只依赖于稳定的 displayIndex，而不是每帧变化的 animatedIndex
        val coverFlowData = remember(displayIndex, cachedMetrics.layoutParams, albums.size) {
            List(cachedMetrics.layoutParams.displayCount) { displayPos ->
                calculateCoverFlowItem(
                    displayPos = displayPos,
                    centerOffset = cachedMetrics.layoutParams.centerOffset,
                    animatedIndex = animatedIndex,
                    displayIndex = displayIndex,
                    params = cachedMetrics.layoutParams,
                    albumsSize = albums.size
                )
            }
        }


        // CoverFlow区域
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
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
                val currentAlbum = if (displayIndex in albums.indices) albums[displayIndex] else null
                currentAlbum?.let { album ->
                    Text(
                        text = album.title ?: "",
                        color = Color.White,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = album.subtitle ?: "",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * 稳定的CoverFlowItem - Phase 3: 使用 LaunchedEffect 替代 produceState
 * 减少不必要的 recomposition，避免级联更新
 * 添加 crossfade 过渡效果实现平滑加载
 * 添加倒影效果
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
    val context = LocalContext.current
    val density = LocalDensity.current

    // 使用带倒影的缓存键
    val reflectedCacheKey = "${albumId}_reflected"

    // Phase 3: 使用手动状态管理而非 produceState
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
    // 处理分辨率600px，显示尺寸150dp，由Compose自动缩放
    val originalSizeDp = 150.dp
    val reflectedHeightDp = originalSizeDp * 1.5f

    // Phase 4: 使用优化的 graphicsLayer 修饰符
    Box(
        modifier = Modifier
            .offset(x = xOffsetDp, y = yOffsetDp)
            .coverFlowTransform(transform, layoutCameraDistance)
    ) {
        bitmapState?.let { bmp ->
            // 显示带倒影的图片
            // Bitmap 尺寸是 600x900（宽x高），宽高比是 2:3
            // 显示尺寸：宽度=coverSize (150.dp)，高度=reflectedHeightDp (225.dp)
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = album.title,
                modifier = Modifier
                    .size(width = coverSize, height = reflectedHeightDp),
                contentScale = ContentScale.FillBounds,
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

// Phase 4: 优化的 transform 修饰符 - 避免在重组时重新创建
private fun Modifier.coverFlowTransform(
    transform: CoverTransform,
    cameraDistance: Float
) = this.then(
    Modifier.graphicsLayer {
        scaleX = transform.scale
        scaleY = transform.scale
        translationX = transform.translationX
        translationY = transform.translationY
        rotationY = transform.rotationY
        this.cameraDistance = cameraDistance
    }.zIndex(transform.zIndex).alpha(transform.alpha)
)

/**
 * 占位图片组件 - 用于填补不足7张的位置
 * 显示半透明深灰背景和音乐图标，带倒影效果
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
    val reflectionSizeDp = originalSizeDp / 2

    // Phase 4: 使用优化的 transform 修饰符
    Box(
        modifier = Modifier
            .offset(x = xOffsetDp, y = yOffsetDp)
            .coverFlowTransform(transform, layoutCameraDistance)
    ) {
        Box(modifier = Modifier.size(reflectedHeightDp)) {
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

            // 倒影部分 - 使用Canvas绘制
            Canvas(
                modifier = Modifier
                    .size(originalSizeDp)
                    .offset(y = originalSizeDp)
            ) {
                val pxSize = originalSizeDp.toPx()
                val pxReflectionSize = pxSize / 2

                // 使用Native Canvas绘制倒影
                drawIntoCanvas { canvas ->
                    val nativeCanvas = canvas.nativeCanvas

                    // 保存当前状态
                    val saveCount = nativeCanvas.save()

                    // 创建倒影的渐变遮罩
                    val gradient = android.graphics.LinearGradient(
                        0f, 0f, 0f, pxReflectionSize,
                        intArrayOf(0x00000000.toInt(), 0xB3000000.toInt(), 0xCC000000.toInt()),
                        floatArrayOf(0f, 0.3f, 1f),
                        android.graphics.Shader.TileMode.CLAMP
                    )

                    // 绘制倒影背景
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(77, 64, 64, 64)
                        shader = gradient
                    }

                    nativeCanvas.drawRect(
                        0f, 0f, pxSize, pxReflectionSize,
                        paint
                    )

                    // 恢复状态
                    nativeCanvas.restoreToCount(saveCount)
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
    val containerPaddingPx: Float,
    val itemSpacing: Float,
    val layoutParams: CoverFlowLayoutParams
)

/**
 * CoverFlow变换属性数据类
 * 基于CSS的iPod CoverFlow 3D效果实现
 */
private data class CoverTransform(
    val x: Float,
    val y: Float,
    val rotationY: Float,
    val skewY: Float,
    val translationY: Float,
    val scale: Float,
    val zIndex: Float,
    val alpha: Float,
    val transformOriginX: Float = 0.5f,  // X轴变换原点：0.5=50%(中心)，0.8=80%(左侧图片)，0.2=20%(右侧图片)
    val transformOriginY: Float = 0.5f,  // Y轴变换原点：始终为50%(垂直居中)
    val translationX: Float = 0f         // X轴偏移：用于深度效果，侧边封面向外推
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
    // 容器内边距（像素）
    val containerPaddingPx: Float = 0f,

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
    val perspectiveStrength: Float = 0.3f    // 透视强度
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
 * 使用缓动曲线让旋转更平滑
 */
private fun calculateRotation(offset: Float, params: CoverFlowLayoutParams): Float {
    val absOffset = abs(offset)
    if (absOffset < 0.1f) return 0f

    // 使用缓动曲线让旋转更平滑
    val t = (absOffset / params.maxSideCount).coerceIn(0f, 1f)
    val easedT = t.pow(params.rotationCurve)
    val rotation = easedT * params.maxRotation

    return rotation * kotlin.math.sign(offset)
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
 * @return CoverFlowData 包含位置和变换信息
 */
private fun calculateCoverFlowItem(
    displayPos: Int,
    centerOffset: Int,
    animatedIndex: Float,
    displayIndex: Int,
    params: CoverFlowLayoutParams,
    albumsSize: Int
): CoverFlowData {

    // 1. 计算专辑索引
    val rawAlbumIndex = displayIndex + displayPos - centerOffset
    val inBounds = rawAlbumIndex >= 0 && rawAlbumIndex < albumsSize
    val albumIndex = rawAlbumIndex.coerceIn(0, (albumsSize - 1).coerceAtLeast(0))
    val isPlaceholder = !inBounds

    // 2. 基础平铺计算 - 第4张（pos=3）在中轴，其他往两边铺
    // 相对于中心的偏移：-3, -2, -1, 0, 1, 2, 3
    val offsetFromCenter = displayPos - centerOffset

    // 使用内容区域的中心（不是屏幕中心），因为间距是基于contentWidth计算的
    val contentCenterX = params.contentLeft + (params.contentRight - params.contentLeft) / 2f
    val centerLeft = contentCenterX - params.coverSizePx / 2f

    // X位置 = 中心左边缘 + 偏移 * 间距
    // pos=0(offset=-3): x=0 (最左，贴box左边缘)
    // pos=3(offset=0): centerLeft (中心)
    // pos=6(offset=3): x=contentRight-coverSize (最右，贴box右边缘)
    val finalX = centerLeft + offsetFromCenter * params.itemSpacing

    // Y位置 = 垂直中心
    val finalY = params.centerY - params.coverSizePx / 2f

    // 3. 简化变换参数 - 无旋转、无弧形偏移、无缩放
    val rotationY = 0f
    val skewY = 0f
    val scale = 1.0f
    val alpha = if (isPlaceholder) 0.3f else 1.0f
    val transformOriginX = 0.5f
    val zIndex = 100f - abs(offsetFromCenter) * 10f
    val translationX = 0f
    val translationY = 0f

    return CoverFlowData(
        displayPos = displayPos,
        albumIndex = albumIndex,
        isPlaceholder = isPlaceholder,
        transform = CoverTransform(
            x = finalX,
            y = finalY,
            rotationY = rotationY,
            skewY = skewY,
            translationY = translationY,
            scale = scale,
            zIndex = zIndex,
            alpha = alpha,
            transformOriginX = transformOriginX,
            transformOriginY = 0.5f,
            translationX = translationX
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
