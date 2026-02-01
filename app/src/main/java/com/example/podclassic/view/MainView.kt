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
import com.example.podclassic.values.Strings
import com.example.podclassic.widget.ListView
import java.io.File
import java.util.*


class MainView(context: Context) : RelativeLayout(context), ScreenView {

    private val listView = ListView(context).apply {
        // 主菜单不需要为右侧时间预留空间
        reserveSpaceForTime = false
    }
    // 图片容器（可见区域）
    private val coverContainer = FrameLayout(context)
    // 图片视图（比容器大，在容器内飘动）
    private val coverImageView = ImageView(context)

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
        dx = (random.nextInt(6) - 3).toFloat() * 5f // 增加移动速度
        dy = (random.nextInt(6) - 3).toFloat() * 5f // 增加移动速度和y轴移动
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
            Core.addView(MusicPlayerView(context))
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
        val containerWidth = if (isLandscape) {
            (resources.displayMetrics.widthPixels * 0.5).toInt() // 横屏时占50%
        } else {
            (resources.displayMetrics.widthPixels * 0.65).toInt() // 竖屏时占65%
        }
        val containerHeight = resources.displayMetrics.heightPixels
        val containerParams = LayoutParams(containerWidth, containerHeight)
        containerParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
        // 设置容器的z轴顺序，使其位于ListView下方
        containerParams.addRule(RelativeLayout.BELOW, -1)
        addView(coverContainer, containerParams)
        coverContainer.setBackgroundColor(android.graphics.Color.BLACK)

        // 设置图片视图（比容器大，在容器内飘动，能看到图片的不同部分）
        // 图片是容器的1.2倍，这样有足够空间飘动看到不同部分，同时不会太大
        val imageParams = FrameLayout.LayoutParams(
            (containerWidth * 1.2).toInt(),
            (containerHeight * 1.2).toInt()
        )
        coverImageView.layoutParams = imageParams
        coverImageView.scaleType = ImageView.ScaleType.CENTER_CROP
        coverContainer.addView(coverImageView)

        // 设置ListView
        val listParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        listParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
        listParams.width = if (isLandscape) {
            (resources.displayMetrics.widthPixels * 0.5).toInt() // 横屏时占50%
        } else {
            (resources.displayMetrics.widthPixels * 0.35).toInt() // 竖屏时占35%
        }
        // 设置ListView的z轴顺序，使其位于ImageView上方
        listParams.addRule(RelativeLayout.ABOVE, -1)
        addView(listView, listParams)
        // 设置ListView的背景为不透明，确保不显示唱片图片
        listView.setBackgroundColor(android.graphics.Color.WHITE)

        // 添加中间竖线分割，增加边界感和阴影效果
        val dividerView = View(context)
        val dividerParams = LayoutParams(10, LayoutParams.MATCH_PARENT)
        dividerParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
        dividerParams.leftMargin = if (isLandscape) {
            (resources.displayMetrics.widthPixels * 0.5).toInt() - 5 // 横屏时分割线位置在50%
        } else {
            (resources.displayMetrics.widthPixels * 0.35).toInt() - 5 // 竖屏时分割线位置在35%
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
                    Core.addView(MusicPlayerView(context))
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
        if (MediaPresenter.getCurrent() == null) {
            listView.remove(item)
        } else {
            listView.addIfNotExist(item)
        }

        // 延迟执行，确保coverContainer和coverImageView尺寸已测量完成
        coverContainer.post {
            coverContainer.post {
                updateCoverImage()
                // 强制重置位置到中心
                resetCoverPosition()
                handler.sendEmptyMessage(0)
            }
        }
    }

    private fun updateCoverImage() {
        val currentMusic = MediaPresenter.getCurrent()
        if (currentMusic != null) {
            val coverBitmap = currentMusic.image
            if (coverBitmap != null) {
                coverImageView.setImageBitmap(coverBitmap)
                // 图片加载完成后重置位置
                resetCoverPosition()
            } else {
                // 随机选一个图片当封面
                setRandomCover()
            }
        } else {
            // 随机选一个图片当封面
            setRandomCover()
        }
    }

    private fun resetCoverPosition() {
        // 重置位置到中心
        val containerWidth = coverContainer.width
        val containerHeight = coverContainer.height
        val imageWidth = coverImageView.width
        val imageHeight = coverImageView.height

        if (containerWidth == 0 || containerHeight == 0 || imageWidth == 0 || imageHeight == 0) {
            // view还未测量完成，延迟再试
            coverContainer.post { resetCoverPosition() }
            return
        }

        // 计算中心位置（让图片中心对准容器中心）
        // 图片比容器大，所以x/y应该是负值
        val centerX = (containerWidth - imageWidth) / 2f
        val centerY = (containerHeight - imageHeight) / 2f

        coverImageView.x = centerX
        coverImageView.y = centerY

        // 重置速度
        dx = (random.nextInt(6) - 3).toFloat()
        dy = (random.nextInt(6) - 3).toFloat()
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
        // 获取容器和图片的尺寸
        val containerWidth = coverContainer.width
        val containerHeight = coverContainer.height
        val imageWidth = coverImageView.width
        val imageHeight = coverImageView.height

        if (containerWidth == 0 || containerHeight == 0 || imageWidth == 0 || imageHeight == 0) {
            return
        }

        // 计算边界：图片在容器内移动
        // 当 x = 0 时，图片左边缘对齐容器左边缘
        // 当 x = containerWidth - imageWidth 时，图片右边缘对齐容器右边缘
        val minX = (containerWidth - imageWidth).toFloat() // 负值
        val maxX = 0f
        val minY = (containerHeight - imageHeight).toFloat() // 负值
        val maxY = 0f

        // 获取图片的当前位置
        val currentX = coverImageView.x + dx
        val currentY = coverImageView.y + dy

        var bounced = false

        // 边界检查和随机方向改变
        // 检查是否碰到左边界
        if (currentX <= minX) {
            // 碰到左边界，向右随机方向移动（确保x速度为正，行程长）
            dx = (random.nextInt(3) + 3).toFloat() * 2f // 3-5的速度向右
            dy = (random.nextInt(7) - 3).toFloat() * 2f // -3到3的随机y速度
            bounced = true
        }
        // 检查是否碰到右边界
        if (currentX >= maxX) {
            // 碰到右边界，向左随机方向移动（确保x速度为负，行程长）
            dx = -(random.nextInt(3) + 3).toFloat() * 2f // 3-5的速度向左
            dy = (random.nextInt(7) - 3).toFloat() * 2f // -3到3的随机y速度
            bounced = true
        }
        // 检查是否碰到上边界
        if (currentY <= minY) {
            // 碰到上边界，向下随机方向移动（确保y速度为正，行程长）
            dx = (random.nextInt(7) - 3).toFloat() * 2f // -3到3的随机x速度
            dy = (random.nextInt(3) + 3).toFloat() * 2f // 3-5的速度向下
            bounced = true
        }
        // 检查是否碰到下边界
        if (currentY >= maxY) {
            // 碰到下边界，向上随机方向移动（确保y速度为负，行程长）
            dx = (random.nextInt(7) - 3).toFloat() * 2f // -3到3的随机x速度
            dy = -(random.nextInt(3) + 3).toFloat() * 2f // 3-5的速度向上
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