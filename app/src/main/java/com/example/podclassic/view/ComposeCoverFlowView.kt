package com.example.podclassic.view

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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.abs
import kotlin.math.pow

/**
 * Jetpack Compose + 3D动画实现的CoverFlow
 * 参考机制重构：
 * 1. RenderTransform作为刷新入口，重新计算每个专辑封面的位置和叠放顺序
 * 2. 区分当前封面左边和右边的不同处理
 * 3. 计算3D旋转、倾斜和平移
 * 4. 使用动画实现平滑过渡
 */
class ComposeCoverFlowView(context: Context) : FrameLayout(context), ScreenView {

    private val albums = MediaStoreUtil.getAlbumList()

    // 使用MutableState确保Compose能观察到变化
    // 初始位置设为3（中间位置，显示第4张专辑，左右各3张）
    private val currentIndexState = mutableIntStateOf(3.coerceAtMost(albums.size - 1))
    private val targetIndexState = mutableIntStateOf(3.coerceAtMost(albums.size - 1))

    private val composeView = androidx.compose.ui.platform.ComposeView(context).apply {
        setContent {
            CoverFlowContent(
                albums = albums,
                currentIndexState = currentIndexState,
                targetIndexState = targetIndexState
            )
        }
    }

    init {
        // 设置黑色背景，避免切换时的闪烁
        setBackgroundColor(android.graphics.Color.BLACK)
        addView(composeView)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        composeView.measure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        composeView.layout(0, 0, right - left, bottom - top)
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
        // 不需要重新设置content，MutableState会保持状态
    }

    override fun onViewDelete() {
    }

    override fun slide(slideVal: Int): Boolean {
        val newIndex = targetIndexState.intValue + slideVal
        Log.d("CoverFlow", "slide called: slideVal=$slideVal, current=${currentIndexState.intValue}, target=${targetIndexState.intValue}, newIndex=$newIndex, albums.size=${albums.size}")
        if (newIndex in albums.indices) {
            targetIndexState.intValue = newIndex
            Log.d("CoverFlow", "slide accepted: new target=${targetIndexState.intValue}")
            return true
        }
        Log.d("CoverFlow", "slide rejected: newIndex out of bounds")
        return false
    }
}

/**
 * CoverFlow内容组件 - 使用RenderTransform机制管理位置和动画
 */
@Composable
private fun CoverFlowContent(
    albums: List<MusicList>,
    currentIndexState: MutableIntState,
    targetIndexState: MutableIntState
) {
    val density = LocalDensity.current

    // 直接读取State的值
    val currentIndex = currentIndexState.intValue
    val targetIndex = targetIndexState.intValue

    // 使用State来跟踪动画状态
    var animatingFromIndex by remember { mutableIntStateOf(currentIndex) }
    var animatingToIndex by remember { mutableIntStateOf(targetIndex) }

    // 动画进度
    val animationProgress = remember { Animatable(0f) }

    // 监听外部索引变化，直接切换位置（无动画，避免闪烁）
    LaunchedEffect(targetIndex) {
        if (targetIndex != animatingToIndex) {
            animatingToIndex = targetIndex
            currentIndexState.intValue = targetIndex
            Log.d("CoverFlow", "Position updated: now at $animatingToIndex")
        }
    }

    // 使用BoxWithConstraints获取实际可用尺寸
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        // 获取实际可用尺寸
        val containerWidth = maxWidth
        val containerHeight = maxHeight

        // 根据屏幕宽度动态计算参数
        val coverSize = (containerWidth / 2.2f).coerceIn(100.dp, 150.dp)
        val coverSizePx = with(density) { coverSize.toPx() }

        // 计算屏幕尺寸（像素）- 这是ScreenView的实际尺寸
        val screenWidthPx = with(density) { containerWidth.toPx() }
        val screenHeightPx = with(density) { containerHeight.toPx() }
        val centerY = screenHeightPx * 0.4f

        Log.d("CoverFlow", "ScreenView尺寸: width=${screenWidthPx.toInt()}, height=${screenHeightPx.toInt()}")

        // 计算布局参数
        val displayCount = 7
        val centerOffset = displayCount / 2 // 3
        // 计算布局：7张图均匀分布在屏幕宽度内
        // 最左边贴着左边缘，最右边贴着右边缘
        val step = (screenWidthPx - coverSizePx) / (displayCount - 1)

        Log.d("CoverFlow", "Layout: screenWidth=${screenWidthPx.toInt()}, coverSize=${coverSizePx.toInt()}, step=${step.toInt()}, animatingToIndex=$animatingToIndex")

        // 直接计算布局数据，不使用remember
        val coverFlowData = List(displayCount) { displayPos ->
            val albumIndex = animatingToIndex + displayPos - centerOffset
            val offsetFromCenter = displayPos - centerOffset

            // 计算基础X位置
            val baseX = displayPos * step

            // 计算3D旋转：左边向右旋转，右边向左旋转
            // 增加旋转角度，让透视效果更明显
            val rotateY = 75f
            val targetRotateY = when {
                offsetFromCenter < 0 -> -rotateY  // 左边：向右旋转
                offsetFromCenter > 0 -> -rotateY  // 右边：也向右旋转
                else -> 0f                        // 当前：不旋转
            }

            // 计算平行变换（skewY）：增加立体感
            // 左边图片底部向内倾斜，右边图片底部向内倾斜
            val skewY = 15f
            val targetSkewY = when {
                offsetFromCenter < 0 -> skewY   // 左边：正倾斜
                offsetFromCenter > 0 -> -skewY  // 右边：负倾斜
                else -> 0f                      // 当前：不倾斜
            }

            // 补偿透视偏移：旋转后图片视觉上会向内收缩
            // 根据旋转角度向外调整位置（增加偏移量）
            val perspectiveOffset = when {
                offsetFromCenter < 0 -> -coverSizePx * 0.45f  // 左边：向左偏移
                offsetFromCenter > 0 -> coverSizePx * 0.45f   // 右边：向右偏移
                else -> 0f                                    // 中间：不偏移
            }
            val finalX = baseX + perspectiveOffset

            if (displayPos == 0 || displayPos == 6) {
                Log.d("CoverFlow", "Pos $displayPos: baseX=${baseX.toInt()}, finalX=${finalX.toInt()}, albumIndex=$albumIndex")
            }

            // 计算ZIndex：当前位置(3)最靠上，距离越远越靠下
            val zIndex = when {
                offsetFromCenter == 0 -> 100f
                else -> 100f - abs(offsetFromCenter) * 10f
            }

            // 缩放：所有封面都不缩小
            val targetScale = 1f

            CoverFlowData(
                displayPos = displayPos,
                albumIndex = albumIndex,
                transform = CoverTransform(
                    x = finalX,
                    y = centerY - coverSizePx / 2f,
                    rotationY = targetRotateY,
                    skewY = targetSkewY,
                    translationY = 0f,
                    scale = targetScale,
                    zIndex = zIndex,
                    alpha = 1f
                )
            )
        }

        // CoverFlow区域 - 使用Column垂直排列封面和文字
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 封面区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Transparent)
            ) {
                coverFlowData.forEach { data ->
                    // 使用 albumIndex 作为 key，这样只有真正变化的专辑才会重组
                    key(data.albumIndex) {
                        if (data.albumIndex in albums.indices) {
                            CoverFlowItem(
                                album = albums[data.albumIndex],
                                transform = data.transform,
                                coverSize = coverSize,
                                position = data.displayPos
                            )
                        } else {
                            CoverFlowPlaceholder(
                                transform = data.transform,
                                coverSize = coverSize,
                                position = data.displayPos
                            )
                        }
                    }
                }
            }

            // 专辑信息文字 - 紧贴着封面下方
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val currentAlbum = if (animatingToIndex in albums.indices) albums[animatingToIndex] else null
                currentAlbum?.let { album ->
                    Text(
                        text = album.title ?: "",
                        color = Color.White,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = album.subtitle ?: "",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * CoverFlow数据类，用于缓存布局计算
 */
private data class CoverFlowData(
    val displayPos: Int,
    val albumIndex: Int,
    val transform: CoverTransform
)

/**
 * RenderTransform - 刷新的入口
 * 当当前位置发生变化时，调用此方法，重新计算每个专辑封面的位置和叠放顺序
 *
 * 封面叠层的顺序是当前封面最靠上，左右两边的封面随着距离由近及远，依次向下叠放。
 *
 * @param pos 当前封面的索引
 * @param currentPos 当前选中的位置
 * @param totalCount 总封面数量
 * @param centerX 屏幕中心X坐标（像素）
 * @param centerY 屏幕中心Y坐标（像素）
 * @param coverSizePx 封面尺寸（像素）
 */
/**
 * RenderTransform - 刷新的入口
 * 基于显示位置（0-6）计算每个封面的位置和叠放顺序
 * 
 * 显示7个位置：0(最左) 1 2 3(当前) 4 5 6(最右)
 * 位置3始终对应当前选中的专辑
 * 
 * 7张图在一个屏幕内，使用固定格子宽度，允许部分重叠
 */
private fun renderTransform(
    displayPos: Int,        // 显示位置 0-6
    currentPos: Int,        // 当前选中的专辑索引
    totalCount: Int,
    screenWidth: Float,     // 屏幕宽度（像素）
    centerY: Float,
    coverSizePx: Float
): CoverTransform {
    // 配置参数
    val centerOffset = 3 // 中心位置偏移（位置3是中心）
    
    // 计算格子宽度：让7个格子刚好填满屏幕宽度的90%
    // 格子宽度 < 封面宽度，实现重叠效果
    val cellWidth = (screenWidth * 0.9f) / 7f
    
    // 计算起始位置（居中）
    val totalWidth = cellWidth * 7f
    val startX = (screenWidth - totalWidth) / 2f
    
    // 计算最终X坐标
    val finalX = startX + displayPos * cellWidth

    // 计算相对于中心位置的偏移
    val offsetFromCenter = displayPos - centerOffset

    // 计算ZIndex：当前位置(3)最靠上，距离越远越靠下
    val zIndex = when {
        offsetFromCenter == 0 -> 100f // 当前位置最上层
        else -> 100f - abs(offsetFromCenter) * 10f
    }

    return CoverTransform(
        x = finalX,
        y = centerY - coverSizePx / 2f,
        rotationY = 0f,
        skewY = 0f,
        translationY = 0f,
        scale = 1f,
        zIndex = zIndex,
        alpha = 1f
    )
}

/**
 * CoverFlow单个专辑项 - 使用RenderTransform计算的属性
 * 使用remember缓存图片，避免重复加载
 */
@Composable
private fun CoverFlowItem(
    album: MusicList,
    transform: CoverTransform,
    coverSize: androidx.compose.ui.unit.Dp,
    position: Int
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // 使用remember缓存bitmap，key为album.id，避免重复加载
    var bitmap by remember(album.id) {
        mutableStateOf<Bitmap?>(null)
    }

    // 只在bitmap为null时加载
    LaunchedEffect(album.id) {
        if (bitmap == null) {
            bitmap = loadAlbumBitmap(context, album.id ?: 0L)
        }
    }

    // 将像素位置转换为Dp
    val xOffsetDp = with(density) { transform.x.toDp() }
    val yOffsetDp = with(density) { transform.y.toDp() }

    // 使用 graphicsLayer 实现 3D 效果
    // rotationY会导致透视偏移，需要通过调整位置来补偿
    Box(
        modifier = Modifier
            .offset(x = xOffsetDp, y = yOffsetDp)
            .graphicsLayer {
                scaleX = transform.scale
                scaleY = transform.scale
                rotationY = transform.rotationY
                // 使用rotationX模拟skewY倾斜效果
                rotationX = transform.skewY * 0.5f
                cameraDistance = 300f
                transformOrigin = TransformOrigin.Center
            }
            .zIndex(transform.zIndex)
            .alpha(transform.alpha)
    ) {
        // 内部 Box 设置实际大小
        Box(
            modifier = Modifier.size(coverSize)
        ) {
            // 始终显示图片，加载中时显示上一个图片或透明
            val displayBitmap = bitmap
            if (displayBitmap != null) {
                val reflectedBitmap = remember(displayBitmap) {
                    createReflectedBitmap(displayBitmap)
                }
                Image(
                    bitmap = reflectedBitmap.asImageBitmap(),
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * CoverFlow占位符 - 用于不足7张专辑时填充
 */
@Composable
private fun CoverFlowPlaceholder(
    transform: CoverTransform,
    coverSize: androidx.compose.ui.unit.Dp,
    position: Int
) {
    val density = LocalDensity.current

    // 将像素位置转换为Dp
    val xOffsetDp = with(density) { transform.x.toDp() }
    val yOffsetDp = with(density) { transform.y.toDp() }

    // 使用 graphicsLayer 实现 3D 效果
    Box(
        modifier = Modifier
            .offset(x = xOffsetDp, y = yOffsetDp)
            .graphicsLayer {
                scaleX = transform.scale
                scaleY = transform.scale
                rotationY = transform.rotationY
                cameraDistance = 800f
                transformOrigin = TransformOrigin.Center
            }
            .zIndex(transform.zIndex)
            .alpha(transform.alpha)
    ) {
        // 内部 Box 设置实际大小
        Box(
            modifier = Modifier
                .size(coverSize)
                .background(Color.Transparent)
        )
    }
}

/**
 * CoverFlow变换属性数据类
 */
private data class CoverTransform(
    val x: Float,           // X轴位置（像素，相对于屏幕左侧）
    val y: Float,           // Y轴位置（像素）
    val rotationY: Float,   // Y轴旋转角度
    val skewY: Float,       // Y轴倾斜角度
    val translationY: Float,// Y轴平移
    val scale: Float,       // 缩放比例
    val zIndex: Float,      // 叠放顺序
    val alpha: Float        // 透明度
)

// 常量
private const val ANIMATION_DURATION_MS = 400

// 全局图片缓存
private val albumBitmapCache = mutableMapOf<Long, Bitmap?>()

/**
 * 加载专辑封面Bitmap（带缓存）
 */
private suspend fun loadAlbumBitmap(context: Context, albumId: Long): Bitmap? {
    // 先检查缓存
    albumBitmapCache[albumId]?.let { return it }
    
    // 加载图片
    return try {
        val bitmap = MediaUtil.getAlbumImage(albumId)
        // 存入缓存
        albumBitmapCache[albumId] = bitmap
        bitmap
    } catch (e: Exception) {
        null
    }
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
