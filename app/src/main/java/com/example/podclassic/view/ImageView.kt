package com.example.podclassic.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.podclassic.base.ScreenView
import com.example.podclassic.values.Colors
import com.example.podclassic.values.Strings
import java.io.File
import kotlin.math.max
import kotlin.math.min


@SuppressLint("ViewConstructor")
class ImageView(context: Context, val list: ArrayList<File>, var index: Int) :
    androidx.appcompat.widget.AppCompatImageView(context), ScreenView {
    private var currentBitmap: Bitmap? = null

    init {
        setBackgroundColor(Colors.text)
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
        loadImage(list[index])
        return true
    }

    private fun loadImage(file: File) {
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
        //setImageBitmap(null)
        currentBitmap?.recycle()
        currentBitmap = BitmapFactory.decodeFile(file.path, opt)
        setImageBitmap(currentBitmap)
    }

    override fun getTitle(): String {
        return Strings.PHOTO
    }

    override fun onViewCreate() {
        post { loadImage(list[index]) }
    }

    override fun onViewDelete() {
        setImageBitmap(null)
        currentBitmap?.recycle()
    }
}