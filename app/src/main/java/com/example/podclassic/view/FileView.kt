package com.example.podclassic.view

import android.annotation.SuppressLint
import android.content.Context
import android.provider.MediaStore
import com.example.podclassic.base.Core
import com.example.podclassic.base.ScreenView
import com.example.podclassic.bean.MusicList
import com.example.podclassic.service.MediaPresenter
import com.example.podclassic.storage.MusicListTable
import com.example.podclassic.storage.MusicTable
import com.example.podclassic.util.FileUtil
import com.example.podclassic.util.MediaStoreUtil
import com.example.podclassic.values.Strings
import com.example.podclassic.widget.ListView
import java.io.File
import java.text.Collator
import java.util.*


@SuppressLint("ViewConstructor")
class FileView(context: Context, val file: File) : ListView(context), ScreenView {

    private var hasAudio = false
    val list: ArrayList<File> = file.let {
        val array = file.listFiles()
        val arrayList = ArrayList<File>(array?.size ?: 0)
        if (array != null) {
            for (f in array) {
                if (!f.name.startsWith(".")) {
                    arrayList.add(f)
                }
            }
            val collator = Collator.getInstance(Locale.getDefault())
            arrayList.sortWith { o1, o2 -> collator.compare(o1?.name, o2?.name) }
        }
        arrayList
    }

    private val musicList = ArrayList<File>(list.size)

    init {
        if (list.isEmpty()) {
            itemList.add(Item(Strings.EMPTY, null, false))
        } else {
            itemList.ensureCapacity(list.size)
            for (file in list) {
                if (FileUtil.isAudio(file)) {
                    hasAudio = true
                    musicList.add(file)
                }
                itemList.add(Item(file.name, null, file.isDirectory))
            }

            if (hasAudio) {
                itemList.add(0, Item(Strings.SHUFFLE_PLAY, null, true))
            }
        }
        sorted = true
    }

    override fun enter(): Boolean {
        if (list.isEmpty()) {
            return false
        }
        if (hasAudio && index == 0) {
            val musics = MediaStoreUtil.getMusicList(
                "${MediaStore.Audio.Media.DATA} like ? ",
                arrayOf("${file.path}%")
            )
            if (musics.isEmpty()) {
                musics.ensureCapacity(musicList.size)
                for (file in musicList) {
                    val music = MediaStoreUtil.getMusicFromFile(file.path)
                    if (music != null) {
                        musics.add(music)
                    }
                }
            } else {
                val lastIndex = file.path.length
                val iterator = musics.iterator()
                while (iterator.hasNext()) {
                    val music = iterator.next()
                    if (music.data?.lastIndexOf('/') != lastIndex) {
                        iterator.remove()
                    }
                }
            }
            MediaPresenter.shufflePlay(musics)
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
                    val music = MediaStoreUtil.getMusicFromFile(file.path)
                    if (music != null) {
                        MediaPresenter.set(music)
                        Core.addView(MusicPlayerView(context))
                    }
                }
                FileUtil.TYPE_TEXT -> {
                    Core.addView(TxtView(context, file))
                }
                else -> return false
            }
        }
        return true
    }

    override fun enterLongClick(): Boolean {
        if (list.isEmpty()) {
            return false
        }
        if (hasAudio && index == 0) {
            return false
        }
        val id = if (hasAudio) index - 1 else index
        val file = list[id]
        if (file.isDirectory) {
            val folder = file.listFiles() ?: return false
            for (f in folder) {
                if (FileUtil.isAudio(f)) {
                    MusicListTable.folder.add(
                        MusicList.Builder().apply { type = MusicList.TYPE_FOLDER; title = file.name; data = file.path }
                            .build()
                    )
                    shake()
                    return true
                }
            }
        } else if (FileUtil.isAudio(file)) {
            val music = MediaStoreUtil.getMusicFromFile(file.path)
            if (music != null) {
                MusicTable.favourite.add(music)
                shake()
                return true
            }
        }
        return false
    }

    override fun slide(slideVal: Int): Boolean {
        return onSlide(slideVal)
    }

    override fun getTitle(): String {
        return file.name
    }
}