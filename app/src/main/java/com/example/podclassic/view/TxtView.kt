package com.example.podclassic.view

import android.content.Context
import com.example.podclassic.base.ScreenView
import com.example.podclassic.values.Colors
import com.example.podclassic.values.Values
import java.io.File

class TxtView :
    androidx.appcompat.widget.AppCompatTextView, ScreenView {

    private val title : String

    constructor(context: Context, title : String, text : String) : super(context) {
        this.text = text
        this.title = title
    }

    constructor(context: Context, file : File) : super(context) {
        this.title = file.name
        this.text = file.readText()
    }

    init {
        val padding = (Values.DEFAULT_PADDING / 2)
        setPadding(padding, 0, padding, 0)
        setTextColor(Colors.text)
        this.text = text
    }

    override fun enter(): Boolean {
        return false
    }

    override fun enterLongClick(): Boolean {
        return false
    }

    override fun slide(slideVal: Int): Boolean {
        if (!canScrollVertically(slideVal)) {
            return false
        }
        scrollY += slideVal * 24
        return true
    }

    override fun getTitle(): String {
        return title
    }
}