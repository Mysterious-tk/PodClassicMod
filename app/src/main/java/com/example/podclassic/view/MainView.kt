package com.example.podclassic.view

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
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
    private val coverImageView = ImageView(context)

    private val random = Random()
    private var dx = 0f
    private var dy = 0f
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
        dx = (random.nextInt(6) - 3).toFloat() * 2.5f // 进一步增加移动速度
        dy = (random.nextInt(6) - 3).toFloat() * 2.5f // 进一步增加移动速度和y轴移动
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
        // 设置ImageView
        val coverParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        coverParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
        coverParams.width = (resources.displayMetrics.widthPixels * 0.6).toInt()
        // 设置ImageView的z轴顺序，使其位于ListView下方
        coverParams.addRule(RelativeLayout.BELOW, -1)
        addView(coverImageView, coverParams)
        coverImageView.scaleType = ImageView.ScaleType.CENTER_CROP
        // 添加背景，使其看起来更像唱片
        coverImageView.setBackgroundColor(android.graphics.Color.BLACK)

        // 设置ListView
        val listParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        listParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
        listParams.width = (resources.displayMetrics.widthPixels * 0.5).toInt() // 将ListView宽度设置为50%
        // 设置ListView的z轴顺序，使其位于ImageView上方
        listParams.addRule(RelativeLayout.ABOVE, -1)
        addView(listView, listParams)
        // 设置ListView的背景为不透明，确保不显示唱片图片
        listView.setBackgroundColor(android.graphics.Color.WHITE)

        // 添加中间竖线分割，增加边界感和阴影效果
        val dividerView = View(context)
        val dividerParams = LayoutParams(10, LayoutParams.MATCH_PARENT)
        dividerParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
        dividerParams.leftMargin = (resources.displayMetrics.widthPixels * 0.5).toInt() - 5 // 更新分割线位置到50%
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

        updateCoverImage()
        handler.sendEmptyMessage(0)
    }

    private fun updateCoverImage() {
        val currentMusic = MediaPresenter.getCurrent()
        if (currentMusic != null) {
            val coverBitmap = currentMusic.image
            if (coverBitmap != null) {
                coverImageView.setImageBitmap(coverBitmap)
            } else {
                // 随机选一个图片当封面
                setRandomCover()
            }
        } else {
            // 随机选一个图片当封面
            setRandomCover()
        }
    }

    private fun setRandomCover() {
        // 从项目的img目录中随机选择图片作为封面
        val imgFiles = File("l:\\Search\\PodClassicMod\\img").listFiles()
        if (imgFiles != null && imgFiles.isNotEmpty()) {
            val randomFile = imgFiles[random.nextInt(imgFiles.size)]
            val bitmap = android.graphics.BitmapFactory.decodeFile(randomFile.absolutePath)
            if (bitmap != null) {
                coverImageView.setImageBitmap(bitmap)
            } else {
                // 如果解码失败，使用默认图片
                coverImageView.setImageResource(android.R.drawable.ic_media_play)
            }
        } else {
            // 如果没有找到图片，使用默认图片
            coverImageView.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    private fun updateCoverPosition() {
        // 计算中线位置（ListView的宽度）
        val centerLine = (resources.displayMetrics.widthPixels * 0.5).toInt()
        // 计算隐藏边界位置（比中线靠左一些）
        val hiddenBoundary = centerLine - 50
        
        // 计算移动范围，比图片view大两倍
        val maxX = centerLine
        val minX = hiddenBoundary - coverImageView.width // 左边界扩展
        val maxY = coverImageView.height * 2 // 下边界扩展
        val minY = -coverImageView.height // 上边界扩展
        
        // 获取ImageView的当前位置
        val currentX = coverImageView.x + dx
        val currentY = coverImageView.y + dy
        
        // 碰撞检测
        // 检查是否撞到左边界
        if (currentX <= minX) {
            // 撞到左边界，往对角线方向反弹
            dx = -dx * 0.8f // 反弹并稍微减速
            dy = (random.nextInt(4) - 2).toFloat() * 0.5f // 随机调整Y方向速度
        }
        // 检查是否撞到右边界
        if (currentX >= maxX) {
            // 撞到右边界（中线），往对角线方向反弹
            dx = -dx * 0.8f // 反弹并稍微减速
            dy = (random.nextInt(4) - 2).toFloat() * 0.5f // 随机调整Y方向速度
        }
        // 检查是否撞到上边界
        if (currentY <= minY) {
            // 撞到上边界，反弹
            dy = -dy * 0.8f // 反弹并稍微减速
        }
        // 检查是否撞到下边界
        if (currentY >= maxY) {
            // 撞到下边界，反弹
            dy = -dy * 0.8f // 反弹并稍微减速
        }
        
        // 更新ImageView的位置
        coverImageView.x += dx
        coverImageView.y += dy
        
        // 确保ImageView不会超过中线
        if (coverImageView.x > maxX) {
            coverImageView.x = maxX.toFloat()
        }
        // 确保ImageView不会超过左边界
        if (coverImageView.x < minX) {
            coverImageView.x = minX.toFloat()
        }
        // 确保ImageView不会超过上边界
        if (coverImageView.y < minY) {
            coverImageView.y = minY.toFloat()
        }
        // 确保ImageView不会超过下边界
        if (coverImageView.y > maxY) {
            coverImageView.y = maxY.toFloat()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }
}