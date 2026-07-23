package com.example.podclassic.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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
import com.example.podclassic.base.Observer
import com.example.podclassic.base.ScreenView
import com.example.podclassic.bean.Music
import com.example.podclassic.service.MediaPresenter
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.LiveData
import com.example.podclassic.util.ArtworkBackgroundController
import com.example.podclassic.util.ThreadUtil
import com.example.podclassic.util.FileUtil
import com.example.podclassic.util.MediaStoreUtil
import com.example.podclassic.values.Colors
import com.example.podclassic.values.Strings
import com.example.podclassic.view.MusicPlayerView3rd
import com.example.podclassic.widget.RecyclerListView
import java.io.File
import java.util.*
import kotlin.math.ceil
import kotlin.math.hypot


class MainView(context: Context) : RelativeLayout(context), ScreenView {

    private val listView = RecyclerListView(context)
    // 图片容器（可见区域）
    private val coverContainer = FrameLayout(context)
    // 专辑封面动画图片视图 - 在容器内缓慢飘动展示
    private val coverImageView = ImageView(context).apply {
        contentDescription = "Animated Album Cover"
    }
    private val glassOverlayView = MainArtworkGlassView(context)
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
    private var coverAnimator: ValueAnimator? = null
    private var isAnimating = false
    private var nextDriftPoint = 0
    private val menuBackgroundController = ArtworkBackgroundController(
        listView,
        Colors.background,
        artworkStrength = 0.28f
    )
    private var artworkLoadGeneration = 0

    // iPod photo-style path. Every leg changes both axes, avoiding a horizontal-only pan.
    private val driftPath = arrayOf(
        0.08f to 0.14f,
        0.92f to 0.84f,
        0.14f to 0.92f,
        0.86f to 0.08f,
        0.06f to 0.68f,
        0.94f to 0.32f
    )

    // Orientation change handling - prevent rapid consecutive orientation changes
    private var orientationChangeHandler: Runnable? = null
    private val orientationChangeLock = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastKnownParentWidth = 0
    private var lastKnownOrientation = Configuration.ORIENTATION_PORTRAIT
    private val handler = Handler(Looper.getMainLooper())

    /** 获取当前布局中的容器高度；仅在首次布局前使用临时估算值。 */
    private fun getActualContainerHeight(): Int {
        // Only laid-out view bounds describe the space that will actually be drawn.
        if (coverContainer.height > 0) {
            android.util.Log.d("MainView", "getActualContainerHeight: using actual ${coverContainer.height}")
            return coverContainer.height
        }

        // The parent is a safe pre-layout fallback; never cache it across window changes.
        val parent = parent as? android.view.ViewGroup
        if (parent != null && parent.height > 0) {
            android.util.Log.d("MainView", "getActualContainerHeight: using parent ${parent.height}")
            return parent.height
        }

        // Last-resort estimate used only before the first layout pass.
        val screenHeight = resources.displayMetrics.heightPixels
        val estimatedHeight = (screenHeight * 0.9).toInt()
        android.util.Log.d("MainView", "getActualContainerHeight: using estimated $estimatedHeight")
        return estimatedHeight
    }

    /** 获取当前布局中的容器宽度；首次布局前按根视图宽度估算。 */
    private fun getActualContainerWidth(): Int {
        val rootWidth = width.takeIf { it > 0 }
            ?: (parent as? android.view.ViewGroup)?.width?.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val dividerPosition = if (isLandscape) (rootWidth * 0.45f).toInt() else (rootWidth * 0.30f).toInt()
        val calculatedWidth = rootWidth - dividerPosition

        // An already laid-out container is authoritative. Comparing it with displayMetrics
        // reintroduced stale full-screen/inset values during resume and rotation.
        if (coverContainer.width > 0) {
            android.util.Log.d("MainView", "getActualContainerWidth: using actual ${coverContainer.width}")
            return coverContainer.width
        }

        // 优先级2: 使用计算值
        android.util.Log.d("MainView", "getActualContainerWidth: using calculated $calculatedWidth")
        return calculatedWidth
    }

    /**
     * 获取状态栏高度
     */
    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

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
            if (SPManager.Theme.usesThirdGenerationLayout(
                    SPManager.getInt(SPManager.Theme.SP_NAME)
                )
            ) {
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

    // Observer for real-time music state monitoring
    private val observer = Observer()

    init {
        // 检查是否是横屏模式
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // 使用屏幕宽度，避免依赖可能不稳定的 parent
        val screenWidth = resources.displayMetrics.widthPixels

        // 计算分割线位置（ListView宽度）
        val dividerPosition = if (isLandscape) {
            (screenWidth * 0.45).toInt() // 横屏时占45%
        } else {
            (screenWidth * 0.30).toInt() // 竖屏时占30%
        }

        // 设置图片容器（可见区域）- 从分割线右侧开始到屏幕右边
        savedContainerWidth = screenWidth - dividerPosition
        // 使用智能高度估算（考虑标题栏和系统UI）
        savedContainerHeight = getActualContainerHeight()

        // 保存初始状态
        lastKnownParentWidth = screenWidth
        lastKnownOrientation = if (isLandscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT

        val containerParams = LayoutParams(savedContainerWidth, LayoutParams.MATCH_PARENT)
        containerParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
        containerParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
        addView(coverContainer, containerParams)
        coverContainer.setBackgroundColor(android.graphics.Color.BLACK)
        coverContainer.addOnLayoutChangeListener { _, left, top, right, bottom,
                                                    oldLeft, oldTop, oldRight, oldBottom ->
            val newWidth = right - left
            val newHeight = bottom - top
            val oldWidth = oldRight - oldLeft
            val oldHeight = oldBottom - oldTop
            if (newWidth > 0 && newHeight > 0 &&
                (newWidth != oldWidth || newHeight != oldHeight)
            ) {
                savedContainerWidth = newWidth
                savedContainerHeight = newHeight
                // The listener runs after this container has its final bounds but before draw.
                // Resize the child now so rotation cannot expose one black frame at an edge.
                if (coverImageView.drawable != null) {
                    resetCoverPosition()
                }
            }
        }

        // 设置图片视图的初始布局参数，实际尺寸将在图片加载后根据比例计算
        val imageParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        coverImageView.layoutParams = imageParams
        // CENTER_CROP is a final rendering-level guarantee that the drawable can never be
        // stretched, even if a layout update and an image update land in the same frame.
        coverImageView.scaleType = ImageView.ScaleType.CENTER_CROP
        coverContainer.addView(coverImageView)
        coverContainer.addView(
            glassOverlayView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
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

                // A shallow refractive seam separates the menu glass from the artwork.
                val rightShadow = LinearGradient(
                    0f, 0f,
                    width, 0f,
                    intArrayOf(
                        Color.argb(118, 0, 0, 0),
                        Color.argb(54, 0, 0, 0),
                        Color.argb(12, 0, 0, 0),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.24f, 0.62f, 1f),
                    Shader.TileMode.CLAMP
                )
                shadowPaint.shader = rightShadow
                canvas.drawRect(0f, 0f, width, height, shadowPaint)

                linePaint.color = Color.argb(138, 255, 255, 255)
                linePaint.strokeWidth = resources.displayMetrics.density
                canvas.drawLine(1f, 0f, 1f, height, linePaint)
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
                    if (SPManager.Theme.usesThirdGenerationLayout(
                            SPManager.getInt(SPManager.Theme.SP_NAME)
                        )
                    ) {
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

        // 监听音乐状态变化，动态添加/移除"正在播放"项并更新封面
        observer.addLiveData(MediaPresenter.music, object : LiveData.OnDataChangeListener {
            override fun onStart() {
                updateNowPlayingItem()
                updateCoverImage()
            }
            override fun onDataChange() {
                ThreadUtil.runOnUiThread {
                    updateNowPlayingItem()
                    updateCoverImage()
                }
            }
        })
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0 || (w == oldw && h == oldh) || !::dividerView.isInitialized) {
            return
        }

        // Configuration is updated before a real-device window necessarily receives its final
        // bounds. This callback is the authoritative second pass for rotation, multi-window,
        // system bar changes, and foreground restoration.
        orientationChangeHandler?.let(handler::removeCallbacks)
        orientationChangeHandler = Runnable {
            orientationChangeLock.set(false)
            updateLayoutForOrientation()
        }
        postOnAnimation(orientationChangeHandler!!)
    }

    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_SINGLE
    }

    override fun getObserver(): Observer? {
        return observer
    }

    override fun onConfigurationChanged() {
        android.util.Log.d("MainView", "onConfigurationChanged() called")

        // 移除之前的待处理切换
        orientationChangeHandler?.let {
            handler?.removeCallbacks(it)
        }

        // 检查方向是否真的改变了
        val newOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Configuration.ORIENTATION_LANDSCAPE
        } else {
            Configuration.ORIENTATION_PORTRAIT
        }

        if (newOrientation == lastKnownOrientation) {
            android.util.Log.d("MainView", "Orientation unchanged, skipping update")
            return
        }

        // 如果已经在处理切换，等待后重试
        if (orientationChangeLock.get()) {
            android.util.Log.d("MainView", "Orientation change already in progress, will retry")
            orientationChangeHandler = Runnable {
                updateLayoutForOrientation()
            }
            handler?.postDelayed(orientationChangeHandler!!, 300)
            return
        }

        // 防抖：延迟执行以避免快速连续切换
        orientationChangeHandler = Runnable {
            updateLayoutForOrientation()
        }
        handler?.postDelayed(orientationChangeHandler!!, 200)
    }

    override fun onViewAdd() {
        android.util.Log.d("MainView", "onViewAdd() called")

        if (MediaPresenter.getCurrent() == null) {
            listView.remove(item)
            android.util.Log.d("MainView", "No current music, removed 'Now Playing' item")
        } else {
            // 添加到末尾（不指定 index）
            listView.addIfNotExist(item)
            android.util.Log.d("MainView", "Added 'Now Playing' item at end of list")
        }

        // 强制刷新 ListView，确保新增/移除的 item 立即可见
        // forceRefresh 使用 RecyclerView.post 确保在正确的时机刷新
        listView.forceRefresh()

        // 检查父容器尺寸是否已测量
        val parentWidth = (parent as? android.view.ViewGroup)?.width ?: 0
        val parentHeight = (parent as? android.view.ViewGroup)?.height ?: 0

        if (parentWidth > 0 && parentHeight > 0) {
            // 尺寸已测量，直接初始化
            updateLayoutForOrientation()
            updateCoverImage()
            // resetCoverPosition() 会在布局稳定后启动漂移动画
        } else {
            // 延迟一帧等待测量
            post {
                updateLayoutForOrientation()
                updateCoverImage()
                // resetCoverPosition() 会在布局稳定后启动漂移动画
            }
        }

        // 强制重新测量容器高度（修复从MusicPlayerView返回时的失真）
        if (coverContainer.height == 0 || kotlin.math.abs(coverContainer.height - getActualContainerHeight()) > 100) {
            android.util.Log.d("MainView", "Container height mismatch, forcing remeasure...")
            waitForLayout {
                val actualHeight = getActualContainerHeight()
                android.util.Log.d("MainView", "After layout: container=${coverContainer.width}x${coverContainer.height}, estimated=$actualHeight")
                resetCoverPosition()
            }
        }
    }

    /**
     * 根据当前音乐状态更新"正在播放"项
     * 有音乐时添加到列表末尾，无音乐时从列表移除
     * 注意：此方法不滚动列表，只在末尾添加/移除项
     */
    private fun updateNowPlayingItem() {
        if (MediaPresenter.getCurrent() == null) {
            listView.remove(item)
            android.util.Log.d("MainView", "Music stopped, removed 'Now Playing' item")
        } else {
            listView.addIfNotExist(item)
            android.util.Log.d("MainView", "Music started, added 'Now Playing' item at end")
        }
    }

    /**
     * 根据当前屏幕方向更新布局参数
     */
    private fun updateLayoutForOrientation() {
        // 设置锁，防止并发执行
        if (!orientationChangeLock.compareAndSet(false, true)) {
            android.util.Log.w("MainView", "Orientation update already in progress, skipping")
            return
        }

        try {
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val rootWidth = width.takeIf { it > 0 }
                ?: (parent as? android.view.ViewGroup)?.width?.takeIf { it > 0 }
                ?: resources.displayMetrics.widthPixels

            // 计算分割线位置
            val dividerPosition = if (isLandscape) (rootWidth * 0.45f).toInt() else (rootWidth * 0.30f).toInt()
            val containerWidth = rootWidth - dividerPosition

            // 更新保存的值（保持兼容性）
            savedContainerWidth = containerWidth
            lastKnownParentWidth = rootWidth
            lastKnownOrientation = if (isLandscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT

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

            android.util.Log.d("MainView", "Layout updated: landscape=$isLandscape, container=${savedContainerWidth}x${savedContainerHeight}, divider=$dividerPosition")

            // 强制重新布局
            listView.requestLayout()
            coverContainer.requestLayout()
            dividerView.requestLayout()
            requestLayout()

            // Wait for the final post-rotation bounds before sizing or moving the artwork.
            waitForLayout {
                resetCoverPosition()
            }
        } finally {
            handler?.postDelayed({
                orientationChangeLock.set(false)
            }, 500)
        }
    }

    /**
     * 等待布局完成后执行回调
     * The callback is posted to the next frame so a non-zero old layout cannot be mistaken
     * for completion of the layoutParams change requested in the current frame.
     */
    private fun waitForLayout(callback: () -> Unit) {
        coverContainer.post {
            if (coverContainer.width > 0 && coverContainer.height > 0) {
                android.util.Log.d("MainView", "Layout completed: ${coverContainer.width}x${coverContainer.height}")
                callback()
            } else {
                coverContainer.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                    override fun onLayoutChange(
                        view: View,
                        left: Int,
                        top: Int,
                        right: Int,
                        bottom: Int,
                        oldLeft: Int,
                        oldTop: Int,
                        oldRight: Int,
                        oldBottom: Int
                    ) {
                        if (right > left && bottom > top) {
                            view.removeOnLayoutChangeListener(this)
                            callback()
                        }
                    }
                })
                coverContainer.requestLayout()
            }
        }
    }

    private fun updateCoverImage() {
        android.util.Log.d("MainView", "updateCoverImage() called")
        val generation = ++artworkLoadGeneration
        val currentMusic = MediaPresenter.getCurrent()
        android.util.Log.d("MainView", "currentMusic: ${currentMusic?.title}")
        if (currentMusic != null) {
            var coverBitmap: Bitmap? = null
            var backgroundScheme: ArtworkBackgroundController.Scheme? = null
            ThreadUtil.asyncTask({
                // Use the square source image: it is ideal for both display and palette sampling.
                coverBitmap = com.example.podclassic.util.MediaUtil.getMusicImage(currentMusic)
                backgroundScheme = coverBitmap?.let(menuBackgroundController::extract)
            }, {
                if (generation != artworkLoadGeneration) return@asyncTask
                menuBackgroundController.apply(backgroundScheme)
                android.util.Log.d("MainView", "coverBitmap: ${coverBitmap != null}, size: ${coverBitmap?.width}x${coverBitmap?.height}")
                if (coverBitmap != null) {
                    coverImageView.setImageBitmap(coverBitmap)
                    waitForLayout { resetCoverPosition() }
                } else {
                    setRandomCover()
                }
            })
        } else {
            menuBackgroundController.apply(null)
            android.util.Log.d("MainView", "No current music, using random cover")
            setRandomCover()
        }
    }

    /**
     * Sizes the artwork with overscan on both axes, then starts a slow, constant-speed
     * diagonal drift. Using the container's laid-out bounds here is important: display
     * metrics can still describe the previous orientation during a window transition.
     */
    private fun resetCoverPosition() {
        stopCoverDrift()

        val containerWidth = coverContainer.width
        val containerHeight = coverContainer.height
        val drawable = coverImageView.drawable ?: return
        val sourceWidth = drawable.intrinsicWidth
        val sourceHeight = drawable.intrinsicHeight
        if (containerWidth <= 0 || containerHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return
        }

        // CENTER_CROP plus 14% overscan guarantees useful movement in X and Y. ceil() and
        // two extra pixels protect against sub-pixel rounding exposing the container edge.
        val cropScale = maxOf(
            containerWidth.toFloat() / sourceWidth,
            containerHeight.toFloat() / sourceHeight
        )
        val driftScale = cropScale * 1.14f
        savedImageWidth = ceil(sourceWidth * driftScale).toInt() + 2
        savedImageHeight = ceil(sourceHeight * driftScale).toInt() + 2

        coverImageView.layoutParams = FrameLayout.LayoutParams(savedImageWidth, savedImageHeight)
        // A container resize can otherwise draw once with the child's old measured size.
        coverImageView.measure(
            View.MeasureSpec.makeMeasureSpec(savedImageWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(savedImageHeight, View.MeasureSpec.EXACTLY)
        )
        coverImageView.layout(0, 0, savedImageWidth, savedImageHeight)

        val minX = (containerWidth - savedImageWidth).toFloat()
        val minY = (containerHeight - savedImageHeight).toFloat()
        coverImageView.translationX = minX / 2f
        coverImageView.translationY = minY / 2f

        nextDriftPoint = random.nextInt(driftPath.size)
        startCoverDrift()
    }

    private fun startCoverDrift() {
        if (!isShown || coverContainer.width <= 0 || coverContainer.height <= 0) return
        isAnimating = true
        animateToNextDriftPoint()
    }

    private fun animateToNextDriftPoint() {
        if (!isAnimating) return

        val minX = (coverContainer.width - savedImageWidth).toFloat()
        val minY = (coverContainer.height - savedImageHeight).toFloat()
        if (minX >= 0f || minY >= 0f) return

        val point = driftPath[nextDriftPoint]
        nextDriftPoint = (nextDriftPoint + 1) % driftPath.size
        val startX = coverImageView.translationX.coerceIn(minX, 0f)
        val startY = coverImageView.translationY.coerceIn(minY, 0f)
        val targetX = minX * point.first
        val targetY = minY * point.second
        val distance = hypot(targetX - startX, targetY - startY)

        // Duration comes only from distance, so every segment has the same visual speed.
        val pixelsPerSecond = 8f * resources.displayMetrics.density
        val durationMs = (distance / pixelsPerSecond * 1000f).toLong().coerceAtLeast(1L)

        coverAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                coverImageView.translationX = startX + (targetX - startX) * fraction
                coverImageView.translationY = startY + (targetY - startY) * fraction
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (isAnimating && animation === coverAnimator) animateToNextDriftPoint()
                }
            })
            start()
        }
    }

    private fun stopCoverDrift() {
        isAnimating = false
        coverAnimator?.removeAllListeners()
        coverAnimator?.cancel()
        coverAnimator = null
    }

    private fun setRandomCover() {
        // 从assets目录中随机选择图片作为封面
        try {
            val randomImage = coverImages[random.nextInt(coverImages.size)]
            val inputStream = context.assets.open(randomImage)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            if (bitmap != null) {
                coverImageView.setImageBitmap(bitmap)
                // 使用 waitForLayout 替代 postDelayed，确保布局完成后再重置位置
                waitForLayout {
                    resetCoverPosition()
                }
            } else {
                // 如果解码失败，使用默认图片
                coverImageView.setImageResource(android.R.drawable.ic_media_play)
                // 使用 waitForLayout 替代 postDelayed
                waitForLayout {
                    resetCoverPosition()
                }
            }
            inputStream.close()
        } catch (e: Exception) {
            // 如果发生异常，使用默认图片
            coverImageView.setImageResource(android.R.drawable.ic_media_play)
            // 使用 waitForLayout 替代 postDelayed
            waitForLayout {
                resetCoverPosition()
            }
        }
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
        artworkLoadGeneration++
        menuBackgroundController.cancel()
        stopCoverDrift()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) {
            // Insets and available window bounds may settle only after returning from the
            // background. Re-read the laid-out container instead of reusing saved dimensions.
            coverContainer.postOnAnimation {
                if (coverContainer.width > 0 && coverContainer.height > 0 &&
                    coverImageView.drawable != null
                ) {
                    resetCoverPosition()
                }
            }
        }
    }

    override fun onViewRemove() {
        artworkLoadGeneration++
        menuBackgroundController.cancel()
        // 清理方向变化处理器
        orientationChangeHandler?.let {
            handler?.removeCallbacks(it)
            orientationChangeHandler = null
        }
        stopCoverDrift()
    }
}

/** Static optical layers over the moving artwork; no per-frame bitmap blur is required. */
private class MainArtworkGlassView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var sideShade: Shader? = null
    private var bottomShade: Shader? = null
    private var diagonalSheen: Shader? = null

    init {
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        sideShade = LinearGradient(
            0f, 0f, w * 0.34f, 0f,
            intArrayOf(Color.argb(72, 4, 8, 16), Color.argb(18, 4, 8, 16), Color.TRANSPARENT),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        bottomShade = LinearGradient(
            0f, h * 0.58f, 0f, h.toFloat(),
            intArrayOf(Color.TRANSPARENT, Color.argb(18, 0, 0, 0), Color.argb(72, 0, 0, 0)),
            floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP
        )
        diagonalSheen = LinearGradient(
            -w * 0.08f, 0f, w * 0.82f, h * 0.72f,
            intArrayOf(Color.argb(48, 255, 255, 255), Color.argb(12, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 0.36f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        paint.shader = diagonalSheen
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = sideShade
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = bottomShade
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        edgePaint.color = Color.argb(82, 255, 255, 255)
        edgePaint.strokeWidth = resources.displayMetrics.density
        canvas.drawLine(0.5f, 0f, 0.5f, height.toFloat(), edgePaint)
    }
}
