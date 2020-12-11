package com.example.podclassic.view

import android.content.Context
import com.example.podclassic.`object`.Core
import com.example.podclassic.`object`.MusicList
import com.example.podclassic.base.ScreenView
import com.example.podclassic.storage.SaveMusicLists
import com.example.podclassic.widget.ListView

class AlbumListView : ListView, ScreenView {
    companion object {
        const val LONG_CLICK_ADD = 0
        const val LONG_CLICK_REMOVE = 1
    }

    private var musicList : ArrayList<MusicList> = ArrayList()
    private var title : String? = null
    private var longClick  = LONG_CLICK_ADD
    constructor(context: Context, list : ArrayList<String>, type : Int, longClick : Int, title : String) : super(context) {
        this.title = title
        this.longClick = longClick
        for (string in list) {
            itemList.add(Item(string, null, true))
            val musicList = MusicList()
            musicList.type = type
            musicList.name = string
            this.musicList.add(musicList)
        }
        sorted = longClick == LONG_CLICK_ADD
    }

    constructor(context: Context, list : ArrayList<MusicList>, title : String) : super(context) {
        this.musicList = list
        this.title = title
        val hasAll = list.size >= 2 && list[0].type != list[1].type

        for (musicList in list) {
            itemList.add(Item(musicList.name, null, true))
        }
        if (hasAll) {
            itemList[0].name = "全部"
        }
        sorted = longClick == LONG_CLICK_ADD
    }

    override fun enter(): Boolean {
        return if (itemList.isEmpty()) {
            false
        } else {
            Core.addView(MusicListView(context, musicList[index], MusicListView.LONG_CLICK_SET_LOVE))
            true
        }
    }

    override fun enterLongClick(): Boolean {
        if (itemList.isEmpty()) {
            return false
        }
        val musicList = musicList[index]
        when (musicList.type) {
            MusicList.TYPE_ALBUM -> {
                if (longClick == LONG_CLICK_REMOVE) {
                    this.musicList.removeAt(index)
                    SaveMusicLists.saveAlbums.remove(musicList.name)
                    removeCurrentItem()
                } else {
                    SaveMusicLists.saveAlbums.add(musicList.name)
                    shake()
                }
            }
            MusicList.TYPE_SINGER -> {
                if (longClick == LONG_CLICK_REMOVE) {
                    this.musicList.removeAt(index)
                    SaveMusicLists.saveSingers.remove(musicList.name)
                    removeCurrentItem()
                } else {
                    SaveMusicLists.saveSingers.add(musicList.name)
                    shake()
                }
            }
        }
        return true
    }

    override fun slide(slideVal: Int): Boolean {
        return onSlide(slideVal)
    }

    override fun getTitle(): String {
        return title!!
    }

    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_NORMAL
    }

}