package com.example.podclassic.storage

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.bean.MusicList

class MusicListTable(val name: String) {

    private val list = search(null)

    fun getList(): ArrayList<MusicList> {
        return list.clone() as ArrayList<MusicList>
    }

    private fun getDatabase(): SQLiteDatabase {
        return DatabaseOpenHelper(BaseApplication.context, name, getTable(name)).writableDatabase
    }

    fun search(data: String?): ArrayList<MusicList> {
        val db = getDatabase()

        val cursor = if (data == null) {
            db.query(name, arrayOf(TITLE, SUBTITLE, DATA, ALBUM_ID), null, null, null, null, null)
        } else {
            db.query(name, arrayOf(TITLE, SUBTITLE, DATA, ALBUM_ID), "$DATA=?", arrayOf(data), null, null, null)

        }
        val list = ArrayList<MusicList>()

        if (cursor.moveToFirst()) {
            val type = when (name) {
                ALBUM_LIST -> MusicList.TYPE_ALBUM
                ARTIST_LIST -> MusicList.TYPE_ARTIST
                FOLDER_LIST -> MusicList.TYPE_FOLDER
                else -> -1
            }
            do {
                val musicList = buildMusicListFromCursor(cursor, type)
                list.add(musicList)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    fun contains(musicList: MusicList): Boolean {
        return list.contains(musicList)
    }

    fun delete(musicList: MusicList) {
        val db = getDatabase()
        db.delete(name, "$DATA=?", arrayOf(musicList.title))
        db.close()
        list.remove(musicList)
    }

    fun add(musicList: MusicList) {
        if (!list.contains(musicList)) {
            val db = getDatabase()
            val values = putMusicList(musicList)
            db.insert(name, null, values)
            list.add(musicList)
        }
    }

    fun clear() {
        list.clear()
        val db = getDatabase()
        db.delete(name, null, null)
        db.close()
    }

    companion object {
        fun getTable(name: String): String {
            return "CREATE TABLE IF NOT EXISTS $name($DATA VARCHAR PRIMARY KEY, $TITLE VARCHAR, $SUBTITLE VARCHAR, $ALBUM_ID VARCHAR);"
        }

        const val FOLDER_LIST = "folder_list"
        const val ALBUM_LIST = "album_list"
        const val ARTIST_LIST = "artist_list"

        const val TITLE = "title"
        const val DATA = "data"
        const val ALBUM_ID = "album_id"
        const val SUBTITLE = "subtitle"

        val folder = MusicListTable(FOLDER_LIST)
        val album = MusicListTable(ALBUM_LIST)
        val artist = MusicListTable(ARTIST_LIST)

        private fun buildMusicListFromCursor(cursor: Cursor, type: Int): MusicList {
            val title = cursor.getString(cursor.getColumnIndex(TITLE))
            val data = cursor.getString(cursor.getColumnIndex(DATA))
            val subtitle = cursor.getString(cursor.getColumnIndex(SUBTITLE))
            val id = cursor.getLong(cursor.getColumnIndex(ALBUM_ID))
            return MusicList.Builder().apply {
                this.title = title
                this.data = data
                this.subtitle = subtitle
                this.id = id
                this.type = type
            }.build()
        }

        private fun putMusicList(musicList: MusicList): ContentValues {
            val values = ContentValues()
            values.put(ALBUM_ID, musicList.id)
            values.put(SUBTITLE, musicList.subtitle)
            values.put(DATA, musicList.data)
            values.put(TITLE, musicList.title)
            return values
        }
    }
}