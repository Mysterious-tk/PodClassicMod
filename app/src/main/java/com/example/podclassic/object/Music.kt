package com.example.podclassic.`object`

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import com.example.podclassic.util.FileUtil
import com.example.podclassic.util.MediaMetadataUtil
import com.example.podclassic.util.Values
import java.io.File
import java.lang.Exception


class Music {
    companion object {
        private class ImageSet(var image : Bitmap?, var music : Music?)

        private var imageSet : ImageSet = ImageSet(null, null)
        private var lyricSet : MediaMetadataUtil.LyricSet? = null
    }

    var name = ""
    set(value) {
        if (value.isNotBlank()) {
            field = value
        }
    }
    var album = Values.NULL
        set(value) {
            if (value.isNotBlank()) {
                field = value
            }
        }
    var singer = Values.NULL
        set(value) {
            if (value.isNotBlank()) {
                field = value
            }
        }
    var path : String = ""

    constructor(name : String, album : String, singer : String, path : String) {
        this.name = name
        this.album = album
        this.singer = singer
        this.path = path
    }

    constructor(file : File) {
        path = file.path
        val name = file.name
        this.name = name.substring(0, name.lastIndexOf('.'))
        MediaMetadataUtil.getMusicInfo(this)
    }

    constructor(uri : Uri) {
        path = FileUtil.uriToPath(uri)
        val mediaMetadataRetriever = MediaMetadataRetriever()
        mediaMetadataRetriever.setDataSource(path)

        val name = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        if (name == null) {
            this.name = path.substring(path.lastIndexOf(File.separatorChar) + 1, path.lastIndexOf('.'))
        } else {
            this.name = name
        }
        val album = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        if (album == null) {
            this.album = Values.NULL
        } else {
            this.album = album
        }
        val singer = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        if (singer == null) {
            this.singer = Values.NULL
        } else {
            this.singer = singer
        }

        mediaMetadataRetriever.release()
    }

    fun getLyric() : MediaMetadataUtil.LyricSet? {
        if (lyricSet?.music == this) {
            return lyricSet
        }
        lyricSet = MediaMetadataUtil.getLyric(this)
        return lyricSet
    }

    fun getImage() : Bitmap? {
        if (imageSet.music == this) {
            return imageSet.image
        }

        val mediaMetadataRetriever = MediaMetadataRetriever()
        mediaMetadataRetriever.setDataSource(path)
        val byteArray = mediaMetadataRetriever.embeddedPicture

        mediaMetadataRetriever.release()
        val image = if (byteArray == null || byteArray.isEmpty()) {
            null
        } else {
            ThumbnailUtils.extractThumbnail(BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size), 512, 512)
        }
        val tempImage = imageSet.image
        if (tempImage?.isRecycled == false) {
            tempImage.recycle()
        }
        imageSet.music = this
        imageSet.image = image
        return image
    }

    override fun hashCode(): Int {
        return path.hashCode()
    }

    override fun toString(): String {
        return "name = $name , album = $album , singer = $singer , path = $path"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Music) {
            return false
        }
        if (other.path == path && !TextUtils.isEmpty(path)) {
            return true
        }
        return false
    }
}