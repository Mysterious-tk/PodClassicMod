package com.example.podclassic.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.LinearLayout.VERTICAL
import com.example.podclassic.util.Colors
import com.example.podclassic.util.Icons
import com.example.podclassic.util.PinyinUtil
import com.example.podclassic.util.ThreadUtil
import com.example.podclassic.util.Values.DEFAULT_PADDING
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs


open class ListView(context: Context, private val MAX_SIZE: Int) : FrameLayout(context) {

    companion object {
        const val DEFAULT_MAX_SIZE = 9
        const val DELAY = 75
        const val QUICK_SLIDE = DEFAULT_MAX_SIZE * 5
    }

    constructor(context: Context) : this(context, DEFAULT_MAX_SIZE)

    private val linearLayout = LinearLayout(context)
    private val itemViewList = ArrayList<ItemView>()

    private val scrollBar = ScrollBar(context)

    private val indexView = android.widget.TextView(context)

    init {
        linearLayout.orientation = VERTICAL
        this.addView(linearLayout)
        val scrollBarLayoutParams = LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.MATCH_PARENT)
        scrollBarLayoutParams.gravity = Gravity.RIGHT
        this.addView(scrollBar, scrollBarLayoutParams)
        for (i in 0 until MAX_SIZE) {
            val itemView = ItemView(context)
            val layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT)
            layoutParams.weight = 1f
            itemViewList.add(itemView)
            linearLayout.addView(itemView, layoutParams)
        }

        indexView.visibility = INVISIBLE
        indexView.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT)
        (indexView.layoutParams as LayoutParams).gravity = Gravity.CENTER
        indexView.setTextColor(Colors.text_light)
        indexView.setBackgroundColor(Colors.color_primary)

        indexView.setPadding(DEFAULT_PADDING * 4, DEFAULT_PADDING * 3, DEFAULT_PADDING * 4, DEFAULT_PADDING * 3)

        val gradientDrawable = GradientDrawable()
        gradientDrawable.setColor(Colors.color_primary)
        gradientDrawable.cornerRadius = 4f
        gradientDrawable.setStroke(DEFAULT_PADDING * 4, Colors.color_primary)
        indexView.background = gradientDrawable
        indexView.textSize = 20f
        indexView.gravity = Gravity.CENTER


        indexView.width = DEFAULT_PADDING * 10
        indexView.maxWidth = DEFAULT_PADDING * 10
        indexView.minWidth = DEFAULT_PADDING * 10

        addView(indexView)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshList()
    }


    protected var itemList: ArrayList<Item> = arrayListOf()
    set(list) {
        field = list
        refreshList()
    }

    fun addIfNotExist(item : Item) {
        if (!itemList.contains(item)) {
            add(item)
        }
    }

    fun add(item : Item) {
        itemList.add(item)
        refreshList()
    }

    fun remove(item : Item) {
        val result = itemList.remove(item)
        if (result) {
            if (index == itemList.size) {
                index--
            }
            if (index < 0) {
                index = 0
            }
            refreshList()
        }
    }


    fun getItem(index : Int) : Item {
        return itemList[index]
    }

    fun getCurrentItem() : Item {
        return itemList[index]
    }

    fun shake() {
        itemViewList[index - position].shake()
    }

    fun removeCurrentItem() {
        itemList.removeAt(index)
        if (index == itemList.size) {
            index --
        }
        if (index < 0) {
            index = 0
        }
        if (position > 0 && position + MAX_SIZE > itemList.size) {
            position --
        }
        refreshList()
    }

    fun refreshList() {
        if (itemList.size > MAX_SIZE && scrollBar.visibility == GONE) {
            scrollBar.visibility = View.VISIBLE
        }
        for (i in position until MAX_SIZE + position) {
            if (i >= itemList.size) {
                clearAt(i - position)
                break
            }
            val itemView = itemViewList[i - position]
            itemView.setText(itemList[i].name)
            //itemList.height = itemHeight
            //itemView.cancelShake()
            if (scrollBar.visibility == View.VISIBLE) {
                itemView.setPadding(0,0, ScrollBar.WIDTH, 0)
            }
            itemView.setHighlight(i == index)
            itemView.setRightText(itemList[i].rightText)
            itemView.setEnable(itemList[i].enable)
            onItemCreated(i, itemView)
        }
        scrollBar.setScrollBar(position, MAX_SIZE, itemList.size)
    }

    open fun refreshItem() {
        val itemView = itemViewList[index - position]
        if (itemList.size == 0) {
            itemView.clear()
        } else {
            itemView.setText(itemList[index].name)
            itemView.setRightText(itemList[index].rightText)
            itemView.setEnable(itemList[index].enable)
            onItemCreated(index, itemView)
        }
    }

    private fun clearAt(index: Int) {
        itemViewList[index].clear()
    }

    open fun onItemCreated(index : Int, itemView : ItemView) {}

    //index 为list中位置
    protected var index = 0

    //position为屏幕上第一个元素的index
    private var position = 0

    var sorted = false
    private var prevSlideTime = 0L
    private var slideCount = 0

    fun onSlide(direction: Int) : Boolean {
        if (direction == 0 || itemList.isEmpty()) {
            return false
        }

        if ((direction < 0 && index == 0) || (direction > 0 && index == itemList.size - 1)) {
            return false
        }

        if (itemList.size >= QUICK_SLIDE) {
            val currentMillis = System.currentTimeMillis()
            if (sorted && indexView.visibility == VISIBLE) {
                indexView.text = PinyinUtil.getPinyinChar(itemList[index].name[0]).toString()
            }
            when {
                (currentMillis - prevSlideTime) > DELAY -> {
                    slideCount = 0
                }
                abs(slideCount) >= MAX_SIZE * 2 -> {
                    if (direction < 0 && position == index) {
                        position -= MAX_SIZE
                        index -= MAX_SIZE
                        if (position < 0) {
                            position = 0
                            index = 0
                        }
                    } else if (direction > 0 && index == position + MAX_SIZE - 1) {
                        position += MAX_SIZE
                        index += MAX_SIZE
                        if (index >= itemList.size) {
                            index = itemList.size - 1
                        }
                        if (position >= itemList.size - MAX_SIZE + 1) {
                            position = itemList.size - MAX_SIZE
                        }
                    }
                    refreshList()
                    if (sorted) {
                        indexView.visibility = VISIBLE
                        postDelayed({
                            if ((System.currentTimeMillis() - prevSlideTime) > DELAY) {
                                indexView.visibility = INVISIBLE
                            }
                        }, DELAY * 20L)
                    }
                    prevSlideTime = currentMillis
                    return true
                }
                else -> {
                    if (direction > 0) {
                        if (slideCount < 0) {
                            slideCount = 0
                        }
                        slideCount++
                    } else {
                        if (slideCount > 0) {
                            slideCount = 0
                        }
                        slideCount--
                    }
                }
            }
            if (sorted) {
                postDelayed({
                    if ((System.currentTimeMillis() - prevSlideTime) > DELAY) {
                        indexView.visibility = INVISIBLE
                    }
                }, DELAY * 10L)
            }
            prevSlideTime = currentMillis
        }
        if (direction < 0) {
            if (index > 0) {
                if (position == index) {
                    position--
                }
                index--
            }
        } else {
            if (index < itemList.size - 1) {
                if (itemList.size > MAX_SIZE && index == position + MAX_SIZE - 1) {
                    position++
                }
                index++
            }
        }
        refreshList()
        return true
    }

    open fun onItemClick() : Boolean {
        if (index >= itemList.size) {
            return false
        }
        val result : Boolean? = if (itemList[index].onItemClickListener == null) {
            defaultOnItemClickListener?.onItemClick(index, this)
        } else {
            itemList[index].onItemClickListener?.onItemClick(index, this)
        }
        refreshItem()
        return result != null && result
    }

    open fun onItemLongClick() : Boolean {
        if (index >= itemList.size) {
            return false
        }
        val result : Boolean? = if (itemList[index].onItemClickListener == null) {
            defaultOnItemClickListener?.onItemLongClick(index, this)
        } else {
            itemList[index].onItemClickListener?.onItemLongClick(index, this)
        }
        return result != null && result
    }

    fun size() : Int {
        return itemList.size
    }

    var defaultOnItemClickListener: OnItemClickListener? = null

    interface OnItemClickListener {
        fun onItemClick(index : Int, listView : ListView) : Boolean
        fun onItemLongClick(index : Int, listView : ListView) : Boolean { return false }
    }

    class Item {
        var name : String = ""
        var onItemClickListener : OnItemClickListener? = null
        var enable : Boolean = false
        var rightText = ""

        constructor(name: String, onItemClickListener: OnItemClickListener?, enable: Boolean) {
            this.name = name
            this.onItemClickListener = onItemClickListener
            this.enable = enable
        }

        constructor(name: String, onItemClickListener: OnItemClickListener?, rightText : String) {
            this.name = name
            this.onItemClickListener = onItemClickListener
            this.rightText = rightText
        }
    }

    private class ScrollBar(context: Context) : ViewGroup(context) {
        private var position = 0
        private var maxSize = 0
        var size = 0

        companion object {
            const val WIDTH = 24
            const val LINE_WIDTH = 1
        }

        private val paint by lazy { Paint() }

        fun setScrollBar(position : Int, maxSize : Int, size : Int) {
            this.position = position
            this.maxSize = maxSize
            this.size = size
            //invalidate()
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            if (size > maxSize) {
                visibility = VISIBLE
                var barHeight = measuredHeight * maxSize / size
                if (barHeight == 0) {
                    barHeight = 1
                }
                val barTop = measuredHeight * position / size
                bar.layout(LINE_WIDTH, barTop, measuredWidth, barTop + barHeight)
            } else {
                visibility = GONE
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            setMeasuredDimension(WIDTH, MeasureSpec.getSize(heightMeasureSpec))
        }

        override fun onDraw(canvas: Canvas?) {
            super.onDraw(canvas)
            paint.shader = Colors.getShader(0f, height / 2f, width.toFloat(), height / 2f, Colors.background_dark_1, Colors.background_dark_2)
            canvas?.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
            paint.color = Colors.line
            canvas?.drawRect(0f, 0f, LINE_WIDTH.toFloat(), measuredHeight.toFloat(), paint)
        }

        private var bar = View(context)

        init {
            visibility = GONE
            setBackgroundColor(Colors.background)
            bar.setBackgroundColor(Colors.main)
            addView(bar)
        }
    }

    class ItemView(context: Context) : FrameLayout(context) {

        private var highlight = false
        private var enable = false
        private var playing = false

        private val rightText = TextView(context)
        private val leftText = TextView(context)

        init {
            clear()

            val layoutParams1 = LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT)
            val layoutParams2 = LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.MATCH_PARENT)

            layoutParams2.gravity = Gravity.RIGHT

            addView(leftText, layoutParams1)
            addView(rightText, layoutParams2)
        }

        fun setHighlight(highlight: Boolean) {
            if (this.highlight == highlight) {
                return
            }
            this.highlight = highlight
            leftText.scrollable = highlight
            setIcons()
        }

        fun setEnable(enable: Boolean) {
            if (this.enable == enable) {
                return
            }
            this.enable = enable
            setIcons()
        }

        fun setPlaying(playing: Boolean) {
            if (this.playing == playing) {
                return
            }
            this.playing = playing
            setIcons()
        }

        fun setText(text : String) {
            leftText.text = text
        }

        fun setRightText(text : String) {
            rightText.text = text
        }

        private var hasRightIcon = false
        private fun setRightIcon(drawable: Drawable?) {
            rightText.setRightIcon(drawable)
            hasRightIcon = (drawable != null)
        }

        private fun setIcons() {
            if (highlight) {
                setBackgroundColor(Colors.main)
                leftText.setTextColor(Colors.text_light)
                rightText.setTextColor(Colors.text_light)
                when {
                    enable -> setRightIcon(Icons.ARROW_WHITE.drawable)
                    playing -> setRightIcon(Icons.PLAYING_WHITE.drawable)
                    else -> setRightIcon(null)
                }
            } else {
                background = null
                leftText.setTextColor(Colors.text)
                rightText.setTextColor(Colors.text)
                when {
                    enable -> setRightIcon(Icons.ARROW_BLACK.drawable)
                    playing -> setRightIcon(Icons.PLAYING_BLACK.drawable)
                    else -> setRightIcon(null)
                }
            }
            leftText.paddingRight = if (hasRightIcon) DEFAULT_PADDING * 4 else DEFAULT_PADDING
        }

        private var shakeCount = 0
        private var shakeTimer : Timer? = null
        fun shake() {
            if (shakeTimer == null) {
                shakeTimer = Timer()
                shakeTimer?.schedule(object : TimerTask() {
                    override fun run() {
                        shakeCount ++
                        ThreadUtil.runOnUiThread(Runnable { setHighlight(!highlight) })
                        if (shakeCount == 4) {
                            cancelShake()
                        }
                    }
                }, 100, 100)
            }
        }

        private fun cancelShake() {
            shakeTimer?.cancel()
            shakeTimer = null
            shakeCount = 0
        }

        fun clear() {
            cancelShake()

            leftText.clear()
            rightText.clear()

            highlight = false
            enable = false
            playing = false

            setPadding(0,0,0,0)

            setIcons()
        }
    }
}