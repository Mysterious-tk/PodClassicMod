package com.example.podclassic.view

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.widget.FrameLayout
import com.example.podclassic.`object`.Core
import com.example.podclassic.`object`.MusicList
import com.example.podclassic.base.ScreenView
import com.example.podclassic.util.Icons
import com.example.podclassic.util.MediaUtil
import com.example.podclassic.util.ThreadUtil
import com.example.podclassic.widget.TextView
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.pow

class CoverFlowView(context: Context) : ScreenView, FrameLayout(context) {
    companion object {
        private const val MAX_SIZE = 9
        private const val CENTER_OFFSET = 5

        private val threadPoolExecutor = ThreadPoolExecutor(10, 10, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue<Runnable>())
    }

    private val albums by lazy {
        MediaUtil.albums
    }

    private val imageViews = ArrayList<ImageView>(MAX_SIZE)

    private val album = TextView(context)
    private val artist = TextView(context)

    private var imageHeight = 0
    private var imageWidth = 0
    private var imageBottom = 0
    private var imageCenter = 0
    private var imagePadding = 0
    private var textHeight = 0

    private var index = -CENTER_OFFSET

    init {
        for (i in 0..MAX_SIZE) {
            val imageView = ImageView(context)
            imageViews.add(imageView)
            addView(imageView)
        }
        album.gravity = Gravity.CENTER_HORIZONTAL
        artist.gravity = Gravity.CENTER_HORIZONTAL
        addView(album)
        addView(artist)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (!changed) {
            return
        }
        for (i in imageViews.indices) {
            val index = i - CENTER_OFFSET
            val imageView = imageViews[i]
            if (index >= 0) {
                imageView.bindItem(albums[index])
            }
            imageView.layout(imageCenter + index * imagePadding - imageWidth / 2, imageBottom - imageHeight, imageCenter + index * imagePadding + imageWidth / 2, imageBottom)
            setRotationY(imageView)
            if (imageView.rotationY == 0f) {
                setTexts(index)
            }
        }
        artist.layout(0, bottom - textHeight, width, bottom)
        album.layout(0, artist.top - textHeight, width, artist.top)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        threadPoolExecutor.queue.clear()
    }
    private fun setTexts(index : Int) {
        album.setBufferedText(albums[index].name)
        artist.setBufferedText(albums[index].artist)
    }

    private fun setRotationY(imageView: ImageView) {
        val temp = imageCenter - imageView.left - imageWidth / 2f
        if (temp == 0f) {
            imageView.rotationY = 0f
        } else {
            val x = temp / width.toDouble()
            imageView.rotationY = sgn(x) * (1 / (1 + Math.E.pow( - 16 * abs(x)).toFloat()) - 0.5f) * 81f
        }
        imageView.z = -abs(temp)
    }

    private fun sgn(x : Double) : Int {
        return when {
            x < 0 -> -1
            x > 0 -> 1
            else -> 0
        }
    }


    override fun onMeasure(widthMeasureSec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSec, heightMeasureSpec)

        imageHeight = measuredHeight / 3 * 2
        imageWidth = imageHeight
        imageBottom = measuredHeight / 2 + imageHeight / 2
        imagePadding = (measuredWidth - imageWidth) / 4
        imageCenter = measuredWidth / 2
        textHeight = measuredHeight / 10
    }

    override fun enter(): Boolean {
        Core.addView(MusicListView(context, albums[index + CENTER_OFFSET], MusicListView.LONG_CLICK_SET_LOVE))
        return true
    }

    override fun enterLongClick(): Boolean {
        return false
    }


    private var animator : ValueAnimator? = null

    @SuppressLint("ObjectAnimatorBinding")
    override fun slide(slideVal: Int): Boolean {
        if (animator?.isRunning == true) {
            if (abs(animator!!.animatedValue as Int) < imagePadding / 16) {
                animator?.duration = 100
            }
            return false
        }

        if ((index == -CENTER_OFFSET && slideVal < 0) || (index + CENTER_OFFSET == albums.size - 1 && slideVal > 0)) {
            return false
        }
        index += slideVal
        animator = ValueAnimator.ofInt(0, - slideVal * imagePadding)
        animator?.duration = 300
        var prev = 0
        animator?.addUpdateListener { animation ->
            val currentValue = animation.animatedValue as Int
            val offset = currentValue - prev
            prev = currentValue

            for (i in imageViews.indices) {
                val imageView = imageViews[i]

                imageView.layout(imageView.left + offset, imageView.top, imageView.right + offset, imageView.bottom)
                when {
                    imageView.right < imageCenter - CENTER_OFFSET * imagePadding + imageWidth / 2 -> {
                        imageView.layout(imageCenter + CENTER_OFFSET * imagePadding - imageWidth / 2 + offset, imageBottom - imageHeight, imageCenter + CENTER_OFFSET * imagePadding + imageWidth / 2 + offset, imageBottom)
                        val tempIndex = index + MAX_SIZE
                        if (tempIndex < albums.size && tempIndex >= 0) {
                            imageView.bindItem(albums[tempIndex])
                        } else {
                            imageView.bindItem(null)
                        }
                    }
                    imageView.left > imageCenter + CENTER_OFFSET * imagePadding - imageWidth / 2 -> {
                        imageView.layout(imageCenter - CENTER_OFFSET * imagePadding - imageWidth / 2 + offset, imageBottom - imageHeight, imageCenter + - CENTER_OFFSET * imagePadding + imageWidth / 2 + offset, imageBottom)
                        val tempIndex = index + 1
                        if (tempIndex < albums.size && tempIndex >= 0) {
                            imageView.bindItem(albums[tempIndex])
                        } else {
                            imageView.bindItem(null)
                        }
                    }
                }
                setRotationY(imageView)
                if (abs(imageView.rotationY) <= 5f) {
                    setTexts(index + CENTER_OFFSET)
                }
            }
        }
        animator?.start()
        return true
    }

    override fun getTitle(): String {
        return "CoverFlow"
    }

    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_NORMAL
    }

    private class ImageView(context: Context) : androidx.appcompat.widget.AppCompatImageView(context) {

        private var bitmap : Bitmap? = null
        private var album : MusicList? = null
        private val defaultBitmap by lazy { getReflectBitmap(Icons.DEFAULT.bitmap) }
        private var runnable : Runnable? = null

        fun bindItem(album: MusicList?) {
            if (album == this.album) {
                return
            }
            if (bitmap?.isRecycled == false) {
                bitmap!!.recycle()
            }
            if (album == null) {
                setImageBitmap(null)
            } else {
                setImageBitmap(defaultBitmap)
            }
            if (album != null) {
                if (runnable != null) {
                    threadPoolExecutor.remove(runnable)
                }
                runnable = Runnable {
                    bitmap = album.image
                    if (bitmap != null) {
                        val temp = getReflectBitmap(bitmap!!)
                        ThreadUtil.runOnUiThread(Runnable { setImageBitmap(temp) })
                    }
                }
                threadPoolExecutor.execute(runnable)
            }
        }

        private fun getReflectBitmap(bitmap: Bitmap) : Bitmap {
            val reflectionGap = 0
            val width = bitmap.width
            val height = bitmap.height
            val matrix = Matrix()
            matrix.preScale(1f, -1f)
            val reflectionImage = Bitmap.createBitmap(bitmap, 0, height / 2, width, height / 2, matrix, false)
            val bitmap4Reflection = Bitmap.createBitmap(width, height + height / 2, Bitmap.Config.ARGB_8888)
            val canvasRef = Canvas(bitmap4Reflection)
            val defaultPaint = Paint()
            defaultPaint.isAntiAlias = true
            canvasRef.drawBitmap(bitmap, 0f, 0f, null)
            canvasRef.drawRect(0f, height.toFloat(), width.toFloat(), height + reflectionGap.toFloat(), defaultPaint)
            canvasRef.drawBitmap(reflectionImage, 0f, height + reflectionGap.toFloat(), null)
            val paint = Paint()
            val shader = LinearGradient(0f, bitmap.height.toFloat(), 0f, bitmap4Reflection.height.toFloat() + reflectionGap, 0x70ffffff, 0x00ffffff, Shader.TileMode.CLAMP)
            paint.shader = shader
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvasRef.drawRect(0f, height.toFloat(), width.toFloat(), (bitmap4Reflection.height + reflectionGap).toFloat(), paint)
            return bitmap4Reflection
        }
    }
}