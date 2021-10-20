package com.example.podclassic.util

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.MediaStore
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.example.podclassic.`object`.Music
import com.example.podclassic.`object`.MusicList
import com.example.podclassic.base.BaseApplication
import java.io.File
import java.text.Collator
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

object MediaStoreUtil {
    private const val MIN_DURATION = 10 * 1000
    private const val MIN_SIZE = 512 * 1024
    private const val MAX_DURATION = 60 * 60 * 1000

    const val SINGER = MediaStore.Audio.Media.ARTIST
    const val ALBUM = MediaStore.Audio.Media.ALBUM
    const val NAME = MediaStore.Audio.Media.TITLE
    const val PATH = MediaStore.Audio.Media.DATA


    private val contentResolver = BaseApplication.context.contentResolver
    private val collator = Collator.getInstance(Locale.CHINA)

    val musics = ArrayList<Music>()
    val singers = ArrayList<MusicList>()
    val albums = ArrayList<MusicList>()

    private fun buildMusicFromCursor(cursor: Cursor): Music? {
        val duration = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION))
        val size = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.SIZE))
        val path = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA))

        if (path.contains("通话录音") || /*path.substring(0, path.lastIndexOf('/')).lowercase(Locale.ROOT).contains("record") ||*/ duration < MIN_DURATION || size < MIN_SIZE || duration > MAX_DURATION) {
            return null
        }
        val album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))
        val singer = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))
        val name = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
        val id = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID))
        return Music(name, album, singer, path, id)
    }

    private var prepared = false
    fun prepare() {
        if (ContextCompat.checkSelfPermission(BaseApplication.context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        if (prepared) {
            return
        }

        var uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        var cursor = contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        )//MediaStore.Audio.Media.TITLE_RESOURCE_URI + " collate localized")

        musics.ensureCapacity(cursor!!.count)
        while (cursor.moveToNext()) {
            val music = buildMusicFromCursor(cursor)
            if (music != null) {
                musics.add(music)
            }
        }
        musics.sortWith { o1, o2 -> collator.compare(o1?.name, o2?.name) }
        cursor.close()

        uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        cursor = contentResolver.query(
            uri,
            null,
            null,
            null,
            null/*MediaStore.Audio.Albums.ALBUM + " collate localized"*/
        )
        var hashSet = HashSet<MusicList>(cursor!!.count)
        val albumIdIndex = cursor.getColumnIndex(MediaStore.Audio.Albums._ID)

        val artistIndex = cursor.getColumnIndex(MediaStore.Audio.Albums.ARTIST)
        val nameIndex = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM)

        while (cursor.moveToNext()) {
            val name = cursor.getString(nameIndex)
            if (TextUtils.isEmpty(name)) {
                continue
            }

            val artist = cursor.getString(artistIndex)
            val id = if (albumIdIndex == -1) 0L else cursor.getLong(albumIdIndex)

            val album = MusicList()
            album.type = MusicList.TYPE_ALBUM
            album.name = name
            album.artist = artist
            album.id = id
            hashSet.add(album)
        }
        albums.addAll(hashSet)
        albums.sortWith { o1, o2 -> collator.compare(o1?.name, o2?.name) }
        cursor.close()

        uri = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI
        cursor = contentResolver.query(
            uri,
            null,
            null,
            null, /*MediaStore.Audio.Artists.ARTIST + " collate localized"*/
            null
        )
        hashSet = HashSet(cursor!!.count)
        while (cursor.moveToNext()) {
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
        singers.sortWith { o1, o2 -> collator.compare(o1?.name, o2?.name) }

        System.gc()
        prepared = true
    }

    fun getAlbumMusic(album: String): ArrayList<Music> {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cursor = contentResolver.query(
            uri,
            null,
            "$ALBUM=?",
            arrayOf(album),
            MediaStore.Audio.Media.TRACK
        )
        val result = ArrayList<Music>(cursor!!.count)
        while (cursor.moveToNext()) {
            val music = buildMusicFromCursor(cursor)
            if (music != null) {
                result.add(music)
            }
        }
        cursor.close()
        return result
    }

    fun getArtistMusic(artist: String): ArrayList<Music> {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cursor = contentResolver.query(
            uri,
            null,
            "$SINGER like ? ",
            arrayOf("%$artist%"),
            null/*MediaStore.Audio.Media.TITLE + " collate localized"*/
        )
        val result = ArrayList<Music>(cursor!!.count)
        while (cursor.moveToNext()) {
            val music = buildMusicFromCursor(cursor)
            if (music != null) {
                result.add(music)
            }
        }
        cursor.close()
        result.sortWith { o1, o2 -> collator.compare(o1?.name, o2?.name) }
        return result
    }

    fun searchMusic(by: String, target: String): ArrayList<Music> {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cursor = contentResolver.query(uri, null, by, arrayOf(target), null)
        val list = ArrayList<Music>(cursor!!.count)

        while (cursor.moveToNext()) {
            val music = buildMusicFromCursor(cursor)
            if (music != null) {
                list.add(music)
            }
        }
        cursor.close()

        list.sortWith { o1, o2 -> collator.compare(o1?.name, o2?.name) }
        return list
    }

    fun getMusic(path: String): Music {
        val list = searchMusic("${PATH}=?", path)
        if (list.isEmpty()) {
            return getMusicFromFile(path)
        }
        return list[0]
    }

    fun getMusicFromFile(path: String): Music {
        val mediaMetadataRetriever = MediaMetadataRetriever()
        mediaMetadataRetriever.setDataSource(path)
        var name = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        if (name == null) {
            name = path.substring(path.lastIndexOf(File.separatorChar), path.lastIndexOf('.'))
        }
        val singer =
            mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        val album =
            mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        mediaMetadataRetriever.release()
        return Music(name, singer, album, path, 0L)
    }


    fun searchAlbum(singer: String): ArrayList<MusicList> {
        val uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        val cursor = contentResolver.query(
            uri,
            null,
            "$SINGER like ? ",
            arrayOf("%$singer%"),
            null/*MediaStore.Audio.Albums.ALBUM + " collate localized"*/
        )
        val list = HashSet<MusicList>(cursor!!.count)
        val nameId = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM)
        val artistId = cursor.getColumnIndex(MediaStore.Audio.Albums.ARTIST)
        while (cursor.moveToNext()) {
            val name = cursor.getString(nameId)
            val artist = cursor.getString(artistId)

            if (TextUtils.isEmpty(name)) {
                continue
            }
            val album = MusicList()
            album.type = MusicList.TYPE_ALBUM
            album.name = name
            album.artist = artist
            list.add(album)
        }
        cursor.close()
        val temp = ArrayList<MusicList>(list)
        temp.sortWith { o1, o2 -> collator.compare(o1?.name, o2?.name) }
        //temp.sortBy { PinyinUtil.getPinyin(it.name) }
        return temp
    }

    private var photoSize: Int? = null
    val photoList by lazy {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val cursor = contentResolver.query(uri, null, null, null, null)
        val list = ArrayList<File>(cursor!!.count)
        while (cursor.moveToNext()) {
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
        val list = ArrayList<File>(cursor!!.count)
        while (cursor.moveToNext()) {
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
        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            null,
            null,
            null,
            null
        )
        val size = cursor?.count
        cursor?.close()
        photoSize = size
        return photoSize!!
    }

    fun getVideoSize(): Int {
        if (videoSize != null) {
            return videoSize!!
        }
        val cursor = contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            null,
            null,
            null,
            null
        )
        val size = cursor?.count
        cursor?.close()
        videoSize = size
        return size!!
    }


    fun getAlbumImage(id: Long): Bitmap? {
        val artworkUri = Uri.parse("content://media/external/audio/albumart")
        val uri = ContentUris.withAppendedId(artworkUri, id)
        try {
            contentResolver.openInputStream(uri).use { inputStream ->
                return ThumbnailUtils.extractThumbnail(
                    BitmapFactory.decodeStream(inputStream),
                    Values.IMAGE_WIDTH,
                    Values.IMAGE_WIDTH,
                    ThumbnailUtils.OPTIONS_RECYCLE_INPUT
                )
            }
        } catch (ignored: Exception) {
            return null
        }
    }

    private fun getMusicImage(path: String): Bitmap? {
        val mediaMetadataRetriever = MediaMetadataRetriever()
        mediaMetadataRetriever.setDataSource(path)
        val byteArray = mediaMetadataRetriever.embeddedPicture
        mediaMetadataRetriever.release()
        return if (byteArray == null || byteArray.isEmpty()) {
            null
        } else {
            ThumbnailUtils.extractThumbnail(
                BitmapFactory.decodeByteArray(
                    byteArray,
                    0,
                    byteArray.size
                ), Values.IMAGE_WIDTH, Values.IMAGE_WIDTH
            )
        }
    }


    fun getMusicImage(music: Music): Bitmap? {
        return if (music.id != 0L) {
            getAlbumImage(music.id)
        } else {
            getMusicImage(music.path)
        }
    }
}