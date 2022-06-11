package com.example.podclassic.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.VERTICAL
import com.example.podclassic.util.PinyinUtil
import com.example.podclassic.util.ThreadUtil
import com.example.podclassic.values.Colors
import com.example.podclassic.values.Icons
import com.example.podclassic.values.Values
import com.example.podclassic.values.Values.DEFAULT_PADDING
import java.util.*
import kotlin.math.abs


open class ListView(context: Context, private val MAX_SIZE: Int) : FrameLayout(context) {

    companion object {
        const val DEFAULT_MAX_SIZE = 9
        const val DELAY = 75
        const val QUICK_SLIDE = DEFAULT_MAX_SIZE * 5
    }

    constructor(context: Context) : this(context, DEFAULT_MAX_SIZE)

    private val linearLayout = LinearLayout(context)
    private val itemViewList = ArrayList<ItemView>(DEFAULT_MAX_SIZE)

    private val scrollBar = ScrollBar(context)

    private val indexView = android.widget.TextView(context)

    init {
        linearLayout.orientation = VERTICAL
        this.addView(linearLayout)
        val scrollBarLayoutParams =
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        scrollBarLayoutParams.gravity = Gravity.END
        this.addView(scrollBar, scrollBarLayoutParams)
        for (i in 0 until MAX_SIZE) {
            val itemView = ItemView(context)
            val layoutParams =
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            layoutParams.weight = 1f
            itemViewList.add(itemView)
            linearLayout.addView(itemView, layoutParams)
        }

        indexView.visibility = INVISIBLE
        val layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        layoutParams.gravity = Gravity.CENTER
        indexView.layoutParams = layoutParams

        indexView.setTextColor(Colors.text_light)
        indexView.setBackgroundColor(Colors.color_primary)

        indexView.setPadding(
            DEFAULT_PADDING * 4,
            DEFAULT_PADDING * 3,
            DEFAULT_PADDING * 4,
            DEFAULT_PADDING * 3
        )

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

    fun addIfNotExist(item: Item) {
        addIfNotExist(item, size())
    }

    fun addIfNotExist(item: Item, index: Int) {
        if (!itemList.contains(item)) {
            add(item, index)
        }
    }

    fun add(item: Item, index: Int) {
        itemList.add(index, item)
        refreshList()
    }

    fun add(item: Item) {
        add(item, size())
    }

    fun remove(item: Item) {
        if (itemList.remove(item)) {
            if (index == itemList.size) {
                index--
            }
            if (index < 0) {
                index = 0
            }
            refreshList()
        }
    }


    fun getItem(index: Int): Item {
        return itemList[index]
    }

    fun getCurrentItem(): Item {
        return getItem(index)
    }

    fun setCurrent(index: Int) {
        this.index = index
        this.position =
            (index - MAX_SIZE / 2).coerceAtMost(itemList.size - MAX_SIZE).coerceAtLeast(0)
        refreshList()
    }

    fun shake() {
        itemViewList[index - position].shake()
    }

    fun removeCurrentItem() {
        itemList.removeAt(index)
        if (index == itemList.size) {
            index--
        }
        if (index < 0) {
            index = 0
        }
        if (position > 0 && position + MAX_SIZE > itemList.size) {
            position--
        }
        refreshList()
    }

    fun refreshList() {
        if (itemList.size > MAX_SIZE && scrollBar.visibility == GONE) {
            scrollBar.visibility = View.VISIBLE
        }
        scrollBar.setScrollBar(position, MAX_SIZE, itemList.size)
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
                itemView.setPadding(0, 0, DEFAULT_PADDING, 0)
            }
            itemView.setHighlight(i == index)
            itemView.setRightText(itemList[i].rightText)
            itemView.setEnable(itemList[i].enable)
            onItemCreated(i, itemView)

        }
    }

    open fun refreshItem() {
        val itemView = itemViewList[index - position]
        if (itemList.isEmpty()) {
            itemView.clear()
        } else {
            val item = itemList[index]
            itemView.setText(item.name)
            itemView.setRightText(item.rightText)
            itemView.setEnable(item.enable)
            onItemCreated(index, itemView)
        }
    }

    private fun clearAt(index: Int) {
        itemViewList[index].clear()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            refreshList()
        }
    }

    open fun onItemCreated(index: Int, itemView: ItemView) {}

    //index 为list中位置
    protected var index = 0

    //position为屏幕上第一个元素的index
    private var position = 0

    var sorted = false
    private var prevSlideTime = 0L
    private var slideCount = 0

    private var timer: Timer? = null

    fun onSlide(direction: Int): Boolean {
        if (direction == 0 || itemList.isEmpty()) {
            return false
        }

        if ((direction < 0 && index == 0) || (direction > 0 && index == itemList.size - 1)) {
            return false
        }

        if (itemList.size >= QUICK_SLIDE) {
            val currentMillis = System.currentTimeMillis()
            if (sorted && indexView.visibility == VISIBLE) {
                indexView.text =
                    PinyinUtil.getPinyinChar(itemList[index].name[0])
                        .toString()
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
                        if (timer == null) {
                            timer = Timer()
                            timer!!.schedule(object : TimerTask() {
                                override fun run() {
                                    if (indexView.visibility == View.INVISIBLE) {
                                        cancelTimer()
                                    } else if ((System.currentTimeMillis() - prevSlideTime) > DELAY) {
                                        cancelTimer()
                                        ThreadUtil.runOnUiThread {
                                            indexView.visibility = INVISIBLE
                                        }
                                    }
                                }
                            }, DELAY * 6L, DELAY * 12L)
                        }
                    }
                    prevSlideTime = currentMillis
                    return true
                }
                else -> {
                    if (direction > 0) {
                        if (slideCount < 0) {
                            slideCount = 1
                        } else {
                            slideCount++
                        }
                    } else {
                        if (slideCount > 0) {
                            slideCount = -1
                        } else {
                            slideCount--
                        }
                    }
                }
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
            //direction > 0
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

    open fun onItemClick(): Boolean {
        if (index >= itemList.size) {
            return false
        }
        val result: Boolean? = if (itemList[index].onItemClickListener == null) {
            defaultOnItemClickListener?.onItemClick(index, this)
        } else {
            itemList[index].onItemClickListener?.onItemClick(index, this)
        }
        refreshItem()
        return result != null && result
    }

    open fun onItemLongClick(): Boolean {
        if (index >= itemList.size) {
            return false
        }
        val result: Boolean? = if (itemList[index].onItemClickListener == null) {
            defaultOnItemClickListener?.onItemLongClick(index, this)
        } else {
            itemList[index].onItemClickListener?.onItemLongClick(index, this)
        }
        return result != null && result
    }

    fun size(): Int {
        return itemList.size
    }

    private fun cancelTimer() {
        timer?.cancel()
        timer = null
    }

    var defaultOnItemClickListener: OnItemClickListener? = null

    interface OnItemClickListener {
        fun onItemClick(index: Int, listView: ListView): Boolean
        fun onItemLongClick(index: Int, listView: ListView): Boolean {
            return false
        }
    }

    open class Item {
        var name: String = ""
        var onItemClickListener: OnItemClickListener? = null
        var enable: Boolean = false
        var rightText = ""

        var extra: Any? = null

        constructor(name: String, onItemClickListener: OnItemClickListener?, enable: Boolean) {
            this.name = name
            this.onItemClickListener = onItemClickListener
            this.enable = enable
        }

        constructor(name: String, onItemClickListener: OnItemClickListener?, rightText: String) {
            this.name = name
            this.onItemClickListener = onItemClickListener
            this.rightText = rightText
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Item) {
                return false
            }
            return other.name == name && other.onItemClickListener == onItemClickListener && other.enable == enable && other.rightText == rightText
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }
    }

    private class ScrollBar(context: Context) : ViewGroup(context) {
        private var position = 0
        private var maxSize = 0
        var size = 0

        private val paint by lazy { Paint() }
        private val shader by lazy {
            Colors.getShader(
                width / 1.8f,
                0f,
                width.toFloat(),
                0f,
                Colors.background_dark_2,
                Colors.background_dark_1
            )
        }

        fun setScrollBar(position: Int, maxSize: Int, size: Int) {
            this.position = position
            this.maxSize = maxSize
            this.size = size
            refresh()
            //invalidate()
        }

        private fun refresh() {
            if (size > maxSize) {
                var barHeight = measuredHeight * maxSize / size
                if (barHeight == 0) {
                    barHeight = 1
                }
                val barTop = measuredHeight * position / size
                bar.layout(Values.LINE_WIDTH, barTop, measuredWidth, barTop + barHeight)
                visibility = VISIBLE
            } else {
                visibility = GONE
            }
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            refresh()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            setMeasuredDimension(DEFAULT_PADDING, MeasureSpec.getSize(heightMeasureSpec))
        }

        override fun onDraw(canvas: Canvas?) {
            super.onDraw(canvas)

            paint.shader = shader
            canvas?.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            paint.shader = null
            paint.color = Colors.line
            canvas?.drawRect(0f, 0f, Values.LINE_WIDTH.toFloat(), measuredHeight.toFloat(), paint)
        }

        private val bar = object : View(context) {
            private val paint by lazy {
                Paint().apply { shader = null }
            }

            override fun onDraw(canvas: Canvas?) {
                if (paint.shader == null) {
                    paint.shader = Colors.getShader(
                        width / 2f,
                        0f,
                        width.toFloat(),
                        0f,
                        Colors.main,
                        Colors.main_light,
                        Shader.TileMode.MIRROR
                    )
                }
                canvas?.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
        }

        init {
            visibility = GONE
            setBackgroundColor(Colors.background)
            addView(bar)
        }
    }

    class ItemView(context: Context) : FrameLayout(context) {

        private var highlight = false
        private var enable = false
        private var playing = false

        private val rightText = TextView(context)
        private val leftText = TextView(context)
        private val rightIcon = ImageView(context)

        init {

            val layoutParams1 = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            val layoutParams2 = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            val layoutParams3 = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

            layoutParams2.gravity = Gravity.END

            rightIcon.scaleType = ImageView.ScaleType.CENTER
            leftText.setSingleLine()
            addView(leftText, layoutParams1)
            addView(rightText, layoutParams2)
            addView(rightIcon, layoutParams3)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            rightIcon.layout(
                rightIcon.right - (rightIcon.height * 0.9).toInt(),
                rightIcon.top,
                rightIcon.right,
                rightIcon.bottom
            )
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

        fun setText(text: String) {
            leftText.text = text
        }

        fun setRightText(text: String) {
            rightText.text = text
        }

        private var hasRightIcon = false
        private fun setRightIcon(drawable: Drawable?) {
            rightIcon.setImageDrawable(drawable)
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
        private var shakeTimer: Timer? = null
        fun shake() {
            if (shakeTimer == null) {
                shakeTimer = Timer()
                shakeTimer?.schedule(object : TimerTask() {
                    override fun run() {
                        shakeCount++
                        ThreadUtil.runOnUiThread { setHighlight(!highlight) }
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
            rightIcon.setImageDrawable(null)

            highlight = false
            enable = false
            playing = false

            setPadding(0, 0, 0, 0)

            setIcons()
        }
    }
}