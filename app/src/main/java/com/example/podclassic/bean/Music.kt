package com.example.podclassic.bean

import android.graphics.*
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
    private val defaultPaint = Paint().apply { isAntiAlias = true }

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

    private fun getReflectBitmap(bitmap: Bitmap): Bitmap {
        val reflectionGap = 0
        val width = bitmap.width
        val height = bitmap.height
        val matrix = Matrix()
        matrix.preScale(1f, -1f)
        val reflectionImage =
            Bitmap.createBitmap(bitmap, 0, height / 2, width, height / 2, matrix, false)
        val bitmap4Reflection =
            Bitmap.createBitmap(width, height + height / 2, Bitmap.Config.ARGB_8888)
        val canvasRef = Canvas(bitmap4Reflection)

        canvasRef.drawBitmap(bitmap, 0f, 0f, null)
        canvasRef.drawRect(
            0f,
            height.toFloat(),
            width.toFloat(),
            height + reflectionGap.toFloat(),
            defaultPaint
        )
        canvasRef.drawBitmap(reflectionImage, 0f, height + reflectionGap.toFloat(), null)
        val paint = Paint()
        val shader = LinearGradient(
            0f,
            bitmap.height.toFloat(),
            0f,
            bitmap4Reflection.height.toFloat() + reflectionGap,
            0x70ffffff,
            0x00ffffff,
            Shader.TileMode.CLAMP
        )
        paint.shader = shader
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)

        canvasRef.drawRect(
            0f,
            height.toFloat(),
            width.toFloat(),
            (bitmap4Reflection.height + reflectionGap).toFloat(),
            paint
        )
        return bitmap4Reflection
    }

    private var hasBitmap: Boolean? = null
    val image: Bitmap?
        get() {
            if (hasBitmap == null) {
                var image = MediaUtil.getMusicImage(this)
                return if (image != null) {
                    val ReflectBitmap:Bitmap = getReflectBitmap(image)
                    hasBitmap = true
                    imageCache.put(this, ReflectBitmap)
                    ReflectBitmap
                } else {
                    hasBitmap = false
                    null
                }
            } else {
                return if (hasBitmap == true) {
                    if (imageCache[this] == null) {
                        var image = MediaUtil.getMusicImage(this)

                        if (image != null) {
                            val ReflectBitmap = getReflectBitmap(image)
                            imageCache.put(this, ReflectBitmap)
                            image = ReflectBitmap
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