package com.example.podclassic.view


import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.podclassic.`object`.Core
import com.example.podclassic.`object`.MediaPlayer
import com.example.podclassic.`object`.Music
import com.example.podclassic.`object`.MusicList
import com.example.podclassic.base.ScreenView
import com.example.podclassic.storage.SPManager
import com.example.podclassic.storage.SaveMusics
import com.example.podclassic.widget.ListView
import java.lang.RuntimeException

@SuppressLint("ViewConstructor")
class MusicListView : ListView, ScreenView, MediaPlayer.OnMediaChangeListener {
    private var musicList : ArrayList<Music>? = null
    private var name : String? = null
    private var onLongClick : Int? = null
    constructor(context: Context, musicList: ArrayList<Music>, name : String) : super(context) {
        this.musicList = musicList
        this.name = name
        this.onLongClick = LONG_CLICK_SET_LOVE
        init()
        sorted = true
    }

    constructor(context: Context, musicList : MusicList, onLongClick: Int) : super(context) {
        this.musicList = musicList.list
        this.onLongClick = onLongClick
        this.name = musicList.name
        init()
        sorted = musicList.type != MusicList.TYPE_ALBUM
    }
    constructor(context: Context, onLongClick : Int) : super(context) {
        val musicList = when (onLongClick) {
            LONG_CLICK_REMOVE_LOVE -> SaveMusics.loveList.getMusicList()
            LONG_CLICK_REMOVE_CURRENT -> MusicList("正在播放", MediaPlayer.getPlayList())
            else -> throw IllegalArgumentException("illegal value for onLongClick")
        }

        this.musicList = musicList.list
        this.name = musicList.name
        this.onLongClick = onLongClick
        sorted = false
        init()
    }

    companion object {
        const val LONG_CLICK_SET_LOVE = 0
        const val LONG_CLICK_REMOVE_LOVE = 1
        const val LONG_CLICK_REMOVE_CURRENT = 2
    }

    override fun getTitle(): String { return name!! }

    private fun init() {
        val showInfo = SPManager.getBoolean(SPManager.SP_SHOW_INFO)
        if (showInfo) {
            for (music in musicList!!) {
                itemList.add(Item("${music.name} - ${music.singer} - ${music.album}", null, false))
            }
        } else {
            for (music in musicList!!) {
                itemList.add(Item(music.name, null, false))
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        onStop()
    }

    private fun onStart() {
        MediaPlayer.addOnMediaChangeListener(this)
        onMediaChange()
    }

    private fun onStop() {
        MediaPlayer.removeOnMediaChangeListener(this)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            onStart()
        } else {
            onStop()
        }
    }

    override fun onItemCreated(index : Int, itemView : ItemView) {
        if (musicList!!.isEmpty()) {
            return
        }
        itemView.setPlaying(musicList!![index] == MediaPlayer.getCurrent())
    }

    override fun enter() : Boolean {
        return if (index in 0 until musicList!!.size) {
            MediaPlayer.setPlayList(musicList!!, index)
            Core.addView(MusicPlayerView(context))
            true
        } else {
            false
        }
    }

    override fun enterLongClick() : Boolean {
        if (index !in 0 until musicList!!.size) {
            return false
        }
        when (onLongClick) {
            LONG_CLICK_SET_LOVE -> {
                SaveMusics.loveList.add(musicList!![index])
                shake()
            }
            LONG_CLICK_REMOVE_LOVE -> {
                SaveMusics.loveList.removeAt(index)
                removeCurrentItem()
            }
            LONG_CLICK_REMOVE_CURRENT -> {
                MediaPlayer.remove(index)
                removeCurrentItem()
            }
        }
        return true
    }

    override fun slide(slideVal: Int): Boolean {
        return onSlide(slideVal)
    }

    override fun onMediaChange() {
        refreshList()
    }

    override fun onMediaChangeFinished() {}

    override fun onPlayStateChange() {}

    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_NORMAL
    }
}