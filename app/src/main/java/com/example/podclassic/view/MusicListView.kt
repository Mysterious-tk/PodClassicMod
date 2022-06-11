package com.example.podclassic.view


import android.annotation.SuppressLint
import android.content.Context
import com.example.podclassic.base.Core
import com.example.podclassic.base.Observer
import com.example.podclassic.base.ScreenView
import com.example.podclassic.bean.Music
import com.example.podclassic.bean.MusicList
import com.example.podclassic.media.PlayMode
import com.example.podclassic.service.MediaPresenter
import com.example.podclassic.storage.MusicTable
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.LiveData
import com.example.podclassic.util.ThreadUtil
import com.example.podclassic.widget.ListView

@SuppressLint("ViewConstructor")
class MusicListView : ListView, ScreenView {
    private val observer = Observer()
    override fun getObserver(): Observer {
        return observer
    }

    override fun getTitle(): String {
        return name
    }

    private val musicList: ArrayList<Music>
    private val name: String
    private val type: Int

    constructor(context: Context, musicList: ArrayList<Music>, name: String, type: Int) : super(
        context
    ) {
        this.musicList = musicList
        this.name = name
        this.type = type
        init()
        sorted = type == TYPE_NORMAL
    }

    constructor(context: Context, musicList: MusicList) : super(context) {
        this.musicList = musicList.getMusicList()
        this.name = musicList.title
        this.type = TYPE_NORMAL
        init()
        sorted = musicList.type != MusicList.TYPE_ALBUM
    }

    companion object {
        const val TYPE_NORMAL = 0
        const val TYPE_FAVOURITE = 1
        const val TYPE_RECENT = 2
        const val TYPE_CURRENT = 3

    }


    private fun init() {
        itemList.ensureCapacity(musicList.size)
        if (SPManager.getBoolean(
                SPManager.SP_SHOW_INFO
            )
        ) {
            for (music in musicList) {
                itemList.add(Item("${music.title} - ${music.artist} - ${music.album}", null, false))
            }
        } else {
            for (music in musicList) {
                itemList.add(Item(music.title, null, false))
            }
        }
        val index = musicList.indexOf(MediaPresenter.getCurrent())
        if (index != -1) {
            setCurrent(index)
        }

        observer.addLiveData(MediaPresenter.music, object : LiveData.OnDataChangeListener {
            override fun onDataChange() {
                ThreadUtil.runOnUiThread { refreshList() }
            }
        })
    }

    override fun onItemCreated(index: Int, itemView: ItemView) {
        if (musicList.isEmpty()) {
            return
        }
        itemView.setPlaying(musicList[index] == MediaPresenter.getCurrent())
    }

    override fun enter(): Boolean {
        return if (index in 0 until musicList.size) {
            MediaPresenter.setPlayMode(PlayMode.ORDER)
            MediaPresenter.setPlaylist(musicList, index)
            Core.addView(MusicPlayerView(context))
            true
        } else {
            false
        }
    }

    override fun enterLongClick(): Boolean {
        if (index !in 0 until musicList.size) {
            return false
        }
        when (type) {
            TYPE_FAVOURITE -> {
                MusicTable.favourite.removeAt(index)
                removeCurrentItem()
            }
            TYPE_CURRENT -> {
                MediaPresenter.removeAt(index)
                removeCurrentItem()
            }
            else -> {
                MusicTable.favourite.add(musicList[index])
                shake()
            }
        }
        return true
    }

    override fun slide(slideVal: Int): Boolean {
        return onSlide(slideVal)
    }
}