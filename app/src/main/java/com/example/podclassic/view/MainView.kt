package com.example.podclassic.view

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Environment
import com.example.podclassic.`object`.Core
import com.example.podclassic.`object`.MediaPlayer
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.base.ScreenView
import com.example.podclassic.game.Brick
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.FileUtil
import com.example.podclassic.util.MediaStoreUtil
import com.example.podclassic.util.Values
import com.example.podclassic.widget.ListView
import java.io.File


class MainView(context: Context) : ListView(context), ScreenView {

    companion object {
        const val TITLE = Values.POD
    }

    override fun getTitle(): String { return TITLE }

    val item = Item("正在播放", object : OnItemClickListener {
        override fun onItemClick(index: Int, listView: ListView): Boolean {
            Core.addView(MusicPlayerView(context))
            return true
        }
    }, true)

    init {
        itemList = arrayListOf(
            Item("音乐", object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(MusicView(context))
                    return true
                }

            }, true),
            Item("照片", object : OnItemClickListener {
                val list by lazy { MediaStoreUtil.photoList }
                private fun getItemList(): ArrayList<Item> {
                    val itemList = ArrayList<Item>()
                    for (file in list) {
                        itemList.add(Item(file.name, null, false))
                    }
                    return itemList
                }

                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(
                        ItemListView(context, getItemList(), "照片", object : OnItemClickListener {
                            override fun onItemClick(index: Int, listView: ListView): Boolean {
                                Core.addView(ImageView(context, list, index))
                                return true
                            }
                        })
                    )
                    return true
                }

            }, true),
            Item("视频", object : OnItemClickListener {
                val list by lazy { MediaStoreUtil.videoList }

                private fun getItemList(): ArrayList<Item> {
                    val itemList = ArrayList<Item>()
                    for (file in list) {
                        itemList.add(Item(file.name, null, false))
                    }
                    return itemList
                }

                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(
                        ItemListView(context, getItemList(), "视频", object : OnItemClickListener {
                            override fun onItemClick(index: Int, listView: ListView): Boolean {
                                val file = list[index]
                                return if (file.exists()) {
                                    Core.addView(VideoView(context, list[index]))
                                    true
                                } else {
                                    false
                                }
                            }
                        })
                    )
                    return true
                }

            }, true),

            Item("文件", object : OnItemClickListener {
                private fun getItemList(): ArrayList<Item>? {
                    val sdCardPath = FileUtil.getSDCardPath() ?: return null
                    val itemList = ArrayList<Item>()
                    itemList.add(Item("内部存储", object : OnItemClickListener {
                        override fun onItemClick(index: Int, listView: ListView): Boolean {
                            Core.addView(FileView(
                                    context,
                                    Environment.getRootDirectory()
                                )
                            )
                            return true
                        }
                    }, true))

                    itemList.add(Item("SD卡", object : OnItemClickListener {
                        override fun onItemClick(index: Int, listView: ListView): Boolean {
                            Core.addView(FileView(context, File(sdCardPath)))
                            return true
                        }
                    }, true))
                    return itemList
                }

                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    val itemList = getItemList()
                    if (itemList == null) {

                        Core.addView(FileView(context, Environment.getExternalStorageDirectory()))
                    } else {
                        Core.addView(ItemListView(context, itemList, "文件", null))
                    }
                    return true
                }

            }, true),

            Item("附加程序", object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(AppsView(context))
                    return true
                }
            }, true),

            Item("设置", object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(
                        SettingsView(
                            context
                        )
                    ); return true
                }

            }, true),
            Item("随机播放歌曲", object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    if (MediaPlayer.shufflePlay()) {
                        Core.addView(MusicPlayerView(context))
                        return true
                    }
                    return false
                }
            }, false)
        )
    }

    override fun enter() : Boolean { return onItemClick() }

    override fun enterLongClick() : Boolean { return false }

    override fun slide(slideVal: Int): Boolean { return onSlide(slideVal) }

    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_SINGLE
    }

    override fun onStart() {
        if (MediaPlayer.getCurrent() == null) {
            remove(item)
        } else {
            addIfNotExist(item)
        }
    }

    override fun onStop() {
    }
}