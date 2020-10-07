package com.example.podclassic.view


import android.content.Context
import com.example.podclassic.`object`.Core
import com.example.podclassic.`object`.MediaPlayer
import com.example.podclassic.`object`.Music
import com.example.podclassic.`object`.MusicList
import com.example.podclassic.base.ScreenView
import com.example.podclassic.storage.SaveMusics

class MusicListView(context: Context, private val musicList: ArrayList<Music>, private val name : String) : ListView(context), ScreenView, MediaPlayer.OnMediaChangeListener {
    constructor(context: Context, musicList : MusicList) : this(context, musicList.list, musicList.name)
    constructor(context: Context, onLongClick : Int) : this(context, when (onLongClick) {
        LONG_CLICK_REMOVE_LOVE -> SaveMusics.loveList.getMusicList()
        LONG_CLICK_REMOVE_CURRENT -> MusicList("正在播放", MediaPlayer.getPlayList())
        else -> throw Exception()
    }) {
        this.onLongClick = onLongClick
        sorted = onLongClick == LONG_CLICK_SET_LOVE
    }

    companion object {
        private const val LONG_CLICK_SET_LOVE = 0
        const val LONG_CLICK_REMOVE_LOVE = 1
        const val LONG_CLICK_REMOVE_CURRENT = 2
    }

    override fun getTitle(): String { return name }

    private var onLongClick = LONG_CLICK_SET_LOVE

    init {
        for (music in musicList) {
            itemList.add(Item(music.name, null, false))
        }
        sorted = onLongClick == LONG_CLICK_SET_LOVE
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
        if (musicList.isEmpty()) {
            return
        }
        itemView.setPlaying(musicList[index] == MediaPlayer.getCurrent())
    }

    override fun enter() : Boolean {
        return if (index in 0 until musicList.size) {
            MediaPlayer.setPlayList(musicList, index)
            Core.addView(MusicPlayerView(context))
            true
        } else {
            false
        }
    }

    override fun enterLongClick() : Boolean {
        if (index !in 0 until musicList.size) {
            return false
        }
        when (onLongClick) {
            LONG_CLICK_SET_LOVE -> {
                SaveMusics.loveList.add(musicList[index])
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

    override fun onPlayStateChange() {}

    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_NORMAL
    }
}