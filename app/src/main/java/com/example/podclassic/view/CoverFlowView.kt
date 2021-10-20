package com.example.podclassic.view

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import com.example.podclassic.`object`.Core
import com.example.podclassic.`object`.MusicList
import com.example.podclassic.base.ScreenView
import com.example.podclassic.util.Icons
import com.example.podclassic.util.MediaStoreUtil
import com.example.podclassic.util.ThreadUtil
import com.example.podclassic.widget.TextView
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.*

class CoverFlowView(context: Context) : ScreenView, FrameLayout(context) {
    companion object {
        private const val MAX_SIZE = 11
        private const val CENTER_OFFSET = 6
        private const val DEFAULT_DURATION = 200L
        private const val MIN_DURATION = 10L

        private val threadPoolExecutor = ThreadPoolExecutor(10, 10, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue())
    }

    private val albums by lazy {
        MediaStoreUtil.albums
    }

    private val imageViews = ArrayList<ImageView>(MAX_SIZE)

    private val album = TextView(context)
    private val artist = TextView(context)

    private var imageWidth = 0
    private var halfImageWidth = 0
    private var imageBottom = 0
    private var imageCenter = 0
    private var imagePadding = 0
    private var textHeight = 0
    private var centerY = 0

    private var index = -CENTER_OFFSET

    init {
        for (i in 0 until MAX_SIZE) {
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
            val index = i + this.index
            val imageView = imageViews[i]
            if (index in 0 until albums.size) {
                imageView.bindItem(albums[index])
            }
            scaleImageView(imageCenter + (i - CENTER_OFFSET) * imagePadding, imageView)
            setRotationY(imageView)

        }
        setTexts(index + CENTER_OFFSET)
        artist.layout(0, bottom - textHeight, width, bottom)
        album.layout(0, artist.top - textHeight, width, artist.top)
    }

    private fun setTexts(index : Int) {
        album.text = albums[index].name
        artist.text = albums[index].artist
    }

    private fun setRotationY(imageView: ImageView) {
        val temp = (imageCenter - imageView.centerX()).toFloat()
        if (temp == 0f) {
            imageView.rotationY = 0f
        } else {
            val x = temp / width.toFloat()
            imageView.rotationY = sgn(x) * (1f / (1f + 3f.pow( - 18f * abs(x))) - 0.5f) * 81f
        }
        imageView.z = -abs(temp)
    }

    private fun scaleImageView(centerX : Int, imageView: ImageView) {
        val scale = - abs(imageCenter - centerX) / 4
        imageView.layout(centerX - halfImageWidth - scale, centerY - halfImageWidth - scale, centerX + halfImageWidth + scale, centerY + halfImageWidth + scale)
    }

    private fun sgn(x : Float) : Float {
        return when {
            x < 0f -> -1f
            x > 0f -> 1f
            else -> 0f
        }
    }

    private fun s(x: Float) : Float {
        return sqrt(x) / sqrt(x + 1)
    }


    override fun onMeasure(widthMeasureSec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSec, heightMeasureSpec)

        imageWidth = measuredHeight / 4 * 3
        halfImageWidth = imageWidth / 2
        imageBottom = measuredHeight / 2 + halfImageWidth
        imagePadding = (measuredWidth - imageWidth) / 4
        imageCenter = measuredWidth / 2
        centerY = imageBottom - halfImageWidth
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
    private var duration = DEFAULT_DURATION

    private var slides = 0

    override fun slide(slideVal: Int) : Boolean {
        if (animator?.isRunning == true) {
            duration = (duration * (0.9f + 0.1f * (1f - s(duration.toFloat())))).toLong()
            duration = max(duration, MIN_DURATION)
            animator?.duration = duration

            if (slides * slideVal < 0) {
                slides = 0
            } else {
                slides += slideVal
            }
            return false
        }
        loadAnimation(slideVal)
        return false
    }

    private val animatorListener = object : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator?) {}

        override fun onAnimationEnd(animation: Animator?) {
            if (slides == 0) {
                duration = DEFAULT_DURATION
                animator = null
                return
            }
            val sgn = if (slides > 0) 1 else -1
            slides -= sgn

            if (slides <= 12) {
                duration = (duration * (0.98f + 0.08f * (s(abs(slides).toFloat())))).toLong()
                duration = min(duration, DEFAULT_DURATION)
            } else {
                duration = (duration * (0.81f + 0.12f * (1f - s(duration.toFloat())))).toLong()
                duration = max(duration, MIN_DURATION)
            }
            loadAnimation(sgn)

        }

        override fun onAnimationCancel(animation: Animator?) {}
        override fun onAnimationRepeat(animation: Animator?) {}
    }

    private val interpolator = LinearInterpolator()
    private val startValues = Array(MAX_SIZE) {0}

    private fun loadAnimation(slideVal: Int) {
        if ((index == -CENTER_OFFSET && slideVal < 0) || (index + CENTER_OFFSET == albums.size - 1 && slideVal > 0)) {
            duration = DEFAULT_DURATION
            slides = 0
            return
        }

        index += slideVal

        animator = ValueAnimator.ofInt(0, - slideVal * imagePadding).apply {
            addListener(animatorListener)
            interpolator = this@CoverFlowView.interpolator
            duration = this@CoverFlowView.duration
        }
        for (i in imageViews.indices) {
            startValues[i] = imageViews[i].centerX()
        }
        animator?.addUpdateListener { animation ->
            val currentValue = animation.animatedValue as Int

            var tempImageView : ImageView? = null
            for (i in imageViews.indices) {
                val imageView = imageViews[i]

                scaleImageView(startValues[i] + currentValue, imageView)
                //imageView.layout(left, imageView.top, right, imageView.bottom)
                setRotationY(imageView)

                if (slideVal > 0) {
                    if (tempImageView == null || tempImageView.centerX() > imageView.centerX()) {
                        tempImageView = imageView
                    }
                } else {
                    if (tempImageView == null || tempImageView.centerX() < imageView.centerX()) {
                        tempImageView = imageView
                    }
                }

            }
            if (slideVal > 0) {
                scaleImageView(imageCenter + (CENTER_OFFSET - 1) * imagePadding, tempImageView!!)
                val tempIndex = index + MAX_SIZE

                if (tempIndex in 0 until albums.size) {
                    tempImageView.bindItem(albums[tempIndex])
                } else {
                    tempImageView.bindItem(null)
                }
            } else {
                val tempIndex = index + 1

                scaleImageView(imageCenter - (CENTER_OFFSET - 1) * imagePadding, tempImageView!!)

                if (tempIndex in 0 until albums.size) {
                    tempImageView.bindItem(albums[tempIndex])
                } else {
                    tempImageView.bindItem(null)
                }
            }
            setRotationY(tempImageView)
        }

        setTexts(index + CENTER_OFFSET)

        animator?.start()
    }

    override fun getTitle(): String {
        return "CoverFlow"
    }

    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_NORMAL
    }

    override fun onStart() {

    }

    override fun onStop() {
        threadPoolExecutor.queue.clear()
        animator?.cancel()
    }

    private class ImageView(context: Context) : androidx.appcompat.widget.AppCompatImageView(context) {

        private var album : MusicList? = null
        private val defaultBitmap by lazy { getReflectBitmap(Icons.DEFAULT.bitmap) }
        private var runnable : Runnable? = null

        fun centerX() : Int {
            return (left + right) / 2
        }


        fun bindItem(album: MusicList?) {
            if (album != null && album == this.album) {
                return
            }
            this.album = album

            if (album == null) {
                setImageBitmap(null)
                return
            } else {
                setImageBitmap(defaultBitmap)
            }
            if (runnable != null) {
                threadPoolExecutor.remove(runnable)
            }
            runnable = Runnable {
                val bitmap = MediaStoreUtil.getAlbumImage(album.id)
                if (Thread.currentThread().isInterrupted) {
                    return@Runnable
                }
                if (bitmap != null) {
                    val temp = getReflectBitmap(bitmap)
                    ThreadUtil.runOnUiThread(Runnable { setImageBitmap(temp) })
                }
            }
            threadPoolExecutor.execute(runnable)
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