package com.example.podclassic.view

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.podclassic.`object`.Core
import com.example.podclassic.`object`.MediaPlayer
import com.example.podclassic.`object`.Music
import com.example.podclassic.activity.MainActivity
import com.example.podclassic.base.ScreenView
import com.example.podclassic.storage.SaveMusicLists
import com.example.podclassic.storage.SaveMusics
import com.example.podclassic.util.FileUtil
import java.io.File


class FileView(context: Context, val file: File) : ListView(context), ScreenView {

    var hasAudio = false
    val list: ArrayList<File> = file.let{
        val arrayList = ArrayList<File>()
        val array = file.listFiles()
        if (array != null) {
            for (file in file.listFiles()) {
                if (!file.name.startsWith(".")) {
                    arrayList.add(file)
                }
            }
        }
        arrayList.sortBy { com.example.podclassic.util.PinyinUtil.getPinyin(it.name) }
        arrayList
    }

    private val musicList = ArrayList<File>()

    init {
        if (list.isEmpty()) {
            itemList.add(Item("无内容", null, false))
        } else {
            for (file in list) {
                if (FileUtil.isAudio(file)) {
                    hasAudio = true
                    musicList.add(file)
                }
                itemList.add(Item(file.name, null, file.isDirectory))
            }

            if (hasAudio) {
                itemList.add(0, Item("随机播放歌曲", null, true))
            }
        }
        sorted = true
    }

    override fun enter(): Boolean {
        if (list.isEmpty()) {
            return false
        }
        if (hasAudio && index == 0) {
            val musics = ArrayList<Music>()
            for (file in musicList) {
                musics.add(Music(file))
            }
            MediaPlayer.shufflePlay(musics)
            Core.addView(MusicPlayerView(context))
            return true
        }
        val file = list[if (hasAudio) index - 1 else index]
        if (!file.exists()) {
            return false
        }
        if (file.isDirectory) {
            Core.addView(FileView(context, file))
        } else {
            when (FileUtil.getFileType(file)) {
                FileUtil.TYPE_VIDEO -> Core.addView(VideoView(context, file))
                FileUtil.TYPE_IMAGE -> Core.addView(ImageView(context, arrayListOf(file), 0))
                FileUtil.TYPE_AUDIO -> {
                    MediaPlayer.add(Music(file))
                    Core.addView(MusicPlayerView(context))
                }
            }
        }
        return true
    }

    override fun enterLongClick(): Boolean {
        if (list.isEmpty()) {
            return false
        }
        val id = if (hasAudio) index - 1 else index
        if (list[id].isDirectory) {
            val folder = list[id].listFiles()
            var hasAudio = false
            for (file in folder) {
                if (FileUtil.isAudio(file)) {
                    hasAudio = true
                    break
                }
            }
            if (hasAudio) {
                shake()
                val path = list[id].path
                if (SaveMusicLists.saveFolders.contains(path)) {
                    SaveMusicLists.saveFolders.remove(path)
                } else {
                    SaveMusicLists.saveFolders.add(path)
                }
                return true
            }
        } else if (FileUtil.isAudio(list[id])) {
            SaveMusics.loveList.add(Music(list[id]))
            shake()
            return true
        }
        return false
    }

    override fun slide(slideVal: Int): Boolean {
        return onSlide(slideVal)
    }

    override fun getTitle(): String {
        return file.name
    }

    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_NORMAL
    }
}