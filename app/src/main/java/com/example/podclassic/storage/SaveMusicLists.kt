package com.example.podclassic.storage

import android.text.TextUtils
import com.example.podclassic.`object`.MusicList
import java.io.File

class SaveMusicLists(val name: String, val type : Int) {

    companion object {

        private const val SAVE_FOLDERS = "save_folders"
        private const val SAVE_SINGERS = "save_singers"
        private const val SAVE_ALBUMS = "save_albums"

        val saveFolders by lazy { SaveMusicLists(SAVE_FOLDERS, -1) }
        val saveSingers by lazy { SaveMusicLists(SAVE_SINGERS, MusicList.TYPE_SINGER) }
        val saveAlbums by lazy { SaveMusicLists(SAVE_ALBUMS, MusicList.TYPE_ALBUM) }
    }

    val list = HashSet<String>()

    init {
        val array = SPManager.getString(name).split('\n')
        for (str in array) {
            if (!TextUtils.isEmpty(str)) {
                list.add(str)
            }
        }
    }

    fun contains(string: String): Boolean {
        return list.contains(string)
    }

    fun remove(string: String) {
        list.remove(string)
        update()
    }

    fun add(string: String) {
        list.add(string)
        update()
    }

    fun clear() {
        list.clear()
        update()
    }

    fun getFolders() : ArrayList<File> {
        val arrayList = ArrayList<File>()
        for (item in list) {
            val file = File(item)
            if (file.exists() && file.isDirectory) {
                arrayList.add(file)
            }
        }
        return arrayList
    }

    fun getList() : ArrayList<String> {
        return ArrayList(list)
    }

    private fun update() {
        val content = StringBuilder()
        for (s in list) {
            content.append(s).append('\n')
        }
        if (content.isNotEmpty()) {
            content.deleteCharAt(content.length - 1)
        }
        SPManager.setString(name , content.toString())
    }
}