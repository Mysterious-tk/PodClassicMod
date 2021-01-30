package com.example.podclassic.view

import android.content.Context
import com.example.podclassic.`object`.Core
import com.example.podclassic.`object`.MediaPlayer
import com.example.podclassic.base.ScreenView
import com.example.podclassic.storage.SaveMusicLists
import com.example.podclassic.storage.SaveMusics
import com.example.podclassic.util.FileUtil
import com.example.podclassic.util.MediaUtil
import com.example.podclassic.widget.ListView
import java.io.File
import java.text.Collator
import java.util.*
import kotlin.collections.ArrayList


class FileView(context: Context, val file: File) : ListView(context), ScreenView {

    private var hasAudio = false
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
        val collator = Collator.getInstance(Locale.CHINA)
        arrayList.sortWith(Comparator { o1, o2 -> collator.compare(o1?.name, o2?.name) })
        arrayList
    }

    private val musicList = ArrayList<File>()

    init {
        if (list.isEmpty()) {
            itemList.add(Item("无内容", null, false))
        } else {
            for (file in list) {
                hasAudio = hasAudio || FileUtil.isAudio(file)
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
            val musics = MediaUtil.searchMusic("${MediaUtil.PATH} like ?", "${file.path}%")
            val lastIndex = file.path.length
            val iterator = musics.iterator()
            while (iterator.hasNext()) {
                val music = iterator.next()
                if (music.path.lastIndexOf('/') != lastIndex) {
                    iterator.remove()
                }

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
                    MediaPlayer.add(MediaUtil.getMusic(file.path))
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
                SaveMusicLists.saveFolders.add(list[id].path)
                return true
            }
        } else if (FileUtil.isAudio(list[id])) {
            SaveMusics.loveList.add(MediaUtil.getMusic(list[id].path))
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