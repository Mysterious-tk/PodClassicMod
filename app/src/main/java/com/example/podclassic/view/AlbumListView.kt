package com.example.podclassic.view

import android.content.Context
import com.example.podclassic.base.Core
import com.example.podclassic.base.ScreenView
import com.example.podclassic.bean.MusicList
import com.example.podclassic.storage.MusicListTable
import com.example.podclassic.values.Strings
import com.example.podclassic.widget.ListView
import com.example.podclassic.widget.ListView.OnItemClickListener

class AlbumListView(
    context: Context,
    list: ArrayList<MusicList>,
    private val title: String,
    private var longClick: Int
) : ListView(context), ScreenView {
    companion object {
        const val LONG_CLICK_ADD = 0
        const val LONG_CLICK_REMOVE = 1
    }

    private var musicList: ArrayList<MusicList> = list
    private var hasAll = false

    init {
        hasAll = list.size >= 2 && list[0].type != list[1].type
        itemList.ensureCapacity(list.size)
        for (musicList in list) {
            itemList.add(Item(musicList.title, null, true))
        }
        if (hasAll) {
            itemList[0].name = Strings.ALL
        }
        sorted = longClick == LONG_CLICK_ADD
        
        // 设置默认点击监听器，处理触摸播放歌曲的功能
        defaultOnItemClickListener = object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: ListView): Boolean {
                return enter()
            }
        }
    }

    override fun enter(): Boolean {
        return if (itemList.isEmpty()) {
            false
        } else {
            Core.addView(MusicListView(context, musicList[index]))
            true
        }
    }

    override fun enterLongClick(): Boolean {
        if (itemList.isEmpty() || (hasAll && index == 0)) {
            return false
        }
        val musicList = musicList[index]
        when (musicList.type) {
            MusicList.TYPE_ALBUM -> {
                if (longClick == LONG_CLICK_REMOVE) {
                    this.musicList.removeAt(index)
                    MusicListTable.album.delete(musicList)
                    removeCurrentItem()
                } else {
                    MusicListTable.album.add(musicList)
                    shake()
                }
            }
            MusicList.TYPE_ARTIST -> {
                if (longClick == LONG_CLICK_REMOVE) {
                    this.musicList.removeAt(index)
                    MusicListTable.artist.delete(musicList)
                    removeCurrentItem()
                } else {
                    MusicListTable.artist.add(musicList)
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
        return title
    }
}