package com.example.podclassic.view

import android.annotation.SuppressLint
import android.content.Context
import com.example.podclassic.base.ScreenView
import com.example.podclassic.widget.ListView

@SuppressLint("ViewConstructor")
open class ItemListView(context: Context, itemList : ArrayList<Item>, private val title : String, defaultListener: OnItemClickListener?, max_size: Int) : ListView(context, max_size), ScreenView {

    constructor(context: Context, itemList: ArrayList<Item>, TITLE: String, defaultListener: OnItemClickListener?) : this(context, itemList, TITLE, defaultListener, DEFAULT_MAX_SIZE)

    init {
        defaultOnItemClickListener = defaultListener
        this.itemList = itemList
    }


    override fun enter() : Boolean {
        return onItemClick()
    }

    override fun enterLongClick() : Boolean {
        return onItemLongClick()
    }

    override fun slide(slideVal: Int): Boolean {
        return onSlide(slideVal)
    }

    override fun getTitle(): String {
        return title
    }

    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_NORMAL
    }

    override fun onStart() {
    }

    override fun onStop() {
    }

}