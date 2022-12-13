package com.example.podclassic.util

import android.content.ContentUris
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.bean.Music
import com.example.podclassic.bean.MusicList
import com.example.podclassic.values.Values
import java.io.File

object MediaStoreUtil {
    private const val MIN_DURATION = 60 * 1000
    private const val MIN_SIZE = 1024 * 1024
    private const val MAX_DURATION = 60 * 60 * 1000

    private fun getMusicsFromCursor(cursor: Cursor): ArrayList<Music> {
        val list = ArrayList<Music>(cursor.count)

        val durationIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
        val sizeIndex = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
        val pathIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
        val albumIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
        val artistIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
        val titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
        val albumIdIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
        val idIndex = cursor.getColumnIndex(MediaStore.Audio.Media._ID)


        while (cursor.moveToNext()) {
            val duration = cursor.getLong(durationIndex)
            val size = cursor.getLong(sizeIndex)
            val data = cursor.getString(pathIndex)
            val lowerPath = data.lowercase()

            // 屏蔽通话录音
            if (lowerPath.contains("通话录音") || lowerPath.contains("Recorder") || lowerPath.contains("Recordings") || (lowerPath.contains(
                    "call"
                ) && lowerPath.contains("record"))
            ) {
                continue
            }
            // 屏蔽过长或过短音频
            if (duration < MIN_DURATION || size < MIN_SIZE || duration > MAX_DURATION) {
                continue
            }

            val album = cursor.getString(albumIndex)
            val artist = cursor.getString(artistIndex)
            val title = cursor.getString(titleIndex)
            val id = cursor.getLong(idIndex)
            val albumId = cursor.getLong(albumIdIndex)
            val music = Music.Builder().apply {
                this.title = title
                this.artist = artist
                this.album = album
                this.id = id
                this.albumId = albumId
                this.data = data
                this.duration = duration
            }.build()
            list.add(music)
        }
        return list
    }

    private fun getAlbumFromCursor(cursor: Cursor): ArrayList<MusicList> {
        //val list = ArrayList<MusicList>(cursor.count)
        val set = HashSet<MusicList>(cursor.count)
        val albumIndex = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM)
        val albumIdIndex = cursor.getColumnIndex(MediaStore.Audio.Albums._ID)
        val artistIndex = cursor.getColumnIndex(MediaStore.Audio.Albums.ARTIST)
        var albumIDIndex = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ID)

        var AlbumsMap = mutableMapOf<Long, Long>()
        for (music in musicCache!!)
        {
            if (music.albumId != null)
                AlbumsMap.put(music.albumId, 1)
        }

        while (cursor.moveToNext()) {
            var albumID = cursor.getString(albumIDIndex).toLong()
            Log.d("GetAlbum2:", albumID.toString())
            val musicList = MusicList.Builder()
                .apply {
                    title = cursor.getString(albumIndex)
                    data = title
                    subtitle = cursor.getString(artistIndex)
                    id = cursor.getLong(albumIdIndex)
                    type = MusicList.TYPE_ALBUM
                }.build()
            if (AlbumsMap.get(albumID) != null)
            {
                set.add(musicList)
            }

        }
        cursor.close()

        return ArrayList(set)
    }

    private fun getArtistFromCursor(cursor: Cursor): ArrayList<MusicList> {
        //val list = ArrayList<MusicList>(cursor.count)
        val set = HashSet<MusicList>(cursor.count)

        val artistIndex = cursor.getColumnIndex(MediaStore.Audio.Artists.ARTIST)
        while (cursor.moveToNext()) {
            val musicList = MusicList.Builder().apply {
                title = cursor.getString(artistIndex)
                data = title
                subtitle = data
                type = MusicList.TYPE_ARTIST
            }.build()
            set.add(musicList)
        }
        cursor.close()

        return ArrayList(set)
    }

    fun getMusicById(id: Long): Music? {
        val cursor = BaseApplication.context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ALBUM_ID
            ),
            "${MediaStore.Audio.Media._ID}=?",
            arrayOf(id.toString()),
            null//MediaStore.Audio.Media.DEFAULT_SORT_ORDER
        ) ?: return null
        val list = getMusicsFromCursor(cursor)
        cursor.close()
        return list[0]
    }

    fun getMusicFromUri(uri: Uri): Music {
        val path = FileUtil.uriToPath(uri)
        return if (path != null && path.isNotBlank()) {
            getMusicFromFile(path)
        } else {
            MediaMetadataUtil.getMediaMetadata(uri)
        }
    }

    fun getMusicFromFile(file: File): Music {
        return getMusicFromFile(file.path)
    }

    fun getMusicFromFile(path: String): Music {
        val cursor = BaseApplication.context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE
            ),
            "${MediaStore.Audio.Media.DATA}=?",
            arrayOf(path),
            null
        ) ?: return MediaMetadataUtil.getMediaMetadata(path)

        val list = getMusicsFromCursor(cursor)
        cursor.close()
        return if (list.size == 0) {
            MediaMetadataUtil.getMediaMetadata(path)
        } else {
            list[0]
        }
    }

    fun getAlbumList(): ArrayList<MusicList> {
        val cursor = BaseApplication.context.contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Albums.ALBUM_ID,
                MediaStore.Audio.Albums.ALBUM,
                MediaStore.Audio.Albums.ARTIST,
                MediaStore.Audio.Albums._ID,
            ),
            null,
            null,
            null//"${MediaStore.Audio.Albums.ALBUM} collate localized"
        ) ?: return ArrayList()





        val list = getAlbumFromCursor(cursor)
        cursor.close()
        list.sort()
        return list
    }

    fun getArtistList(): ArrayList<MusicList> {
        val cursor = BaseApplication.context.contentResolver.query(
            MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Artists.ARTIST,
            ),
            null,
            null,
            null//"${MediaStore.Audio.Artists.ARTIST} collate localized"
        ) ?: return ArrayList()
        val list = getArtistFromCursor(cursor)
        cursor.close()
        list.sort()
        return list
    }

    fun getAlbumMusic(album: String): ArrayList<Music> {
        val cursor = BaseApplication.context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION
            ),
            "${MediaStore.Audio.Media.ALBUM}=?",
            arrayOf(album),
            MediaStore.Audio.Media.TRACK
        ) ?: return ArrayList()
        val list = getMusicsFromCursor(cursor)
        cursor.close()
        return list
    }

    fun getArtistMusic(artist: String): ArrayList<Music> {
        val cursor = BaseApplication.context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE
            ),
            "${MediaStore.Audio.Media.ARTIST} like ? ",
            arrayOf("%$artist%"),
            null//MediaStore.Audio.Media.TITLE //+ " collate localized"
        ) ?: return ArrayList()
        val list = getMusicsFromCursor(cursor)
        cursor.close()
        list.sort()
        return list
    }

    fun getArtistAlbum(artist: String): ArrayList<MusicList> {
        val cursor = BaseApplication.context.contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums._ID),
            "${MediaStore.Audio.Albums.ARTIST} like ? ",
            arrayOf("%$artist%"),
            null//MediaStore.Audio.Albums.ALBUM //+ " collate localized"
        ) ?: return ArrayList()
        val titleId = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM)
        val albumId = cursor.getColumnIndex(MediaStore.Audio.Albums._ID)

        val list = ArrayList<MusicList>()
        while (cursor.moveToNext()) {
            val title = cursor.getString(titleId)
            if (TextUtils.isEmpty(title)) {
                continue
            }
            val album = MusicList.Builder().apply {
                type = MusicList.TYPE_ALBUM
                this.title = title
                this.id = cursor.getLong(albumId)
            }.build()
            list.add(album)
        }
        cursor.close()
        list.sort()
        //val temp = ArrayList<MusicList>(list)
        //temp.sortWith { o1, o2 -> collator.compare(o1?.name, o2?.name) }
        return list
    }

    fun getImageById(id: Long?): Bitmap? {
        if (id == null || id == 0L) {
            return null
        }
        val artworkUri = Uri.parse("content://media/external/audio/albumart")
        val uri = ContentUris.withAppendedId(artworkUri, id)
        try {
            BaseApplication.context.contentResolver.openInputStream(uri).use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                return ThumbnailUtils.extractThumbnail(
                    bitmap,
                    Values.IMAGE_WIDTH,
                    Values.IMAGE_WIDTH,
                    ThumbnailUtils.OPTIONS_RECYCLE_INPUT
                )
            }
        } catch (ignored: Exception) {
            return null
        }
    }


    fun getPhotoList(): ArrayList<File> {
        val cursor = BaseApplication.context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media.DATA),
            null,
            null,
            MediaStore.Images.Media.TITLE
        ) ?: return ArrayList()
        val list = ArrayList<File>(cursor.count)
        while (cursor.moveToNext()) {
            val dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
            list.add(File(cursor.getString(dataIndex)))
        }
        cursor.close()
        return list
    }

    fun getPhotoSize(): Int {
        val cursor = BaseApplication.context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(),
            null,
            null,
            null
        ) ?: return 0
        val size = cursor.count
        cursor.close()
        return size
    }

    fun getVideoList(): ArrayList<File> {
        val cursor = BaseApplication.context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media.DATA),
            null,
            null,
            MediaStore.Video.Media.TITLE//} collate localized"
        ) ?: return ArrayList()
        val list = ArrayList<File>(cursor.count)
        while (cursor.moveToNext()) {
            val dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
            list.add(File(cursor.getString(dataIndex)))
        }
        cursor.close()
        return list
    }

    fun getVideoSize(): Int {
        val cursor = BaseApplication.context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(),
            null,
            null,
            null
        ) ?: return 0
        val size = cursor.count
        cursor.close()
        return size
    }


    private var musicCache : ArrayList<Music>? = null
    private var cacheTime = 0L
    private const val HOUR = 60 * 60 * 1000L
    fun getMusicList(): ArrayList<Music> {
        val time = System.currentTimeMillis()
        if (musicCache == null || time - cacheTime > HOUR) {
            musicCache = getMusicList(null, null)
            cacheTime = time
        }
        return musicCache!!
    }

    fun init() {
        musicCache = getMusicList()
        cacheTime = System.currentTimeMillis()
    }

    fun getMusicSize(): Int {
        val cursor = BaseApplication.context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(),
            null,
            null,
            null
        ) ?: return 0
        val size = cursor.count
        cursor.close()
        return size
    }

    fun getMusicList(selection: String?, selectionArgs: Array<String>?): ArrayList<Music> {
        val cursor = BaseApplication.context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
            ),
            selection,
            selectionArgs,
            null,//MediaStore.Audio.Media.DEFAULT_SORT_ORDER

        ) ?: return ArrayList()
        val list = getMusicsFromCursor(cursor)
        cursor.close()
        //list.sortBy { pinyinUtil.getPinyinChar( it.title[0]) }
        //list.sortBy { PinyinUtil.getInstance(BaseApplication.context).getPinyinChar(it.title[0]) }
        list.sort()
        return list
    }

}