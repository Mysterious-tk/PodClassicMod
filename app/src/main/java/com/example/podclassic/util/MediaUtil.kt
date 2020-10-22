package com.example.podclassic.util

import android.database.Cursor
import android.provider.MediaStore
import android.text.TextUtils
import com.example.podclassic.`object`.Music
import com.example.podclassic.`object`.MusicList
import com.example.podclassic.base.BaseApplication
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

object MediaUtil {
    private const val MIN_DURATION = 10 * 1000
    private const val MIN_SIZE = 512 * 1024

    const val SINGER = MediaStore.Audio.Media.ARTIST
    const val ALBUM = MediaStore.Audio.Media.ALBUM
    const val NAME = MediaStore.Audio.Media.TITLE
    const val PATH = MediaStore.Audio.Media.DATA
    private val contentResolver = BaseApplication.getContext().contentResolver


    val musics = ArrayList<Music>()
    val singers = ArrayList<MusicList>()
    val albums = ArrayList<MusicList>()

    private fun buildMusicFromCursor(cursor : Cursor) : Music? {
        val duration = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION))
        val size = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.SIZE))
        val path = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
        if (!path.substring(0, path.lastIndexOf('/')).toLowerCase(Locale.ROOT).contains("record") && duration >= MIN_DURATION && size >= MIN_SIZE) {
            val album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))
            val singer = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))
            val name = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
            return Music(name, album, singer, path)
        }
        return null
    }

    private var prepared = false
    fun prepare() {
        if (prepared) {
            return
        }
        var uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        var cursor = contentResolver.query(uri, null, null, null, null)
        while (cursor!!.moveToNext()) {
            val music = buildMusicFromCursor(cursor)
            if (music != null) {
                musics.add(music)
            }
        }
        cursor.close()
        musics.sortBy { PinyinUtil.getPinyin(it.name) }

        uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        cursor = contentResolver.query(uri, null, null, null, null)
        var hashSet = HashSet<MusicList>()
        while (cursor!!.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM))
            if (TextUtils.isEmpty(name)) {
                continue
            }
            val album = MusicList()
            album.type = MusicList.TYPE_ALBUM
            album.name = name
            hashSet.add(album)
        }
        cursor.close()
        albums.addAll(hashSet)
        albums.sortBy { PinyinUtil.getPinyin(it.name) }

        hashSet = HashSet<MusicList>()
        uri = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI
        cursor = contentResolver.query(uri, null, null, null, null)
        while (cursor!!.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Artists.ARTIST))
            if (TextUtils.isEmpty(name)) {
                continue
            }
            val singer = MusicList()
            singer.type = MusicList.TYPE_SINGER
            singer.name = name
            hashSet.add(singer)
        }
        cursor.close()
        singers.addAll(hashSet)
        singers.sortBy { PinyinUtil.getPinyin(it.name) }
        prepared = true
    }

    fun searchMusic(selection : String?, selectionArgs : Array<String>) : ArrayList<Music>{
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cursor = contentResolver.query(uri, null, selection, selectionArgs, null)
        val result = ArrayList<Music>()
        while (cursor!!.moveToNext()) {
            val music = buildMusicFromCursor(cursor)
            if (music != null) {
                result.add(music)
            }
        }
        result.sortBy { PinyinUtil.getPinyin(it.name) }
        cursor.close()
        return result
    }

    fun searchAlbum(by : String?, target: String?): ArrayList<MusicList> {
        val list = HashSet<MusicList>()
        val uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        val cursor = contentResolver.query(uri, null, if (by == null) null else "$by=?", if (target == null) null else arrayOf(target), null)
        while (cursor!!.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM))
            if (TextUtils.isEmpty(name)) {
                continue
            }
            val album = MusicList()
            album.type = MusicList.TYPE_ALBUM
            album.name = name
            list.add(album)
        }
        cursor.close()
        val temp = ArrayList<MusicList>(list)
        temp.sortBy { PinyinUtil.getPinyin(it.name) }
        return temp
    }

    private var photoSize: Int? = null
    val photoList by lazy {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val cursor = contentResolver.query(uri, null, null, null, null)
        val list = ArrayList<File>()
        while (cursor!!.moveToNext()) {
            list.add(File(cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA))))
        }
        cursor.close()
        photoSize = list.size
        list
    }

    private var videoSize: Int? = null
    val videoList by lazy {
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val cursor = contentResolver.query(uri, null, null, null, null)
        val list = arrayListOf<File>()
        while (cursor!!.moveToNext()) {
            list.add(File(cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.DATA))))
        }
        cursor.close()
        videoSize = list.size
        list
    }

    fun getPhotoSize(): Int {
        if (photoSize != null) {
            return photoSize!!
        }
        val cursor = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null, null, null)
        val size = cursor?.count
        cursor?.close()
        photoSize = size
        return photoSize!!
    }
    fun getVideoSize(): Int {
        if (videoSize != null) {
            return videoSize!!
        }
        val cursor = contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, null, null, null)
        val size = cursor?.count
        cursor?.close()
        videoSize = size
        return size!!
    }
}