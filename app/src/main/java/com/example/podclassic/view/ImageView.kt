package com.example.podclassic.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.podclassic.base.ScreenView
import com.example.podclassic.util.Colors
import java.io.File
import kotlin.math.max
import kotlin.math.min


open class ImageView(context: Context, val list : ArrayList<File>, var index : Int) : androidx.appcompat.widget.AppCompatImageView(context),ScreenView {
    private var currentBitmap : Bitmap? = null

    init {

        setBackgroundColor(Colors.text)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { loadImage(list[index]) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        setImageBitmap(null)
        currentBitmap?.recycle()
    }

    override fun enter(): Boolean {
        return false
    }

    override fun enterLongClick(): Boolean {
        return false
    }

    override fun slide(slideVal: Int): Boolean {
        val sv = slideVal + index
        if (sv >= list.size || sv < 0) {
            return false
        }
        index = sv
        setImageBitmap(null)
        currentBitmap?.recycle()
        loadImage(list[index])
        return true
    }

    private fun loadImage(file : File) {
        if (!file.exists()) {
            return
        }
        val opt = BitmapFactory.Options()
        opt.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.path, opt)
        val scaleW = max(width, opt.outWidth) / (min(width, opt.outWidth) * 1f)
        val scaleH = max(height, opt.outHeight) / (min(height, opt.outHeight) * 1f)
        opt.inSampleSize = max(scaleW, scaleH).toInt()
        opt.inJustDecodeBounds = false
        setImageBitmap(null)
        currentBitmap?.recycle()
        currentBitmap = BitmapFactory.decodeFile(file.path, opt)
        setImageBitmap(currentBitmap)
    }

    override fun getTitle(): String {
        return "照片"
    }

    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_NORMAL
    }
}