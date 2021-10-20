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
        isSingleLine = true
        maxLines = MAX_LINES
        ellipsize = TextUtils.TruncateAt.END
        typeface = Typeface.defaultFromStyle(Typeface.BOLD)
        textSize = TEXT_SIZE

        setTextColor(Colors.text)
        setPadding(DEFAULT_PADDING, DEFAULT_PADDING / 4, DEFAULT_PADDING, DEFAULT_PADDING / 4)
        gravity = Gravity.CENTER_VERTICAL
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
        super.setText(null)
        setCompoundDrawables(null, null, null, null)
        setPadding(DEFAULT_PADDING, DEFAULT_PADDING / 4, DEFAULT_PADDING, DEFAULT_PADDING / 4)
    }

    private var buffer : String? = null
    fun setBufferedText(text : String?) {
        if (text == buffer) {
            return
        }
        buffer = text
        setText(buffer)
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