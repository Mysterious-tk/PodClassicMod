package com.example.podclassic.util

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.MediaStore
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.bean.Music

object MediaUtil {
    fun getMusicPath(uri: Uri): String {
        val cursor = BaseApplication.context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATA),
            null,
            null,
            null
        )
        val path = if (cursor?.moveToFirst() == true) {
            cursor.getString(0)
        } else {
            ""
        }
        cursor?.close()
        return path
    }

    fun getMusicUri(songId: Long): Uri {
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
    }

    fun getAlbumImage(albumId: Long): Bitmap? {
        val artworkUri = Uri.parse("content://media/external/audio/albumart")
        val uri = ContentUris.withAppendedId(artworkUri, albumId)
        val inputStream = try {
            BaseApplication.context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            return null
        }
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        return bitmap
    }

    private fun getMusicImage(path: String): Bitmap? {
        val mediaMetadataRetriever = MediaMetadataRetriever()
        mediaMetadataRetriever.setDataSource(path)
        val byteArray = mediaMetadataRetriever.embeddedPicture
        mediaMetadataRetriever.release()
        return if (byteArray == null || byteArray.isEmpty()) {
            null
        } else {
            return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        }
    }


    fun getMusicImage(music: Music): Bitmap? {
        val bitmap = if (music.albumId != null && music.albumId != 0L) {
            getAlbumImage(music.albumId)
        } else if (music.data != null) {
            getMusicImage(music.data)
        } else {
            null
        }
        if (bitmap != null) {
            return ThumbnailUtils.extractThumbnail(
                bitmap,
                400,
                400,
                ThumbnailUtils.OPTIONS_RECYCLE_INPUT
            )
        }
        return null
    }

    fun getLyric() {

    }

}