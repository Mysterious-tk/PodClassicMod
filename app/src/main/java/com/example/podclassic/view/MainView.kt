package com.example.podclassic.view

import android.content.Context
import android.os.Environment
import com.example.podclassic.base.Core
import com.example.podclassic.base.ScreenView
import com.example.podclassic.service.MediaPresenter
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.FileUtil
import com.example.podclassic.util.MediaStoreUtil
import com.example.podclassic.values.Strings
import com.example.podclassic.widget.ListView
import java.io.File


class MainView(context: Context) : ListView(context), ScreenView {

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

    val item = Item(Strings.NOW_PLAYING, object : OnItemClickListener {
        override fun onItemClick(index: Int, listView: ListView): Boolean {
            Core.addView(MusicPlayerView(context))
            return true
        }
    }, true)

    init {
        itemList = arrayListOf(
            Item(Strings.SONG, object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(MusicView(context))
                    return true
                }

            }, true),
            Item(Strings.PHOTO, object : OnItemClickListener {
                val list by lazy { MediaStoreUtil.getPhotoList() }
                private fun getItemList(): ArrayList<Item> {
                    val itemList = ArrayList<Item>()
                    for (file in list) {
                        itemList.add(Item(file.name, null, false))
                    }
                    return itemList
                }

                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(
                        ItemListView(
                            context,
                            getItemList(),
                            Strings.PHOTO,
                            object : OnItemClickListener {
                                override fun onItemClick(index: Int, listView: ListView): Boolean {
                                    Core.addView(ImageView(context, list, index))
                                    return true
                                }
                            })
                    )
                    return true
                }

            }, true),
            Item(Strings.VIDEO, object : OnItemClickListener {
                val list by lazy { MediaStoreUtil.getVideoList() }

                private fun getItemList(): ArrayList<Item> {
                    val itemList = ArrayList<Item>()
                    for (file in list) {
                        itemList.add(Item(file.name, null, false))
                    }
                    return itemList
                }

                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(
                        ItemListView(
                            context,
                            getItemList(),
                            Strings.VIDEO,
                            object : OnItemClickListener {
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

            Item(Strings.FILE, object : OnItemClickListener {
                private fun getItemList(): ArrayList<Item>? {
                    val sdCardPath = FileUtil.getSDCardPath() ?: return null
                    val itemList = ArrayList<Item>()
                    itemList.add(Item(Strings.INTERNAL_STORAGE, object : OnItemClickListener {
                        override fun onItemClick(index: Int, listView: ListView): Boolean {
                            Core.addView(
                                FileView(
                                    context,
                                    Environment.getRootDirectory()
                                )
                            )
                            return true
                        }
                    }, true))

                    itemList.add(Item(Strings.SDCARD, object : OnItemClickListener {
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
                        Core.addView(ItemListView(context, itemList, Strings.FILE, null))
                    }
                    return true
                }

            }, true),

            Item(Strings.EXTRA_APPLICATION, object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(AppsView(context))
                    return true
                }
            }, true),

            Item(Strings.SETTINGS, object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(
                        SettingsView(
                            context
                        )
                    ); return true
                }

            }, true),
            Item(Strings.SHUFFLE_PLAY, object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    MediaPresenter.shufflePlay()
                    Core.addView(MusicPlayerView(context))
                    return true
                }
            }, false)
        )
    }

    override fun enter(): Boolean {
        return onItemClick()
    }

    override fun enterLongClick(): Boolean {
        return false
    }

    override fun slide(slideVal: Int): Boolean {
        return onSlide(slideVal)
    }

    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_SINGLE
    }

    override fun onViewAdd() {
        if (MediaPresenter.getCurrent() == null) {
            remove(item)
        } else {
            addIfNotExist(item)
        }
    }
}