package com.example.podclassic.view

import android.content.Context
import com.example.podclassic.`object`.Core
import com.example.podclassic.`object`.MediaPlayer
import com.example.podclassic.`object`.MusicList
import com.example.podclassic.base.ScreenView
import com.example.podclassic.storage.SPManager
import com.example.podclassic.storage.SaveMusicLists
import com.example.podclassic.util.MediaStoreUtil
import com.example.podclassic.widget.ListView

class MusicView(context: Context) : ListView(context), ScreenView {
    companion object { const val TITLE = "音乐" }

    override fun getTitle(): String { return TITLE }

    override fun enter() : Boolean { return onItemClick() }

    override fun enterLongClick() : Boolean { return false }

    override fun slide(slideVal: Int) : Boolean { return onSlide(slideVal) }

    private val currentList = Item("当前播放列表", object : OnItemClickListener {
        override fun onItemClick(index: Int, listView : ListView) : Boolean {
            Core.addView(MusicListView(context, MusicListView.LONG_CLICK_REMOVE_CURRENT))
            return true
        }
    }, true)

    private val coverFlow = Item("CoverFlow", object : OnItemClickListener {
        override fun onItemClick(index: Int, listView: ListView): Boolean {
            if (MediaStoreUtil.albums.isEmpty()) {
                return false
            }
            Core.addView(CoverFlowView(context))
            return true
        }
    }, true)

    init {
        itemList = arrayListOf(
            Item("歌曲", object : OnItemClickListener {
                val musicList = MediaStoreUtil.musics
                override fun onItemClick(index : Int, listView : ListView) : Boolean {
                    Core.addView(MusicListView(context, musicList, "歌曲"))
                    return true
                }
            }, true),

            Item("专辑", object : OnItemClickListener {
                override fun onItemClick(index : Int, listView : ListView) : Boolean {
                    Core.addView(AlbumListView(context, MediaStoreUtil.albums, "专辑"))
                    return true
                }
            }, true),

            Item("表演者", object : OnItemClickListener {
                override fun onItemClick(index : Int, listView : ListView) : Boolean {
                    val singerList = MediaStoreUtil.singers
                    val itemList1 = ArrayList<Item>(singerList.size)
                    for (musicList in singerList) {
                        itemList1.add(Item(musicList.name, null, true))
                    }
                    val itemListView = ItemListView(context, itemList1,  "表演者", object : OnItemClickListener {
                        override fun onItemClick(index : Int, listView : ListView) : Boolean {
                            val albumList = MediaStoreUtil.searchAlbum(singerList[index].name)
                            val musicList = MusicList()
                            musicList.name = singerList[index].name
                            musicList.type = MusicList.TYPE_SINGER
                            albumList.add(0, musicList)
                            Core.addView(AlbumListView(context, albumList, singerList[index].name))
                            return true
                        }

                        override fun onItemLongClick(index: Int, listView: ListView): Boolean {
                            SaveMusicLists.saveSingers.add(singerList[index].name)
                            listView.shake()
                            return true
                        }
                    })
                    itemListView.sorted = true
                    Core.addView(itemListView)
                    return true
                }

            }, true),

            Item("收藏的文件夹", object : OnItemClickListener {
                override fun onItemClick(index: Int, listView : ListView) : Boolean {
                    val itemList = ArrayList<Item>()
                    val files = SaveMusicLists.saveFolders.getFolders()
                    for (file in files) {
                        itemList.add(Item(file.name, null, true))
                    }
                    Core.addView(ItemListView(context, itemList, "文件夹", object : OnItemClickListener {
                        override fun onItemClick(index: Int, listView : ListView) : Boolean {
                            Core.addView(FileView(context, files[index]))
                            return true
                        }
                        override fun onItemLongClick(index: Int, listView: ListView): Boolean {
                            listView.removeCurrentItem()
                            SaveMusicLists.saveFolders.remove(files[index].path)
                            return true
                        }
                    }))
                    return true
                }
            }, true),


            Item("收藏的歌曲", object : OnItemClickListener {
                override fun onItemClick(index : Int, listView : ListView) : Boolean {
                    Core.addView(MusicListView(context, MusicListView.LONG_CLICK_REMOVE_LOVE))
                    return true
                }
            }, true),
            Item("收藏的专辑", object : OnItemClickListener {
                override fun onItemClick(index : Int, listView : ListView) : Boolean {
                    Core.addView(AlbumListView(context, SaveMusicLists.saveAlbums.getList(), MusicList.TYPE_ALBUM , AlbumListView.LONG_CLICK_REMOVE,"收藏的专辑"))
                    return true
                }
            }, true),
            Item("收藏的艺术家", object : OnItemClickListener {
                override fun onItemClick(index : Int, listView : ListView) : Boolean {
                    Core.addView(AlbumListView(context, SaveMusicLists.saveSingers.getList(), MusicList.TYPE_SINGER, AlbumListView.LONG_CLICK_REMOVE, "收藏的艺术家"))
                    return true
                }
            }, true)
            )
        if (SPManager.getBoolean(SPManager.SP_COVER_FLOW)) {
            addIfNotExist(coverFlow)
        }

        if (MediaPlayer.getCurrent() != null) {
            addIfNotExist(currentList, 0)
        }
    }

    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_NORMAL
    }

    override fun onStart() {
        if (MediaPlayer.getCurrent() == null) {
            remove(currentList)
        } else {
            addIfNotExist(currentList)
        }
    }

    override fun onStop() {

    }

}