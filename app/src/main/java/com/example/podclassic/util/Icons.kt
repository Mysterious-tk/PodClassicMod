package com.example.podclassic.util

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import com.example.podclassic.R
import com.example.podclassic.base.BaseApplication.Companion.context


@SuppressLint("UseCompatLoadingForDrawables")
object Icons {

    val MENU by lazy { Icon(context.getDrawable(R.drawable.ic_menu_white_24dp)!!) }
    val PREV by lazy { Icon(context.getDrawable(R.drawable.ic_skip_previous_white_24dp)!!) }
    val NEXT by lazy { Icon(context.getDrawable(R.drawable.ic_skip_next_white_24dp)!!) }
    val PAUSE by lazy { Icon(context.getDrawable(R.drawable.ic_pause_white_24dp)!!) }
    val ARROW_WHITE by lazy { Icon(context.getDrawable(R.drawable.ic_keyboard_arrow_right_white_24dp)!!) }
    val ARROW_BLACK by lazy { Icon(context.getDrawable(R.drawable.ic_keyboard_arrow_right_grey_600_24dp)!!) }
    val PLAY_BLUE by lazy { Icon(context.getDrawable(R.drawable.ic_play_arrow_blue_500_18dp)!!) }
    val PAUSE_BLUE by lazy { Icon(context.getDrawable(R.drawable.ic_pause_blue_500_18dp)!!) }
    val PLAY_MODE_SHUFFLE by lazy { Icon(context.getDrawable(R.drawable.ic_shuffle_grey_600_18dp)!!) }
    val PLAY_MODE_SINGLE by lazy { Icon(context.getDrawable(R.drawable.ic_repeat_one_grey_600_18dp)!!) }
    val PLAY_MODE_REPEAT by lazy { Icon(context.getDrawable(R.drawable.ic_repeat_grey_600_18dp)!!) }
    val PLAYING_WHITE by lazy { Icon(context.getDrawable(R.drawable.ic_volume_up_white_18dp)!!) }
    val PLAYING_BLACK by lazy { Icon(context.getDrawable(R.drawable.ic_volume_up_grey_600_18dp)!!) }
    val VOLUME_DOWN by lazy { Icon(context.getDrawable(R.drawable.ic_volume_down_grey_600_18dp)!!) }
    val VOLUME_UP by lazy { Icon(context.getDrawable(R.drawable.ic_volume_up_grey_600_18dp)!!) }
    val STOP_TIME by lazy { Icon(context.getDrawable(R.drawable.ic_access_time_grey_600_18dp)!!) }
    val DEFAULT by lazy {Icon(context.getDrawable(R.drawable.ic_default)!!)}

    val EMPTY: Bitmap = Bitmap.createBitmap(Values.IMAGE_WIDTH,Values.IMAGE_WIDTH,Bitmap.Config.ALPHA_8).apply {
        eraseColor(Color.TRANSPARENT)
    }

    fun getBitmap(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            if (drawable.opacity != PixelFormat.OPAQUE) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        )
        val canvas = Canvas(bitmap)
        //drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        drawable.draw(canvas)
        return bitmap
    }

    class Icon(val drawable: Drawable) {
        init {
            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        }
        val bitmap by lazy { getBitmap(drawable) }
        val height = bitmap.height
        val width = bitmap.width
    }
}