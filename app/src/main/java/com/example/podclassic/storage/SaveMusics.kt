package com.example.podclassic.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CursorFactory
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.podclassic.`object`.MediaPlayer
import com.example.podclassic.`object`.Music
import com.example.podclassic.`object`.MusicList
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.service.MediaPlayerService
import java.io.File
import java.net.IDN

class SaveMusics(private val databaseName: String, val name : String) {

    private val list = search(null, null)

    private fun getDatabase() : SQLiteDatabase {
        return DatabaseOpenHelper(BaseApplication.getContext(), databaseName, null, 1).writableDatabase
    }

    fun size() : Int {
        return list.size
    }

    fun contains(music : Music) : Boolean {
        return list.contains(music)
    }

    fun getList() : ArrayList<Music> {
        return list
    }

    fun getMusicList() : MusicList {
        return MusicList(name, list)
    }

    fun clear() {
        list.clear()
        val db = getDatabase()
        db.delete(databaseName, null, null)
        db.close()
    }

    fun add(music: Music) {
        if (contains(music)) {
            return
        }
        list.add(music)
        val values = ContentValues()
        putMusic(values, music)
        val db = getDatabase()
        db.insert(databaseName, null, values)
        db.close()
        if (music == MediaPlayer.getCurrent()) {
            sendNotification()
        }
    }

    fun addAll(list : ArrayList<Music>) {
        if (list.isEmpty()) {
            return
        }
        this.list.addAll(list)
        val db = getDatabase()
        for (music in list) {
            val values = ContentValues()
            putMusic(values, music)
            db.insert(databaseName, null, values)
        }
        db.close()
    }

    fun removeAt(index : Int) {
        val music = list.removeAt(index)
        val db = getDatabase()
        db.delete(databaseName, "$NAME=? and $SINGER=? and $ALBUM=? and $PATH=?", arrayOf(music.name, music.singer, music.album, music.path))
        db.close()
        if (music == MediaPlayer.getCurrent()) {
            sendNotification()
        }
    }

    fun remove(music: Music) {
        list.remove(music)
        val db = getDatabase()
        db.delete(databaseName, "$NAME=? and $SINGER=? and $ALBUM=? and $PATH=?", arrayOf(music.name, music.singer, music.album, music.path))
        db.close()
        if (music == MediaPlayer.getCurrent()) {
            sendNotification()
        }
    }

    fun search(selection: String?, selectionArgs: Array<String>?) : ArrayList<Music> {
        val db = getDatabase()
        val cursor = db.query(databaseName, arrayOf(NAME, PATH, SINGER, ALBUM, ID), selection, selectionArgs, null, null, null)
        val list = ArrayList<Music>()
        val deleteList = ArrayList<Music>()

        if (cursor.moveToFirst()) {
            do {
                val music = buildMusicFromCursor(cursor)
                val file = File(music.path)
                if (isFileExist(file)) {
                    list.add(music)
                } else {
                    deleteList.add(music)
                }
            } while (cursor.moveToNext())
        }
        cursor.close()
        for (music in deleteList) {
            db.delete(databaseName, "$NAME=? and $SINGER=? and $ALBUM=? and $PATH=?", arrayOf(music.name, music.singer, music.album, music.path))
        }
        db.close()
        return list
    }

    private fun isFileExist(file : File) : Boolean {
        return file.exists() && file.isFile && file.length() != 0L
    }

    private fun sendNotification() {
        val context = BaseApplication.getContext()
        context.startService(Intent(context, MediaPlayerService::class.java))
    }

    companion object {
        private class DatabaseOpenHelper(context: Context?, private val name: String, factory: CursorFactory?, version: Int) : SQLiteOpenHelper(context, name, factory, version) {
            override fun onCreate(db: SQLiteDatabase) {
                val sql = "create table if not exists $name (number integer primary key autoincrement, $NAME varchar, $PATH varchar, $SINGER varchar, $ALBUM varchar, $ID varchar)"
                db.execSQL(sql)
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }

        const val NUMBER = "number"
        const val NAME = "name"
        const val PATH = "path"
        const val SINGER = "singer_name"
        const val ALBUM = "album_name"
        const val ID = "album_id"

        private const val LOVE_LIST = "love_list"

        private const val LOVE_LIST_NAME = "收藏的音乐"

        val loveList = SaveMusics(LOVE_LIST, LOVE_LIST_NAME)

        fun buildMusicFromCursor(cursor: Cursor) : Music {
            val name = cursor.getString(cursor.getColumnIndex(NAME))
            val album = cursor.getString(cursor.getColumnIndex(ALBUM))
            val singer = cursor.getString(cursor.getColumnIndex(SINGER))
            val path = cursor.getString(cursor.getColumnIndex(PATH))
            val id = cursor.getLong(cursor.getColumnIndex(ID))
            return Music(name, album, singer, path, id)
        }

        fun putMusic(values: ContentValues, music: Music): ContentValues? {
            values.put(NAME, music.name)
            values.put(SINGER, music.singer)
            values.put(ALBUM, music.album)
            values.put(PATH, music.path)
            values.put(ID, music.id)
            return values
        }
    }
}