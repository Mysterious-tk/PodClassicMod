package com.example.podclassic.view

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.ViewGroup
import android.widget.ImageView
import com.example.podclassic.base.ScreenView
import com.example.podclassic.util.MediaUtil

class CoverFlowView(context: Context) : ScreenView, ViewGroup(context) {
    companion object {
        const val MAX_SIZE = 9
    }

    private val albums by lazy { MediaUtil.albums }

    private val imageViews = ArrayList<ImageView>(MAX_SIZE)

    private var imageHeight = 0
    private var imageWidth = 0
    private var imageBottom = 0
    private var imageCenter = 0
    private var imagePadding = 0

    init {
        for (i in 0..MAX_SIZE) {
            val imageView = ImageView(context)
            imageViews.add(imageView)
            when (i) {
                0 -> imageView.setBackgroundColor(Color.YELLOW)
                1 -> imageView.setBackgroundColor(Color.BLACK)
                2 -> imageView.setBackgroundColor(Color.BLUE)
                3 -> imageView.setBackgroundColor(Color.CYAN)
                4 -> imageView.setBackgroundColor(Color.DKGRAY)
                5 -> imageView.setBackgroundColor(Color.GRAY)
                6 -> imageView.setBackgroundColor(Color.GREEN)
                7 -> imageView.setBackgroundColor(Color.LTGRAY)
                8 -> imageView.setBackgroundColor(Color.MAGENTA)
                9 -> imageView.setBackgroundColor(Color.RED)
                10 -> imageView.setBackgroundColor(Color.CYAN)
            }
            addView(imageView)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (!changed) {
            return
        }
        for (i in imageViews.indices) {
            //0123456789
            val index = i - 5
            val imageView = imageViews[i]
            imageView.layout(imageCenter + index * imagePadding - imageWidth / 2, imageBottom - imageHeight, imageCenter + index * imagePadding + imageWidth / 2, imageBottom)
            imageView.rotationY = -((imageView.left + imageWidth / 2) - imageCenter).toFloat() / width * 100f
            Log.d("haotian_wang", "rotationY = ${imageView.rotationY}")
        }
    }

    override fun onMeasure(widthMeasureSec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSec, heightMeasureSpec)
        imageHeight = measuredHeight / 3
        imageWidth = imageHeight
        imageBottom = measuredHeight / 2 + imageHeight / 2
        imagePadding = (measuredWidth - imageWidth) / 4
        imageCenter = measuredWidth / 2
    }

    override fun enter(): Boolean {
        return false
    }

    override fun enterLongClick(): Boolean {
        return true
    }

    private var animator : ValueAnimator? = null

    @SuppressLint("ObjectAnimatorBinding")
    override fun slide(slideVal: Int): Boolean {
        if (animator?.isRunning == true) {
            return false
        }
        animator = ValueAnimator.ofInt(0, imagePadding)
        animator?.duration = 300
        var prev = 0
        animator?.addUpdateListener { animation ->
            val currentValue = animation.animatedValue as Int
            val offset = slideVal * (currentValue - prev)
            prev = currentValue

            for (i in imageViews.indices) {
                val imageView = imageViews[i]
                imageView.layout(imageView.left + offset, imageView.top, imageView.right + offset, imageView.bottom)
                when {
                    imageView.right <= imageCenter + -5 * imagePadding + imageWidth / 2 -> {
                        removeView(imageView)
                        addView(imageView)
                        val index = 5
                        imageView.layout(imageCenter + index * imagePadding - imageWidth / 2, imageBottom - imageHeight, imageCenter + index * imagePadding + imageWidth / 2, imageBottom)
                    }
                    imageView.left >= imageCenter + 5 * imagePadding - imageWidth / 2 -> {
                        removeView(imageView)
                        addView(imageView)
                        val index = -5
                        imageView.layout(imageCenter + index * imagePadding - imageWidth / 2, imageBottom - imageHeight, imageCenter + index * imagePadding + imageWidth / 2, imageBottom)
                    }
                }
                imageView.rotationY = -((imageView.left + imageWidth / 2) - imageCenter).toFloat() / width * 100f
            }
        }
        animator?.start()
        return true
    }

    override fun getTitle(): String {
        return "CoverFlow"
    }

    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_SINGLE
    }

}