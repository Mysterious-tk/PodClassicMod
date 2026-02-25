package com.example.podclassic.view

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
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
import com.example.podclassic.widget.ListView
import java.io.File
import java.util.*


class MainView(context: Context) : RelativeLayout(context), ScreenView {

    private val listView = ListView(context)
    // 图片容器（可见区域）
    private val coverContainer = FrameLayout(context)
    // 图片视图（比容器大，在容器内飘动）
    private val coverImageView = ImageView(context)

    // 保存容器和图片的尺寸
    private var savedContainerWidth = 0
    private var savedContainerHeight = 0
    private var savedImageWidth = 0
    private var savedImageHeight = 0

    private val random = Random()
    private var dx = 0f
    private var dy = 0f
    private var bounceCount = 0
    private val coverImages = arrayOf("img/1.jpg", "img/2.jpg", "img/3.jpg", "img/4.jpg", "img/5.jpg", "img/6.jpg")
    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.what == 0) {
                updateCoverPosition()
                sendEmptyMessageDelayed(0, 100) // 减少延迟，使移动更快
            }
        }
    }

    init {
        // 初始化随机移动方向和速度
        dx = (random.nextInt(6) - 3).toFloat() * 3f // 调整移动速度
        dy = (random.nextInt(6) - 3).toFloat() * 3f // 调整移动速度
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

    val item = ListView.Item(Strings.NOW_PLAYING, object : ListView.OnItemClickListener {
        override fun onItemClick(index: Int, listView: ListView): Boolean {
            if (SPManager.getInt(SPManager.Theme.SP_NAME) == SPManager.Theme.IPOD_3RD.id) {
                Core.addView(MusicPlayerView3rd(context))
            } else {
                Core.addView(MusicPlayerView(context))
            }
            return true
        }

        override fun onItemLongClick(index: Int, listView: ListView): Boolean {
            return false
        }
    }, true)

    init {
        // 检查是否是横屏模式
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        // 设置图片容器（可见区域）
        // 从 activity_main.xml 可以看到：
        // - FrameLayout 高度：300dp
        // - padding_0 = 6dp（上下各 6dp，共 12dp）
        // - 实际 Screen 高度：300 - 12 = 288dp
        // - TitleBar 占 1/10，ScreenLayout 占 9/10
        // - 所以容器高度 = 288dp * 0.9 = 259.2dp
        val density = resources.displayMetrics.density
        val screenLayoutHeight = ((300 - 12) * density * 0.9).toInt() // 259.2dp 转换为像素
        savedContainerWidth = if (isLandscape) {
            (resources.displayMetrics.widthPixels * 0.55).toInt() // 横屏时占55%
        } else {
            (resources.displayMetrics.widthPixels * 0.70).toInt() // 竖屏时占70%
        }
        savedContainerHeight = screenLayoutHeight
        android.util.Log.d("MainView", "Container size: ${savedContainerWidth}x${savedContainerHeight}, screenLayoutHeight: $screenLayoutHeight")
        val containerParams = LayoutParams(savedContainerWidth, savedContainerHeight)
        containerParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
        // 设置容器的z轴顺序，使其位于ListView下方
        containerParams.addRule(RelativeLayout.BELOW, -1)
        addView(coverContainer, containerParams)
        coverContainer.setBackgroundColor(android.graphics.Color.BLACK)

        // 设置图片视图大小，确保对角线长度大于容器对角线，完全覆盖容器
        // 使用FIT_CENTER保持原始比例，让图片填满视图
        val containerDiagonal = kotlin.math.sqrt((savedContainerWidth * savedContainerWidth + savedContainerHeight * savedContainerHeight).toDouble())
        val imageSize = (containerDiagonal * 1.1).toInt() // 图片对角线比容器对角线大10%
        savedImageWidth = imageSize
        savedImageHeight = imageSize
        android.util.Log.d("MainView", "Container: ${savedContainerWidth}x${savedContainerHeight}, diagonal: $containerDiagonal, Image: ${savedImageWidth}x${savedImageHeight}")
        val imageParams = FrameLayout.LayoutParams(savedImageWidth, savedImageHeight)
        // 不设置gravity，让图片左上角与容器左上角对齐
        coverImageView.layoutParams = imageParams
        coverImageView.scaleType = ImageView.ScaleType.FIT_CENTER
        coverContainer.addView(coverImageView)
        android.util.Log.d("MainView", "CoverImageView added to container")

        // 设置ListView
        val listParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        listParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
        listParams.width = if (isLandscape) {
            (resources.displayMetrics.widthPixels * 0.45).toInt() // 横屏时占45%
        } else {
            (resources.displayMetrics.widthPixels * 0.30).toInt() // 竖屏时占30%
        }
        // 设置ListView的z轴顺序，使其位于ImageView上方
        listParams.addRule(RelativeLayout.ABOVE, -1)
        addView(listView, listParams)
        // 设置ListView的背景为浅灰色，防止专辑图透出影响文字阅读
        listView.setBackgroundColor(Colors.background)

        // 添加中间竖线分割，增加边界感和阴影效果
        val dividerView = View(context)
        val dividerParams = LayoutParams(10, LayoutParams.MATCH_PARENT)
        dividerParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
        dividerParams.leftMargin = if (isLandscape) {
            (resources.displayMetrics.widthPixels * 0.45).toInt() - 5 // 横屏时分割线位置在45%
        } else {
            (resources.displayMetrics.widthPixels * 0.30).toInt() - 5 // 竖屏时分割线位置在30%
        }
        dividerView.setBackgroundColor(android.graphics.Color.parseColor("#333333"))
        // 添加阴影效果
        dividerView.elevation = 5f
        addView(dividerView, dividerParams)

        // 初始化菜单项
        val menuItems = arrayListOf(
            ListView.Item(Strings.SONG, object : ListView.OnItemClickListener {
                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(MusicView(context))
                    return true
                }

                override fun onItemLongClick(index: Int, listView: ListView): Boolean {
                    return false
                }
            }, true),
            ListView.Item(Strings.PHOTO, object : ListView.OnItemClickListener {
                val list by lazy { MediaStoreUtil.getPhotoList() }
                private fun getItemList(): ArrayList<ListView.Item> {
                    val itemList = ArrayList<ListView.Item>()
                    for (file in list) {
                        itemList.add(ListView.Item(file.name, null, false))
                    }
                    return itemList
                }

                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(
                        ItemListView(
                            context,
                            getItemList(),
                            Strings.PHOTO,
                            object : ListView.OnItemClickListener {
                                override fun onItemClick(index: Int, listView: ListView): Boolean {
                                    Core.addView(ImageView(context, list, index))
                                    return true
                                }

                                override fun onItemLongClick(index: Int, listView: ListView): Boolean {
                                    return false
                                }
                            })
                    )
                    return true
                }

                override fun onItemLongClick(index: Int, listView: ListView): Boolean {
                    return false
                }
            }, true),
            ListView.Item(Strings.VIDEO, object : ListView.OnItemClickListener {
                val list by lazy { MediaStoreUtil.getVideoList() }

                private fun getItemList(): ArrayList<ListView.Item> {
                    val itemList = ArrayList<ListView.Item>()
                    for (file in list) {
                        itemList.add(ListView.Item(file.name, null, false))
                    }
                    return itemList
                }

                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(
                        ItemListView(
                            context,
                            getItemList(),
                            Strings.VIDEO,
                            object : ListView.OnItemClickListener {
                                override fun onItemClick(index: Int, listView: ListView): Boolean {
                                    val file = list[index]
                                    return if (file.exists()) {
                                        Core.addView(VideoView(context, list[index]))
                                        true
                                    } else {
                                        false
                                    }
                                }

                                override fun onItemLongClick(index: Int, listView: ListView): Boolean {
                                    return false
                                }
                            })
                    )
                    return true
                }

                override fun onItemLongClick(index: Int, listView: ListView): Boolean {
                    return false
                }
            }, true),

            ListView.Item(Strings.FILE, object : ListView.OnItemClickListener {
                private fun getItemList(): ArrayList<ListView.Item>? {
                    val sdCardPath = FileUtil.getSDCardPath() ?: return null
                    val itemList = ArrayList<ListView.Item>()
                    itemList.add(ListView.Item(Strings.INTERNAL_STORAGE, object : ListView.OnItemClickListener {
                        override fun onItemClick(index: Int, listView: ListView): Boolean {
                            Core.addView(
                                FileView(
                                    context,
                                    Environment.getRootDirectory()
                                )
                            )
                            return true
                        }

                        override fun onItemLongClick(index: Int, listView: ListView): Boolean {
                            return false
                        }
                    }, true))

                    itemList.add(ListView.Item(Strings.SDCARD, object : ListView.OnItemClickListener {
                        override fun onItemClick(index: Int, listView: ListView): Boolean {
                            Core.addView(FileView(context, File(sdCardPath)))
                            return true
                        }

                        override fun onItemLongClick(index: Int, listView: ListView): Boolean {
                            return false
                        }
                    }, true))
                    return itemList
                }

                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    val itemList = getItemList()
                    if (itemList == null) {
                        Core.addView(FileView(context, Environment.getExternalStorageDirectory()))
                    } else {
                        Core.addView(ItemListView(context, itemList, Strings.FILE, null))
                    }
                    return true
                }

                override fun onItemLongClick(index: Int, listView: ListView): Boolean {
                    return false
                }
            }, true),

            ListView.Item(Strings.EXTRA_APPLICATION, object : ListView.OnItemClickListener {
                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(AppsView(context))
                    return true
                }

                override fun onItemLongClick(index: Int, listView: ListView): Boolean {
                    return false
                }
            }, true),

            ListView.Item(Strings.SETTINGS, object : ListView.OnItemClickListener {
                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(
                        SettingsView(
                            context
                        )
                    )
                    return true
                }

                override fun onItemLongClick(index: Int, listView: ListView): Boolean {
                    return false
                }

            }, true),
            ListView.Item(Strings.SHUFFLE_PLAY, object : ListView.OnItemClickListener {
                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    MediaPresenter.shufflePlay()
                    // 根据主题选择使用哪个播放器视图
                    if (SPManager.getInt(SPManager.Theme.SP_NAME) == SPManager.Theme.IPOD_3RD.id) {
                        Core.addView(MusicPlayerView3rd(context))
                    } else {
                        Core.addView(MusicPlayerView(context))
                    }
                    return true
                }

                override fun onItemLongClick(index: Int, listView: ListView): Boolean {
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

        // 延迟执行，确保coverContainer和coverImageView尺寸已测量完成
        coverContainer.post {
            android.util.Log.d("MainView", "First post executed")
            coverContainer.post {
                android.util.Log.d("MainView", "Second post executed, starting updateCoverImage")
                updateCoverImage()
                // 强制重置位置到左上角
                resetCoverPosition()
                handler.sendEmptyMessage(0)
                android.util.Log.d("MainView", "Handler message sent")
            }
        }
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
                // 图片加载完成后重置位置
                resetCoverPosition()
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
        // 使用实际测量的视图尺寸
        val containerWidth = if (coverContainer.width > 0) coverContainer.width else savedContainerWidth
        val containerHeight = if (coverContainer.height > 0) coverContainer.height else savedContainerHeight
        val imageWidth = if (coverImageView.width > 0) coverImageView.width else savedImageWidth
        val imageHeight = if (coverImageView.height > 0) coverImageView.height else savedImageHeight
        android.util.Log.d("MainView", "resetCoverPosition() - container: ${containerWidth}x${containerHeight}, image: ${imageWidth}x${imageHeight}")

        if (containerWidth == 0 || containerHeight == 0 || imageWidth == 0 || imageHeight == 0) {
            // 尺寸未初始化，延迟再试
            android.util.Log.d("MainView", "Size not initialized yet, retrying...")
            coverContainer.post { resetCoverPosition() }
            return
        }

        // AABB算法：计算移动范围的中心点
        // x范围: [containerWidth - imageWidth, 0]
        // y范围: [containerHeight - imageHeight, 0]
        val minX = (containerWidth - imageWidth).toFloat()
        val maxX = 0f
        val minY = (containerHeight - imageHeight).toFloat()
        val maxY = 0f
        
        // 设置初始位置为范围中心
        coverImageView.x = (minX + maxX) / 2
        coverImageView.y = (minY + maxY) / 2
        android.util.Log.d("MainView", "Position reset to center: (${coverImageView.x}, ${coverImageView.y}), drawable: ${coverImageView.drawable != null}")

        // 重置速度
        dx = (random.nextInt(6) - 3).toFloat() * 3f // 调整初始速度
        dy = (random.nextInt(6) - 3).toFloat() * 3f // 调整初始速度
        android.util.Log.d("MainView", "Initial velocity: dx=$dx, dy=$dy")
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

    private fun updateCoverPosition() {
        // 使用保存的尺寸
        val containerWidth = savedContainerWidth
        val containerHeight = savedContainerHeight
        val imageWidth = savedImageWidth
        val imageHeight = savedImageHeight

        if (containerWidth == 0 || containerHeight == 0 || imageWidth == 0 || imageHeight == 0) {
            return
        }

        // AABB碰撞盒算法：确保容器完全在图片内
        // 约束条件：
        // - 图片左边缘 <= 容器左边缘  (x <= 0)
        // - 图片右边缘 >= 容器右边缘  (x + imageWidth >= containerWidth)
        // - 图片上边缘 <= 容器上边缘  (y <= 0)
        // - 图片下边缘 >= 容器下边缘  (y + imageHeight >= containerHeight)
        // 解得移动范围：
        val minX = (containerWidth - imageWidth).toFloat()  // 最右位置（图片右边缘对齐容器右边缘）
        val maxX = 0f  // 最左位置（图片左边缘对齐容器左边缘）
        val minY = (containerHeight - imageHeight).toFloat()  // 最下位置（图片下边缘对齐容器下边缘）
        val maxY = 0f  // 最上位置（图片上边缘对齐容器上边缘）

        // 获取图片的当前位置
        val currentX = coverImageView.x + dx
        val currentY = coverImageView.y + dy

        // 每50帧打印一次位置信息
        if (random.nextInt(50) == 0) {
            android.util.Log.d("MainView", "Position: ($currentX, $currentY), bounds: minX=$minX, maxX=$maxX, minY=$minY, maxY=$maxY")
        }

        var bounced = false

        // 边界检查和随机方向改变
        // 检查是否碰到左边界
        if (currentX <= minX) {
            // 碰到左边界，向右随机方向移动
            dx = (random.nextInt(3) + 2).toFloat() * 0.8f // 2-4的速度向右
            dy = (random.nextInt(7) - 3).toFloat() * 0.8f // -3到3的随机y速度
            bounced = true
            android.util.Log.d("MainView", "Bounced left, new velocity: dx=$dx, dy=$dy")
        }
        // 检查是否碰到右边界
        if (currentX >= maxX) {
            // 碰到右边界，向左随机方向移动
            dx = -(random.nextInt(3) + 2).toFloat() * 0.8f // 2-4的速度向左
            dy = (random.nextInt(7) - 3).toFloat() * 0.8f // -3到3的随机y速度
            bounced = true
            android.util.Log.d("MainView", "Bounced right, new velocity: dx=$dx, dy=$dy")
        }
        // 检查是否碰到上边界
        if (currentY <= minY) {
            // 碰到上边界，向下随机方向移动
            dx = (random.nextInt(7) - 3).toFloat() * 0.8f // -3到3的随机x速度
            dy = (random.nextInt(3) + 2).toFloat() * 0.8f // 2-4的速度向下
            bounced = true
            android.util.Log.d("MainView", "Bounced top, new velocity: dx=$dx, dy=$dy")
        }
        // 检查是否碰到下边界
        if (currentY >= maxY) {
            // 碰到下边界，向上随机方向移动
            dx = (random.nextInt(7) - 3).toFloat() * 0.8f // -3到3的随机x速度
            dy = -(random.nextInt(3) + 2).toFloat() * 0.8f // 2-4的速度向上
            bounced = true
        }

        // 如果发生碰撞，增加计数
        if (bounced) {
            bounceCount++
            // 碰撞50次后换图
            if (bounceCount >= 50) {
                bounceCount = 0
                changeCoverImage()
            }
        }

        // 限制位置在安全范围内
        var newX = currentX
        var newY = currentY

        // 确保不会超过边界
        if (newX < minX) newX = minX
        if (newX > maxX) newX = maxX
        if (newY < minY) newY = minY
        if (newY > maxY) newY = maxY

        // 更新图片的位置
        coverImageView.x = newX
        coverImageView.y = newY
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
        handler.removeCallbacksAndMessages(null)
    }
}