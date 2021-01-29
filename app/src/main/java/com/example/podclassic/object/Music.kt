package com.example.podclassic.`object`

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.text.TextUtils
import com.example.podclassic.util.FileUtil
import com.example.podclassic.util.LyricUtil
import com.example.podclassic.util.Values
import java.io.File


class Music {
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
    var id : Long = 0L

    constructor(name : String, album : String, singer : String, path : String, id : Long) {
        this.name = name
        this.album = album
        this.singer = singer
        this.path = path
        this.id = id
    }

    constructor(file : File) {
        path = file.path

        getMusicInfo()
    }

    constructor(uri : Uri) {
        path = FileUtil.uriToPath(uri)
        getMusicInfo()
    }

    private fun getMusicInfo() {
        val mediaMetadataRetriever = MediaMetadataRetriever()
        mediaMetadataRetriever.setDataSource(path)
        name = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        singer = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        album = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        mediaMetadataRetriever.release()
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