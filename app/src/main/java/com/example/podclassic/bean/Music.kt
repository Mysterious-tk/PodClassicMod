package com.example.podclassic.bean

import android.graphics.Bitmap
import android.net.Uri
import android.text.TextUtils
import androidx.collection.LruCache
import com.example.podclassic.util.MediaMetadataUtil
import com.example.podclassic.util.MediaUtil
import com.example.podclassic.util.PinyinUtil
import com.example.podclassic.values.Strings


data class Music(
    val title: String, val id: Long?, val artist: String, val album: String, val albumId: Long?,
    val duration: Long, val data: String?, val uri: Uri?
) : Comparable<Music> {
    companion object {
        private val imageCache = LruCache<Music, Bitmap>(4)
        private val lyricCache = LruCache<Music, Lyric>(4)
    }

    class Builder {
        var title: String? = null
        var id: Long? = null
        var album: String? = null
        var albumId: Long? = null
        var artist: String? = null
        var data: String? = null
        var uri: Uri? = null
        var duration: Long? = null

        fun build(): Music {
            return Music(
                title ?: Strings.NULL,
                id,
                artist ?: Strings.NULL,
                album ?: Strings.NULL,
                albumId,
                duration ?: 0L,
                data,
                uri
            )
        }
    }

    private var pinyin: String? = null
        get() {
            if (field == null) {
                field = PinyinUtil.getPinyin(title)
            }
            return field
        }

    private var hasBitmap: Boolean? = null
    val image: Bitmap?
        get() {
            if (hasBitmap == null) {
                val image = MediaUtil.getMusicImage(this)
                return if (image != null) {
                    hasBitmap = true
                    imageCache.put(this, image)
                    image
                } else {
                    hasBitmap = false
                    null
                }
            } else {
                return if (hasBitmap == true) {
                    if (imageCache[this] == null) {
                        val image = MediaUtil.getMusicImage(this)
                        if (image != null) {
                            imageCache.put(this, image)
                        }
                        image
                    } else {
                        imageCache[this]
                    }
                } else {
                    null
                }
            }
        }

    private var hasLyric: Boolean? = null
    val lyric: Lyric?
        get() {
            if (hasLyric == null) {
                val lyric = MediaMetadataUtil.getLyric(data)
                return if (lyric == null) {
                    hasLyric = false
                    null
                } else {
                    hasLyric = true
                    lyricCache.put(this, lyric)
                    lyric
                }
            } else {
                return if (hasLyric == true) {
                    if (lyricCache[this] == null) {
                        val lyric = MediaMetadataUtil.getLyric(data)
                        if (lyric != null) {
                            lyricCache.put(this, lyric)
                        }
                        lyric
                    } else {
                        lyricCache[this]
                    }
                } else {
                    null
                }
            }
        }

    override fun hashCode(): Int {
        return if (id == null || id == 0L) data.hashCode() else id.toInt()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Music) {
            return false
        }
        if (data == null) {
            return id == other.id
        }
        if (other.data == data && !TextUtils.isEmpty(data)) {
            return true
        }
        return false
    }

    override fun compareTo(other: Music): Int {
        return pinyin!!.compareTo(other.pinyin!!)
    }

}