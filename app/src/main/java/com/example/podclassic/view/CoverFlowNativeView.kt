package com.example.podclassic.view

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * CoverFlow Native View - 支持Z轴深度的原生容器
 *
 * 核心功能：
 * 1. 使用原生 ImageView 支持 translationZ 属性
 * 2. 禁用剪裁，允许3D变换超出边界
 * 3. 高效的动画驱动：通过 update() 方法同步状态
 */
class CoverFlowNativeView(context: Context) : FrameLayout(context) {

    companion object {
        private const val MAX_VISIBLE_ITEMS = 13

        /**
         * Set to true to make ImageViews visible in Android Layout Inspector
         *
         * When enabled:
         * - Uses LAYER_TYPE_SOFTWARE for better inspector compatibility
         * - Adds semi-transparent red background for visibility
         * - Simplifies dispatchDraw to avoid custom clipping
         * - Adds detailed debug info to contentDescription
         *
         * Note: May affect performance and 3D rendering quality in debug mode
         */
        private const val DEBUG_LAYOUT_INSPECTOR = false

        /**
         * 平铺模式：禁用3D变换，只做简单平铺
         * true: 图片平铺，无旋转、无深度
         * false: 启用完整3D效果（旋转、深度、透视）
         * public: 以便 Compose 侧访问
         */
        const val FLAT_MODE = false
    }

    private val imageViews = mutableListOf<ImageView>()
    private val albumIds = mutableListOf<Long?>()
    private val viewIds = mutableListOf<Int>()  // NEW: Track view IDs for debugging

    // 变换数据类
    data class TransformData(
        val bitmap: Bitmap?,
        val x: Float,              // 屏幕X位置
        val y: Float,              // 屏幕Y位置
        val rotationY: Float,
        val translationZ: Float,
        val scaleX: Float,
        val scaleY: Float,
        val translationX: Float,
        val translationY: Float,
        val alpha: Float,
        // ✅ NEW: Add missing 3D transform properties
        val rotationX: Float = 0f,
        val cameraDistance: Float = 1200f,
        val transformOriginX: Float = 0.5f,
        val transformOriginY: Float = 0.5f,
        val modeScaleCompensation: Float = 1f  // ✅ 新增：模式缩放补偿因子
    )

    init {
        // 关键：禁用剪裁，允许3D变换超出边界
        clipChildren = false
        clipToPadding = false

        // 横屏最多显示 13 张；竖屏通过 setVisibleItemCount 保持 7 张。
        repeat(MAX_VISIBLE_ITEMS) { i ->
            val imageView = createImageView(context)
            // Generate unique view ID for Layout Inspector debugging
            val viewId = View.generateViewId()
            imageView.id = viewId
            viewIds.add(viewId)
            // Set initial content description for debugging
            imageView.contentDescription = "CoverFlow Position $i"
            imageViews.add(imageView)
            albumIds.add(null)
            addView(imageView)
        }
        Log.d("CoverFlowNativeView", "Created ${childCount} ImageViews with IDs: $viewIds")
    }

    /**
     * 创建ImageView - 使用固定尺寸150dp×225dp
     * 150dp: 封面宽度
     * 225dp: 封面高度150dp + 倒影75dp（1.5倍）
     */
    private fun createImageView(context: Context): ImageView {
        // 使用固定尺寸：150dp × 225dp（含1.5倍倒影）
        val density = resources.displayMetrics.density
        val coverWidth = (150 * density).toInt()   // 150dp
        val coverHeight = (225 * density).toInt()  // 225dp (150 × 1.5 for reflection)

        return ImageView(context).apply {
            layoutParams = LayoutParams(coverWidth, coverHeight)
            scaleType = ImageView.ScaleType.FIT_XY

            // Use software layer in debug mode for Layout Inspector compatibility
            setLayerType(
                if (DEBUG_LAYOUT_INSPECTOR) LAYER_TYPE_SOFTWARE else LAYER_TYPE_HARDWARE,
                null
            )

            // Add debug background color in debug mode
            if (DEBUG_LAYOUT_INSPECTOR) {
                setBackgroundColor(android.graphics.Color.argb(128, 255, 0, 0))
            }

            Log.d("CoverFlowNativeView", "Created ImageView: ${coverWidth}x$coverHeight, " +
                    "layerType=${if (DEBUG_LAYOUT_INSPECTOR) "SOFTWARE" else "HARDWARE"}")
        }
    }

    /**
     * 更新单个位置的图片和变换
     *
     * @param position 位置索引 (0-6)
     * @param albumId 专辑ID，用于缓存判断
     * @param transform 变换数据
     */
    fun updateImage(position: Int, albumId: Long?, transform: TransformData) {
        if (position !in imageViews.indices) return

        val imageView = imageViews[position]

        // ImageView 保持固定尺寸 (150dp × 225dp)，不进行动态调整
        // FLAT_MODE 只禁用3D变换（旋转、Z轴深度），不改变布局尺寸

        // Update content description with debug info for Layout Inspector
        imageView.contentDescription = """
            CoverFlow[$position]
            Album: $albumId
            Pos: x=${transform.x.toInt()}, y=${transform.y.toInt()}
            Rotation: ${transform.rotationY}°
            Scale: ${transform.scaleX}x${transform.scaleY}
            Z: ${transform.translationZ}
        """.trimIndent()

        // 变换每帧更新，位图只在内容真正变化或异步加载完成时更新。
        // 避免在动画期间反复 invalidating 大尺寸 Bitmap。
        if (albumIds[position] != albumId || (imageView.drawable == null && transform.bitmap != null)) {
            transform.bitmap?.let(imageView::setImageBitmap) ?: imageView.setImageDrawable(null)
            albumIds[position] = albumId
        }

        // 应用原生3D变换并添加调试日志
        // 详细调试：计算期望的右边缘位置
        // 注意：imageView.width 此时为0（尚未布局），需要使用 layoutParams.width
        val imageWidth = (imageView.layoutParams as? LayoutParams)?.width ?: 0
        val expectedRightEdge = transform.x + imageWidth * transform.scaleX

        val imageHeight = (imageView.layoutParams as? LayoutParams)?.height ?: 0
        if (DEBUG_LAYOUT_INSPECTOR) Log.d("CoverFlowPosition", """
            Position $position:
            x=${transform.x}, y=${transform.y}
            layoutParams.width=$imageWidth, layoutParams.height=$imageHeight
            pivotX=${transform.transformOriginX * imageWidth}, pivotY=${transform.transformOriginY * imageHeight}
            scaleX=${transform.scaleX}, scaleY=${transform.scaleY}
            rotationY=${transform.rotationY}
            translationZ=${transform.translationZ}
            Expected right edge: $expectedRightEdge
            Screen width: ${(parent as? View)?.width ?: -1}
            albumId=$albumId
            bitmap=${transform.bitmap?.let { "${it.width}x${it.height}" } ?: "null"}
            FLAT_MODE=$FLAT_MODE
        """.trimIndent())

        imageView.apply {
            // ❌ x 和 y 会被 FrameLayout 布局系统覆盖
            // ✅ 使用 translationX/translationY 进行位置偏移（在布局之后应用）
            x = 0f
            y = 0f

            if (FLAT_MODE) {
                // 平铺模式：禁用3D变换，但保留缩放用于位图尺寸调整
                translationZ = 0f
                rotationY = 0f
                rotationX = 0f
                scaleX = transform.scaleX
                scaleY = transform.scaleY
                cameraDistance = 0f
                // ✅ 使用 translation 设置位置
                translationX = transform.x
                translationY = transform.y
                // ✅ 修复：使用 layoutParams 替代 width/height（布局前为0）
                val lp = layoutParams as? LayoutParams
                pivotX = (lp?.width?.toFloat() ?: 0f) / 2f
                pivotY = (lp?.height?.toFloat() ?: 0f) / 2f
            } else {
                // 3D模式：应用完整变换
                translationZ = transform.translationZ
                rotationY = transform.rotationY
                rotationX = transform.rotationX
                cameraDistance = transform.cameraDistance
                // ✅ 使用 translation 设置位置（覆盖默认的 0 值）
                translationX = transform.x
                translationY = transform.y
                // ✅ 修复：使用 layoutParams 替代 width/height（布局前为0）
                val lp = layoutParams as? LayoutParams
                pivotX = (lp?.width?.toFloat() ?: 0f) * transform.transformOriginX
                pivotY = (lp?.height?.toFloat() ?: 0f) * transform.transformOriginY
                // ✅ 应用3D模式缩放补偿
                scaleX = transform.scaleX * transform.modeScaleCompensation
                scaleY = transform.scaleY * transform.modeScaleCompensation
            }

            alpha = transform.alpha

            // Debug: log the actual applied values
            if (FLAT_MODE && position == 3) {
                Log.d("CoverFlowNativeView", "FLAT_MODE applied transform: " +
                        "x=$x, y=$y, scaleX=$scaleX, scaleY=$scaleY, " +
                        "translationZ=$translationZ, rotationY=$rotationY, " +
                        "layoutWidth=${layoutParams.width}, layoutHeight=${layoutParams.height}")
            }

            // Add debug info in Layout Inspector mode
            if (DEBUG_LAYOUT_INSPECTOR) {
                // Force refresh contentDescription with layout info
                contentDescription = """
                    CoverFlow[$position]
                    Album: $albumId
                    ScreenPos: x=${x.toInt()}, y=${y.toInt()}
                    Layout: w=${width}, h=${height}
                    Transform: rotY=$rotationY, rotX=$rotationX, scale=$scaleX×$scaleY
                    Z: $translationZ, cameraDist=$cameraDistance
                    Pivot: ($pivotX, $pivotY)
                    ViewId: $id
                """.trimIndent()

                // Add outline provider for better bounds visibility in inspector
                outlineProvider = android.view.ViewOutlineProvider.BOUNDS
            }
        }

        // 始终可见
        imageView.visibility = View.VISIBLE

        // 详细调试：记录实际应用的值
        val actualImageWidth = (imageView.layoutParams as? LayoutParams)?.width ?: 0
        val actualRightEdge = imageView.x + actualImageWidth * imageView.scaleX
        if (DEBUG_LAYOUT_INSPECTOR) Log.d("CoverFlowApplied", """
            Position $position (AFTER apply):
            Actual x=${imageView.x}, y=${imageView.y}
            Actual scaleX=${imageView.scaleX}, scaleY=${imageView.scaleY}
            Actual pivotX=${imageView.pivotX}, pivotY=${imageView.pivotY}
            Actual rotationY=${imageView.rotationY}
            layoutParams.width=$actualImageWidth
            Calculated right edge: $actualRightEdge
            Parent width: ${(parent as? View)?.width ?: -1}
        """.trimIndent())
    }

    /**
     * 配置当前方向需要的封面数量，并清理隐藏位置的旧图片。
     */
    fun setVisibleItemCount(count: Int) {
        val visibleCount = count.coerceIn(0, imageViews.size)
        imageViews.forEachIndexed { index, imageView ->
            val visible = index < visibleCount
            imageView.visibility = if (visible) View.VISIBLE else View.GONE
            if (!visible && albumIds[index] != null) {
                imageView.setImageDrawable(null)
                albumIds[index] = null
            }
        }
    }

    /**
     * 设置容器的尺寸（用于计算位置）
     */
    fun setContainerSize(width: Int, height: Int) {
        layoutParams = LayoutParams(width, height)
    }

    /**
     * 扩大绘制区域以支持3D变换
     */
    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        if (DEBUG_LAYOUT_INSPECTOR) {
            // Simple draw for Layout Inspector compatibility
            super.dispatchDraw(canvas)
        } else {
            // Normal draw with extended clip region for 3D transforms
            val saveCount = canvas.save()
            // 扩大绘制区域，避免旋转后的图片被裁剪（对称扩展）
            canvas.clipRect(
                -width * 0.5f,      // 左边界扩展到 -50%
                -height * 0.5f,     // 上边界扩展到 -50%
                width * 1.5f,       // 右边界扩展到 +150%
                height * 1.5f       // 下边界扩展到 +150%
            )
            super.dispatchDraw(canvas)
            canvas.restoreToCount(saveCount)
        }
    }
}
