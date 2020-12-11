package com.example.podclassic.widget

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import com.example.podclassic.util.Colors
import com.example.podclassic.util.Values.DEFAULT_PADDING


class TextView(context: Context) : androidx.appcompat.widget.AppCompatTextView(context) {

    companion object {
        const val MAX_LINES = 1
        const val TEXT_SIZE = 16f
    }

    init {
        marqueeRepeatLimit = -1
        setSingleLine(true)
        maxLines = MAX_LINES
        ellipsize = TextUtils.TruncateAt.END
        typeface = Typeface.defaultFromStyle(Typeface.BOLD)
        textSize = TEXT_SIZE
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(Colors.text)
        setPadding(DEFAULT_PADDING, DEFAULT_PADDING / 4, DEFAULT_PADDING, DEFAULT_PADDING / 4)
        gravity = Gravity.CENTER_VERTICAL
    }

    private var bufferedText : String? = null

    fun setBufferedText(text : String?) {
        if (text == bufferedText) {
            return
        }
        super.setText(text)
        bufferedText = text
    }

    fun setLeftIcon(drawable : Drawable?) {
        setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
    }

    fun setRightIcon(drawable: Drawable?) {
        setCompoundDrawables(null, null, drawable, null)
    }

    fun setPaddingRight(padding : Int) {
        setPadding(DEFAULT_PADDING, DEFAULT_PADDING / 4, padding, DEFAULT_PADDING / 4)
    }

    fun clear() {
        super.setText("")
        setCompoundDrawables(null, null, null, null)
        setPadding(DEFAULT_PADDING, DEFAULT_PADDING / 4, DEFAULT_PADDING, DEFAULT_PADDING / 4)
    }

    var scrollable = false
    set(value) {
        if (value == field) {
            return
        }
        ellipsize = if (value) {
            TextUtils.TruncateAt.MARQUEE
        } else {
            TextUtils.TruncateAt.END
        }
        field = value
    }

    override fun isFocused(): Boolean {
        return scrollable
    }
}