package com.example.podclassic.storage

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.bean.Music
import com.example.podclassic.util.FileUtil
import com.example.podclassic.util.MediaStoreUtil


class MusicTable(private val name: String) {
    private val list = search(null, null)

    fun getList(): ArrayList<Music> {
        return list.clone() as ArrayList<Music>
    }

    fun size(): Int {
        return list.size
    }

    fun contains(music: Music): Boolean {
        return list.contains(music)
    }

    fun clear() {
        list.clear()
        val db = getDatabase()
        db.delete(name, null, null)
        db.close()
    }

    fun add(music: Music): Boolean {
        if (contains(music)) {
            return false
        }
        list.add(music)
        val values = putMusic(music)
        val db = getDatabase()
        db.insert(name, null, values)
        db.close()
        return true
    }

    fun addAll(list: ArrayList<Music>) {
        if (list.isEmpty()) {
            return
        }
        val db = getDatabase()
        for (music in list) {
            if (!contains(music)) {
                this.list.add(music)
                val values = putMusic(music)
                db.insert(name, null, values)
            }
        }
        db.close()
    }

    fun removeAt(index: Int, database: SQLiteDatabase? = null) {
        val music = list.removeAt(index)
        remove(music, database)
    }

    fun remove(music: Music, database: SQLiteDatabase? = null) {
        list.remove(music)
        val db = database ?: getDatabase()
        db.delete(
            name,
            "$TITLE=? and $ARTIST=? and $ALBUM=? and $DATA=?",
            arrayOf(music.title, music.artist, music.album, music.data)
        )
        db.close()
    }

    fun search(selection: String?, selectionArgs: Array<String>?): ArrayList<Music> {
        val db = getDatabase()
        val cursor = db.query(
            name,
            arrayOf(TITLE, DATA, ARTIST, ALBUM, ALBUM_ID, ID, DURATION),
            selection,
            selectionArgs,
            null,
            null,
            null
        )
        val list = ArrayList<Music>()
        val deleteList = ArrayList<Music>()

        if (cursor.moveToFirst()) {
            do {
                val music = buildMusicFromCursor(cursor)
                if (music.data == null) {
                    if (music.id == null || MediaStoreUtil.getMusicById(music.id) == null) {
                        deleteList.add(music)
                    } else {
                        list.add(music)
                    }
                } else {
                    if (!FileUtil.isFileExist(music.data)) {
                        deleteList.add(music)
                    } else {
                        list.add(music)
                    }
                }
            } while (cursor.moveToNext())
        }
        for (music in deleteList) {
            remove(music, db)
        }
        cursor.close()
        db.close()
        return list
    }

    private fun getDatabase(): SQLiteDatabase {
        return DatabaseOpenHelper(BaseApplication.context, name, getTable(name)).writableDatabase
    }

    companion object {
        fun getTable(name: String): String {
            return "CREATE TABLE IF NOT EXISTS $name(number INTEGER PRIMARY KEY AUTOINCREMENT, $TITLE VARCHAR, $ID INTEGER, $ALBUM VARCHAR, $ALBUM_ID INTEGER, $ARTIST VARCHAR, $DURATION VARCHAR, $DATA VARCHAR);"
        }

        const val TITLE = "title"
        const val ID = "_id"
        const val ALBUM = "album"
        const val ALBUM_ID = "album_id"
        const val ARTIST = "artist"
        const val DATA = "data"
        const val DURATION = "duration"

        const val FAVOURITE_LIST = "favourite_list"
        const val RECENT_LIST = "recent_list"

        val favourite = MusicTable(FAVOURITE_LIST)
        val recent = MusicTable(RECENT_LIST)


        fun buildMusicFromCursor(cursor: Cursor): Music {
            val titleIndex = cursor.getColumnIndex(TITLE)
            val albumIndex = cursor.getColumnIndex(ALBUM)
            val albumIdIndex = cursor.getColumnIndex(ALBUM_ID)
            val artistIndex = cursor.getColumnIndex(ARTIST)
            val dataIndex = cursor.getColumnIndex(DATA)
            val idIndex = cursor.getColumnIndex(ID)
            val durationIndex = cursor.getColumnIndex(DURATION)
            
            val title = if (titleIndex >= 0) cursor.getString(titleIndex) else ""
            val album = if (albumIndex >= 0) cursor.getString(albumIndex) else ""
            val albumId = if (albumIdIndex >= 0) cursor.getLong(albumIdIndex) else 0L
            val artist = if (artistIndex >= 0) cursor.getString(artistIndex) else ""
            val data = if (dataIndex >= 0) cursor.getString(dataIndex) else null
            val id = if (idIndex >= 0) cursor.getLong(idIndex) else null
            val duration = if (durationIndex >= 0) cursor.getLong(durationIndex) else 0L
            
            return Music(title, id, artist, album, albumId, duration, data, null)
        }

        fun putMusic(music: Music): ContentValues {
            val values = ContentValues()
            values.put(TITLE, music.title)
            values.put(ARTIST, music.artist)
            values.put(ALBUM, music.album)
            values.put(ALBUM_ID, music.albumId)
            values.put(DATA, music.data)
            values.put(ID, music.id)
            values.put(DURATION, music.duration)
            return values
        }
    }
}