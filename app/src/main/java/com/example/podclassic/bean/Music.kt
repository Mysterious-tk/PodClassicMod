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
        val width = bitmap.width
        val height = bitmap.height

        // Player artwork only has room for a short reflection. Keeping the generated
        // bitmap close to the view's aspect ratio also prevents centerCrop from cutting
        // off the transparent tail and turning it into a visible hard edge.
        val reflectionHeight = (height * 0.25f).toInt().coerceAtLeast(1)
        val reflectionGap = (height * 0.005f).toInt().coerceAtLeast(1)
        val matrix = Matrix()
        matrix.preScale(1f, -1f)
        val sharpReflection = Bitmap.createBitmap(
            bitmap,
            0,
            height - reflectionHeight,
            width,
            reflectionHeight,
            matrix,
            true
        )

        // A small bilinear down/up sample takes the digital sharpness out of the
        // reflection without relying on RenderEffect (the app still supports API 26).
        val softenedWidth = (width / 2).coerceAtLeast(1)
        val softenedHeight = (reflectionHeight / 2).coerceAtLeast(1)
        val softenedSmall = Bitmap.createScaledBitmap(
            sharpReflection,
            softenedWidth,
            softenedHeight,
            true
        )
        val reflectionImage = Bitmap.createScaledBitmap(
            softenedSmall,
            width,
            reflectionHeight,
            true
        )
        val bitmap4Reflection =
            Bitmap.createBitmap(
                width,
                height + reflectionHeight + reflectionGap,
                Bitmap.Config.ARGB_8888
            )
        val canvasRef = Canvas(bitmap4Reflection)

        canvasRef.drawBitmap(bitmap, 0f, 0f, null)
        canvasRef.drawBitmap(reflectionImage, 0f, height + reflectionGap.toFloat(), null)

        // Fade in over the first few pixels to soften the join, then fall off faster
        // than a linear gradient so the bottom dissolves naturally into the glass.
        val reflectionTop = height + reflectionGap.toFloat()
        val reflectionBottom = bitmap4Reflection.height.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val shader = LinearGradient(
            0f,
            reflectionTop,
            0f,
            reflectionBottom,
            intArrayOf(
                0x00ffffff,
                0x58ffffff,
                0x3dffffff,
                0x18ffffff,
                0x00ffffff
            ),
            floatArrayOf(0f, 0.035f, 0.16f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = shader
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)

        canvasRef.drawRect(
            0f,
            reflectionTop,
            width.toFloat(),
            reflectionBottom,
            paint
        )

        // A restrained specular seam visually ties the reflection to the surrounding
        // liquid-glass surface instead of leaving a blunt mirrored boundary.
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = LinearGradient(
                0f,
                height.toFloat(),
                width.toFloat(),
                height.toFloat(),
                intArrayOf(0x00ffffff, 0x36ffffff, 0x12ffffff, 0x00ffffff),
                floatArrayOf(0f, 0.24f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvasRef.drawRect(
            0f,
            height.toFloat(),
            width.toFloat(),
            height + reflectionGap.toFloat(),
            highlightPaint
        )

        sharpReflection.recycle()
        if (softenedSmall !== sharpReflection) softenedSmall.recycle()
        reflectionImage.recycle()
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
