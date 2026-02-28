package com.example.podclassic.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.RelativeLayout.LayoutParams
import com.example.podclassic.base.Core
import com.example.podclassic.base.ScreenView
import com.example.podclassic.bean.Music
import com.example.podclassic.service.MediaPresenter
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.FileUtil
import com.example.podclassic.util.MediaStoreUtil
import com.example.podclassic.values.Colors
import com.example.podclassic.values.Strings
import com.example.podclassic.view.MusicPlayerView3rd
import com.example.podclassic.widget.RecyclerListView
import java.io.File
import java.util.*


class MainView(context: Context) : RelativeLayout(context), ScreenView {

    private val listView = RecyclerListView(context)
    // 图片容器（可见区域）
    private val coverContainer = FrameLayout(context)
    // 图片视图（比容器大，在容器内飘动）
    private val coverImageView = ImageView(context)
    // 分割线视图
    private lateinit var dividerView: View

    // 保存容器和图片的尺寸
    private var savedContainerWidth = 0
    private var savedContainerHeight = 0
    private var savedImageWidth = 0
    private var savedImageHeight = 0

    private val random = Random()
    private val coverImages = arrayOf("img/1.jpg", "img/2.jpg", "img/3.jpg", "img/4.jpg", "img/5.jpg", "img/6.jpg")

    // 动画相关
    private var xAnimator: ObjectAnimator? = null
    private var yAnimator: ObjectAnimator? = null
    private var isAnimating = false
    
    // 蛇形扫描算法相关
    private var scanGridCols = 3  // 网格列数
    private var scanGridRows = 3  // 网格行数
    private var currentGridX = 0  // 当前网格X索引
    private var currentGridY = 0  // 当前网格Y索引
    private var scanDirectionX = 1 // X方向扫描方向：1为右，-1为左
    private var scanDirectionY = 1 // Y方向扫描方向：1为下，-1为上
    private var isHorizontalScan = true // 是否以横向扫描为主
    private val visitedGrids = mutableSetOf<Pair<Int, Int>>() // 记录已访问的网格

    init {
        // 启用硬件加速
        coverImageView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    override fun getTitle(): String? {
        return if (SPManager.getBoolean(
                SPManager.SP_SHOW_TIME
            )
        ) {
            null
        } else {
            Strings.iPod
        }
    }

    val item = RecyclerListView.Item(Strings.NOW_PLAYING, object : RecyclerListView.OnItemClickListener {
        override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
            if (SPManager.getInt(SPManager.Theme.SP_NAME) == SPManager.Theme.IPOD_3RD.id) {
                Core.addView(MusicPlayerView3rd(context))
            } else {
                Core.addView(MusicPlayerView(context))
            }
            return true
        }

        override fun onItemLongClick(index: Int, listView: RecyclerListView): Boolean {
            return false
        }
    }, true)

    init {
        // 检查是否是横屏模式
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        // 计算分割线位置（ListView宽度）
        val dividerPosition = if (isLandscape) {
            (resources.displayMetrics.widthPixels * 0.45).toInt() // 横屏时占45%
        } else {
            (resources.displayMetrics.widthPixels * 0.30).toInt() // 竖屏时占30%
        }
        
        // 设置图片容器（可见区域）- 从分割线右侧开始到屏幕右边
        savedContainerWidth = resources.displayMetrics.widthPixels - dividerPosition
        // 估算容器高度（屏幕高度减去状态栏和标题栏）
        savedContainerHeight = (resources.displayMetrics.heightPixels * 0.85).toInt()
        val containerParams = LayoutParams(savedContainerWidth, LayoutParams.MATCH_PARENT)
        containerParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
        containerParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
        addView(coverContainer, containerParams)
        coverContainer.setBackgroundColor(android.graphics.Color.BLACK)

        // 设置图片视图的初始布局参数，实际尺寸将在图片加载后根据比例计算
        val imageParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        coverImageView.layoutParams = imageParams
        coverImageView.scaleType = ImageView.ScaleType.FIT_XY  // 使用FIT_XY，我们将手动计算尺寸
        coverContainer.addView(coverImageView)
        android.util.Log.d("MainView", "CoverImageView added to container, width: $savedContainerWidth")

        // 设置ListView
        val listParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        listParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
        listParams.width = dividerPosition
        // 设置ListView的z轴顺序，使其位于ImageView上方
        listParams.addRule(RelativeLayout.ABOVE, -1)
        addView(listView, listParams)
        // 设置ListView的背景为浅灰色，防止专辑图透出影响文字阅读
        listView.setBackgroundColor(Colors.background)

        // 添加中间竖线分割，右侧横向内阴影效果
        dividerView = object : View(context) {
            private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)

            init {
                // 设置透明背景
                setBackgroundColor(Color.TRANSPARENT)
            }

            override fun onDraw(canvas: Canvas) {
                val width = width.toFloat()
                val height = height.toFloat()

                // 绘制右侧内阴影（从中间到右边缘，由黑到透明）
                val rightShadow = LinearGradient(
                    width * 0.1f, 0f,
                    width, 0f,
                    intArrayOf(
                        Color.argb(240, 0, 0, 0),    // 左侧很黑的阴影
                        Color.argb(120, 0, 0, 0),    // 过渡
                        Color.argb(40, 0, 0, 0),     // 中间
                        Color.argb(0, 0, 0, 0)       // 右边缘透明
                    ),
                    floatArrayOf(0f, 0.3f, 0.6f, 1f),
                    Shader.TileMode.CLAMP
                )
                shadowPaint.shader = rightShadow
                canvas.drawRect(width * 0.1f, 0f, width, height, shadowPaint)
            }
        }
        val dividerParams = LayoutParams(48, LayoutParams.MATCH_PARENT)
        dividerParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
        dividerParams.leftMargin = dividerPosition - 2 // 分割线向左微调2像素
        addView(dividerView, dividerParams)

        // 初始化菜单项
        val menuItems = arrayListOf(
            RecyclerListView.Item(Strings.SONG, object : RecyclerListView.OnItemClickListener {
                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                    Core.addView(MusicView(context))
                    return true
                }

                override fun onItemLongClick(index: Int, listView: RecyclerListView): Boolean {
                    return false
                }
            }, true),
            RecyclerListView.Item(Strings.PHOTO, object : RecyclerListView.OnItemClickListener {
                val list by lazy { MediaStoreUtil.getPhotoList() }
                private fun getItemList(): ArrayList<RecyclerListView.Item> {
                    val itemList = ArrayList<RecyclerListView.Item>()
                    for (file in list) {
                        itemList.add(RecyclerListView.Item(file.name, null, false))
                    }
                    return itemList
                }

                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                    Core.addView(
                        ItemListView(
                            context,
                            getItemList(),
                            Strings.PHOTO,
                            object : RecyclerListView.OnItemClickListener {
                                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                                    Core.addView(ImageView(context, list, index))
                                    return true
                                }

                                override fun onItemLongClick(index: Int, listView: RecyclerListView): Boolean {
                                    return false
                                }
                            })
                    )
                    return true
                }

                override fun onItemLongClick(index: Int, listView: RecyclerListView): Boolean {
                    return false
                }
            }, true),
            RecyclerListView.Item(Strings.VIDEO, object : RecyclerListView.OnItemClickListener {
                val list by lazy { MediaStoreUtil.getVideoList() }

                private fun getItemList(): ArrayList<RecyclerListView.Item> {
                    val itemList = ArrayList<RecyclerListView.Item>()
                    for (file in list) {
                        itemList.add(RecyclerListView.Item(file.name, null, false))
                    }
                    return itemList
                }

                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                    Core.addView(
                        ItemListView(
                            context,
                            getItemList(),
                            Strings.VIDEO,
                            object : RecyclerListView.OnItemClickListener {
                                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                                    val file = list[index]
                                    return if (file.exists()) {
                                        Core.addView(VideoView(context, list[index]))
                                        true
                                    } else {
                                        false
                                    }
                                }

                                override fun onItemLongClick(index: Int, listView: RecyclerListView): Boolean {
                                    return false
                                }
                            })
                    )
                    return true
                }

                override fun onItemLongClick(index: Int, listView: RecyclerListView): Boolean {
                    return false
                }
            }, true),

            RecyclerListView.Item(Strings.FILE, object : RecyclerListView.OnItemClickListener {
                private fun getItemList(): ArrayList<RecyclerListView.Item>? {
                    val sdCardPath = FileUtil.getSDCardPath() ?: return null
                    val itemList = ArrayList<RecyclerListView.Item>()
                    itemList.add(RecyclerListView.Item(Strings.INTERNAL_STORAGE, object : RecyclerListView.OnItemClickListener {
                        override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                            Core.addView(
                                FileView(
                                    context,
                                    Environment.getRootDirectory()
                                )
                            )
                            return true
                        }

                        override fun onItemLongClick(index: Int, listView: RecyclerListView): Boolean {
                            return false
                        }
                    }, true))

                    itemList.add(RecyclerListView.Item(Strings.SDCARD, object : RecyclerListView.OnItemClickListener {
                        override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                            Core.addView(FileView(context, File(sdCardPath)))
                            return true
                        }

                        override fun onItemLongClick(index: Int, listView: RecyclerListView): Boolean {
                            return false
                        }
                    }, true))
                    return itemList
                }

                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                    val itemList = getItemList()
                    if (itemList == null) {
                        Core.addView(FileView(context, Environment.getExternalStorageDirectory()))
                    } else {
                        Core.addView(ItemListView(context, itemList, Strings.FILE, null))
                    }
                    return true
                }

                override fun onItemLongClick(index: Int, listView: RecyclerListView): Boolean {
                    return false
                }
            }, true),

            RecyclerListView.Item(Strings.EXTRA_APPLICATION, object : RecyclerListView.OnItemClickListener {
                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                    Core.addView(AppsView(context))
                    return true
                }

                override fun onItemLongClick(index: Int, listView: RecyclerListView): Boolean {
                    return false
                }
            }, true),

            RecyclerListView.Item(Strings.SETTINGS, object : RecyclerListView.OnItemClickListener {
                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                    Core.addView(
                        SettingsView(
                            context
                        )
                    )
                    return true
                }

                override fun onItemLongClick(index: Int, listView: RecyclerListView): Boolean {
                    return false
                }

            }, true),
            RecyclerListView.Item(Strings.SHUFFLE_PLAY, object : RecyclerListView.OnItemClickListener {
                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                    MediaPresenter.shufflePlay()
                    // 根据主题选择使用哪个播放器视图
                    if (SPManager.getInt(SPManager.Theme.SP_NAME) == SPManager.Theme.IPOD_3RD.id) {
                        Core.addView(MusicPlayerView3rd(context))
                    } else {
                        Core.addView(MusicPlayerView(context))
                    }
                    return true
                }

                override fun onItemLongClick(index: Int, listView: RecyclerListView): Boolean {
                    return false
                }
            }, false)
        )

        // 添加菜单项
        for (menuItem in menuItems) {
            listView.add(menuItem)
        }
    }

    override fun enter(): Boolean {
        return listView.onItemClick()
    }

    override fun enterLongClick(): Boolean {
        return listView.onItemLongClick()
    }

    override fun slide(slideVal: Int): Boolean {
        return listView.onSlide(slideVal)
    }

    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_SINGLE
    }

    override fun onViewAdd() {
        android.util.Log.d("MainView", "onViewAdd() called")
        if (MediaPresenter.getCurrent() == null) {
            listView.remove(item)
            android.util.Log.d("MainView", "No current music, removed 'Now Playing' item")
        } else {
            listView.addIfNotExist(item)
            android.util.Log.d("MainView", "Added 'Now Playing' item")
        }

        // 检查父容器尺寸是否已测量
        val parentWidth = (parent as? android.view.ViewGroup)?.width ?: 0
        val parentHeight = (parent as? android.view.ViewGroup)?.height ?: 0
        
        if (parentWidth > 0 && parentHeight > 0) {
            // 尺寸已测量，直接初始化
            updateLayoutForOrientation()
            updateCoverImage()
            resetCoverPosition()
            startCoverAnimation()
        } else {
            // 延迟一帧等待测量
            post {
                updateLayoutForOrientation()
                updateCoverImage()
                resetCoverPosition()
                startCoverAnimation()
            }
        }
    }
    
    /**
     * 根据当前屏幕方向更新布局参数
     */
    private fun updateLayoutForOrientation() {
        // 检查当前屏幕方向
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        // 获取父容器（ScreenView）的尺寸
        val rawParentWidth = (parent as? android.view.ViewGroup)?.width ?: 0
        val rawParentHeight = (parent as? android.view.ViewGroup)?.height ?: 0
        
        // 如果父容器尺寸为0，使用默认值（基于屏幕尺寸估算）
        val parentWidth = if (rawParentWidth > 0) rawParentWidth else resources.displayMetrics.widthPixels
        val parentHeight = if (rawParentHeight > 0) rawParentHeight else (resources.displayMetrics.heightPixels * 0.35).toInt()
        
        // 计算分割线位置（ListView宽度）
        val dividerPosition = if (isLandscape) {
            (parentWidth * 0.45).toInt() // 横屏时占45%
        } else {
            (parentWidth * 0.30).toInt() // 竖屏时占30%
        }
        
        // 更新图片容器尺寸 - 基于父容器尺寸
        savedContainerWidth = parentWidth - dividerPosition
        savedContainerHeight = parentHeight
        
        // 更新容器布局参数
        val containerParams = coverContainer.layoutParams as LayoutParams
        containerParams.width = savedContainerWidth
        coverContainer.layoutParams = containerParams
        
        // 更新ListView宽度
        val listParams = listView.layoutParams as LayoutParams
        listParams.width = dividerPosition
        listView.layoutParams = listParams
        
        // 更新分割线位置
        val dividerParams = dividerView.layoutParams as LayoutParams
        dividerParams.leftMargin = dividerPosition - 2
        dividerView.layoutParams = dividerParams
        
        android.util.Log.d("MainView", "Layout updated for orientation: landscape=$isLandscape, parent=${parentWidth}x${parentHeight}, container=${savedContainerWidth}x${savedContainerHeight}, divider=$dividerPosition")
    }

    private fun updateCoverImage() {
        android.util.Log.d("MainView", "updateCoverImage() called")
        val currentMusic = MediaPresenter.getCurrent()
        android.util.Log.d("MainView", "currentMusic: ${currentMusic?.title}")
        if (currentMusic != null) {
            // 使用 MediaUtil.getMusicImage 获取不带倒影的原图
            val coverBitmap = com.example.podclassic.util.MediaUtil.getMusicImage(currentMusic)
            android.util.Log.d("MainView", "coverBitmap: ${coverBitmap != null}, size: ${coverBitmap?.width}x${coverBitmap?.height}")
            if (coverBitmap != null) {
                coverImageView.setImageBitmap(coverBitmap)
                android.util.Log.d("MainView", "Bitmap set to coverImageView")
            } else {
                // 随机选一个图片当封面
                android.util.Log.d("MainView", "No cover bitmap, using random cover")
                setRandomCover()
            }
        } else {
            // 随机选一个图片当封面
            android.util.Log.d("MainView", "No current music, using random cover")
            setRandomCover()
        }
    }

    private fun resetCoverPosition() {
        // 使用父容器提供的尺寸（在updateLayoutForOrientation中已设置）
        val containerWidth = savedContainerWidth
        val containerHeight = savedContainerHeight
        
        // 获取图片实际尺寸
        val drawable = coverImageView.drawable
        if (drawable == null) {
            android.util.Log.d("MainView", "Drawable not ready yet, retrying...")
            coverContainer.post { resetCoverPosition() }
            return
        }
        
        val bitmapWidth = drawable.intrinsicWidth
        val bitmapHeight = drawable.intrinsicHeight
        
        if (containerWidth == 0 || containerHeight == 0 || bitmapWidth == 0 || bitmapHeight == 0) {
            android.util.Log.d("MainView", "Size not initialized yet, retrying...")
            coverContainer.post { resetCoverPosition() }
            return
        }
        
        // 计算缩放比例：确保图片填满整个容器（不露出黑色背景）
        // 同时保证至少一个边超出容器，以便有移动空间展示全貌
        val scaleX = containerWidth.toFloat() / bitmapWidth
        val scaleY = containerHeight.toFloat() / bitmapHeight
        
        // 基础缩放：取较大值确保填满容器
        val baseScale = maxOf(scaleX, scaleY)
        
        // 计算基础图片尺寸
        var imageWidth = (bitmapWidth * baseScale).toInt()
        var imageHeight = (bitmapHeight * baseScale).toInt()
        
        // 计算可移动范围
        var movableWidth = imageWidth - containerWidth
        var movableHeight = imageHeight - containerHeight
        
        // 确保至少有一个方向有足够的移动范围来展示图片全貌
        // 目标是让图片至少有一个边的可移动范围 >= 容器对应边的15%
        val targetMovableWidth = (containerWidth * 0.15f).toInt()
        val targetMovableHeight = (containerHeight * 0.15f).toInt()
        
        if (movableWidth < targetMovableWidth && movableHeight < targetMovableHeight) {
            // 两个方向都不够，需要额外放大
            // 根据容器比例决定优先放大哪个方向
            if (containerWidth >= containerHeight) {
                // 横向容器：优先确保横向有足够移动范围
                val targetWidth = containerWidth + targetMovableWidth
                val additionalScale = targetWidth.toFloat() / imageWidth
                imageWidth = targetWidth
                imageHeight = (imageHeight * additionalScale).toInt()
            } else {
                // 纵向容器：优先确保纵向有足够移动范围
                val targetHeight = containerHeight + targetMovableHeight
                val additionalScale = targetHeight.toFloat() / imageHeight
                imageHeight = targetHeight
                imageWidth = (imageWidth * additionalScale).toInt()
            }
        }
        
        // 保存最终尺寸
        savedImageWidth = imageWidth
        savedImageHeight = imageHeight
        
        // 更新图片视图尺寸
        coverImageView.layoutParams = FrameLayout.LayoutParams(savedImageWidth, savedImageHeight)
        
        // 重新计算可移动范围
        movableWidth = savedImageWidth - containerWidth
        movableHeight = savedImageHeight - containerHeight
        
        android.util.Log.d("MainView", "resetCoverPosition() - container: ${containerWidth}x${containerHeight}, bitmap: ${bitmapWidth}x${bitmapHeight}, image: ${savedImageWidth}x${savedImageHeight}, movable: ${movableWidth}x${movableHeight}")
        
        // 根据可移动范围决定扫描策略
        // 决定主扫描方向：如果横向可移动范围更大，则以横向扫描为主
        isHorizontalScan = movableWidth >= movableHeight
        
        // 根据可移动范围动态调整网格数量（至少3x3，最多6x6）
        scanGridCols = kotlin.math.max(3, kotlin.math.min(6, (movableWidth / 150).toInt() + 1))
        scanGridRows = kotlin.math.max(3, kotlin.math.min(6, (movableHeight / 150).toInt() + 1))
        
        // 如果某个方向无法移动，该方向网格设为1
        if (movableWidth <= 0) scanGridCols = 1
        if (movableHeight <= 0) scanGridRows = 1
        
        // 重置扫描状态
        currentGridX = 0
        currentGridY = 0
        scanDirectionX = 1
        scanDirectionY = 1
        visitedGrids.clear()
        
        android.util.Log.d("MainView", "Scan grid: ${scanGridCols}x${scanGridRows}, horizontalScan: $isHorizontalScan, movable: ${movableWidth}x${movableHeight}")
        
        // 设置初始位置到第一个网格点
        val initialPos = calculateGridPosition(currentGridX, currentGridY, movableWidth, movableHeight)
        coverImageView.x = initialPos.first
        coverImageView.y = initialPos.second
        android.util.Log.d("MainView", "Initial position: (${coverImageView.x}, ${coverImageView.y})")
    }
    
    /**
     * 计算指定网格位置对应的图片坐标
     */
    private fun calculateGridPosition(gridX: Int, gridY: Int, movableWidth: Int, movableHeight: Int): Pair<Float, Float> {
        // 使用最新的容器和图片尺寸重新计算可移动范围
        val containerWidth = if (coverContainer.width > 0) coverContainer.width else savedContainerWidth
        val containerHeight = if (coverContainer.height > 0) coverContainer.height else savedContainerHeight
        val imageWidth = coverImageView.width
        val imageHeight = coverImageView.height
        
        // 计算实际可移动范围（确保图片不会露出黑色背景）
        // 图片位置范围：x ∈ [containerWidth - imageWidth, 0], y ∈ [containerHeight - imageHeight, 0]
        val actualMinX = if (imageWidth > containerWidth) (containerWidth - imageWidth).toFloat() else 0f
        val actualMaxX = 0f
        val actualMinY = if (imageHeight > containerHeight) (containerHeight - imageHeight).toFloat() else 0f
        val actualMaxY = 0f
        
        // 计算网格步长
        val actualMovableWidth = (actualMaxX - actualMinX).toInt()
        val actualMovableHeight = (actualMaxY - actualMinY).toInt()
        val stepX = if (scanGridCols > 1) actualMovableWidth.toFloat() / (scanGridCols - 1) else 0f
        val stepY = if (scanGridRows > 1) actualMovableHeight.toFloat() / (scanGridRows - 1) else 0f
        
        // 计算基础位置
        val baseX = actualMaxX - gridX * stepX
        val baseY = actualMaxY - gridY * stepY
        
        // 添加随机扰动（±15%的步长，减小扰动避免越界）
        val randomOffsetX = if (stepX > 0) (random.nextFloat() - 0.5f) * stepX * 0.3f else 0f
        val randomOffsetY = if (stepY > 0) (random.nextFloat() - 0.5f) * stepY * 0.3f else 0f
        
        // 严格限制在边界内，确保不露出黑色背景
        val finalX = kotlin.math.max(actualMinX, kotlin.math.min(actualMaxX, baseX + randomOffsetX))
        val finalY = kotlin.math.max(actualMinY, kotlin.math.min(actualMaxY, baseY + randomOffsetY))
        
        return Pair(finalX, finalY)
    }

    private fun setRandomCover() {
        // 从assets目录中随机选择图片作为封面
        try {
            val randomImage = coverImages[random.nextInt(coverImages.size)]
            val inputStream = context.assets.open(randomImage)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            if (bitmap != null) {
                coverImageView.setImageBitmap(bitmap)
                // 图片加载完成后重置位置
                resetCoverPosition()
            } else {
                // 如果解码失败，使用默认图片
                coverImageView.setImageResource(android.R.drawable.ic_media_play)
                // 重置位置
                resetCoverPosition()
            }
            inputStream.close()
        } catch (e: Exception) {
            // 如果发生异常，使用默认图片
            coverImageView.setImageResource(android.R.drawable.ic_media_play)
            // 重置位置
            resetCoverPosition()
        }
    }

    private fun startCoverAnimation() {
        if (isAnimating) return
        isAnimating = true
        animateToNextPosition()
    }

    private fun animateToNextPosition() {
        // 使用实际测量的容器尺寸
        val containerWidth = if (coverContainer.width > 0) coverContainer.width else savedContainerWidth
        val containerHeight = if (coverContainer.height > 0) coverContainer.height else savedContainerHeight
        val imageWidth = savedImageWidth
        val imageHeight = savedImageHeight

        if (containerWidth == 0 || containerHeight == 0 || imageWidth == 0 || imageHeight == 0) {
            isAnimating = false
            return
        }

        val movableWidth = imageWidth - containerWidth
        val movableHeight = imageHeight - containerHeight

        // 蛇形扫描算法：计算下一个网格点
        val nextPos = calculateNextGridPosition(movableWidth, movableHeight)
        val targetX = nextPos.first
        val targetY = nextPos.second

        // 计算动画持续时间（根据距离动态调整）
        val currentX = coverImageView.x
        val currentY = coverImageView.y
        val distance = kotlin.math.sqrt((targetX - currentX) * (targetX - currentX) + (targetY - currentY) * (targetY - currentY))
        val duration = kotlin.math.max(3000L, (distance * 6).toLong()) // 至少3秒，速度减半

        // 创建X轴动画
        xAnimator = ObjectAnimator.ofFloat(coverImageView, "x", currentX, targetX).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (isAnimating) {
                        animateToNextPosition()
                    }
                }
            })
            start()
        }

        // 创建Y轴动画
        yAnimator = ObjectAnimator.ofFloat(coverImageView, "y", currentY, targetY).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopCoverAnimation() {
        isAnimating = false
        xAnimator?.cancel()
        yAnimator?.cancel()
        xAnimator = null
        yAnimator = null
    }
    
    /**
     * 蛇形扫描算法：计算下一个网格位置
     * 采用S形扫描路径，确保覆盖图片的每个部分
     */
    private fun calculateNextGridPosition(movableWidth: Int, movableHeight: Int): Pair<Float, Float> {
        // 记录当前网格为已访问
        visitedGrids.add(Pair(currentGridX, currentGridY))
        
        // 计算下一个网格位置（蛇形扫描）
        if (isHorizontalScan) {
            // 横向为主扫描：每行从左到右或从右到左，然后换行
            currentGridX += scanDirectionX
            
            // 检查是否需要换行
            if (currentGridX >= scanGridCols || currentGridX < 0) {
                scanDirectionX = -scanDirectionX // 反转X方向
                currentGridX = kotlin.math.max(0, kotlin.math.min(scanGridCols - 1, currentGridX))
                currentGridY += scanDirectionY // 移动到下一行
                
                // 检查是否需要反转Y方向（到达底部）
                if (currentGridY >= scanGridRows || currentGridY < 0) {
                    scanDirectionY = -scanDirectionY
                    currentGridY = kotlin.math.max(0, kotlin.math.min(scanGridRows - 1, currentGridY))
                    
                    // 如果所有网格都已访问，随机选择一个未访问的网格或重新开始
                    if (visitedGrids.size >= scanGridCols * scanGridRows) {
                        visitedGrids.clear()
                        // 随机选择新起点
                        currentGridX = random.nextInt(scanGridCols)
                        currentGridY = random.nextInt(scanGridRows)
                    }
                }
            }
        } else {
            // 纵向为主扫描：每列从上到下或从下到上，然后换列
            currentGridY += scanDirectionY
            
            // 检查是否需要换列
            if (currentGridY >= scanGridRows || currentGridY < 0) {
                scanDirectionY = -scanDirectionY // 反转Y方向
                currentGridY = kotlin.math.max(0, kotlin.math.min(scanGridRows - 1, currentGridY))
                currentGridX += scanDirectionX // 移动到下一列
                
                // 检查是否需要反转X方向（到达边缘）
                if (currentGridX >= scanGridCols || currentGridX < 0) {
                    scanDirectionX = -scanDirectionX
                    currentGridX = kotlin.math.max(0, kotlin.math.min(scanGridCols - 1, currentGridX))
                    
                    // 如果所有网格都已访问，随机选择一个新起点
                    if (visitedGrids.size >= scanGridCols * scanGridRows) {
                        visitedGrids.clear()
                        currentGridX = random.nextInt(scanGridCols)
                        currentGridY = random.nextInt(scanGridRows)
                    }
                }
            }
        }
        
        // 计算目标位置
        val pos = calculateGridPosition(currentGridX, currentGridY, movableWidth, movableHeight)
        android.util.Log.d("MainView", "Next grid: ($currentGridX, $currentGridY), pos: (${pos.first}, ${pos.second}), visited: ${visitedGrids.size}/${scanGridCols * scanGridRows}")
        
        return pos
    }

    private fun changeCoverImage() {
        // 检查是否正在播放音乐
        val currentMusic = MediaPresenter.getCurrent()
        if (currentMusic != null) {
            val coverBitmap = currentMusic.image
            if (coverBitmap != null) {
                coverImageView.setImageBitmap(coverBitmap)
                return
            }
        }
        // 没有正在播放或没有封面，随机选一张
        setRandomCover()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopCoverAnimation()
    }
}
