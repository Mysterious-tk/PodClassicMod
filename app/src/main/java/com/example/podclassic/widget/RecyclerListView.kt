package com.example.podclassic.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.podclassic.util.ThreadUtil
import com.example.podclassic.values.Colors
import com.example.podclassic.values.Values
import com.example.podclassic.values.Values.DEFAULT_PADDING
import com.example.podclassic.widget.TextView.Companion.TEXT_SIZE
import java.util.*
import kotlin.math.abs


open class RecyclerListView(context: Context, private val MAX_SIZE: Int) : FrameLayout(context) {

    companion object {
        const val DEFAULT_MAX_SIZE = 10
        const val DELAY = 75
        const val QUICK_SLIDE = DEFAULT_MAX_SIZE * 5
    }

    constructor(context: Context) : this(context, DEFAULT_MAX_SIZE)

    private val recyclerView: RecyclerView
    private val adapter: ItemAdapter
    private val scrollBar = ScrollBar(context)
    private val indexView = android.widget.TextView(context)
    private val recyclerParams: LayoutParams

    // 触摸起始位置
    private var touchStartY = 0f

    init {
        // 创建 RecyclerView
        recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.overScrollMode = View.OVER_SCROLL_NEVER
        
        // 创建 Adapter
        adapter = ItemAdapter()
        recyclerView.adapter = adapter

        // 添加 RecyclerView
        recyclerParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        recyclerParams.rightMargin = DEFAULT_PADDING
        this.addView(recyclerView, recyclerParams)

        // 添加滚动条
        val scrollBarLayoutParams =
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        scrollBarLayoutParams.gravity = Gravity.END
        this.addView(scrollBar, scrollBarLayoutParams)

        // 初始化索引视图
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

        // 确保 RecyclerListView 是可点击和可触摸的
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true

        // 添加触摸事件监听器
        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartY = event.y
                    Log.d("RecyclerListView", "touch down: $touchStartY")
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.y - touchStartY
                    Log.d("RecyclerListView", "touch move: deltaY=$deltaY")
                    
                    if (Math.abs(deltaY) > 10) {
                        if (deltaY < 0) {
                            Log.d("RecyclerListView", "swipe up")
                            onSlide(1)
                        } else {
                            Log.d("RecyclerListView", "swipe down")
                            onSlide(-1)
                        }
                        touchStartY = event.y
                    }
                }
                MotionEvent.ACTION_UP -> {
                    Log.d("RecyclerListView", "touch up")
                }
            }
            true
        }
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

    fun addAll(items: List<Item>) {
        itemList.addAll(items)
        refreshList()
    }

    fun addAll(items: List<Item>, index: Int) {
        itemList.addAll(index, items)
        refreshList()
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

    fun getCurrentIndex(): Int {
        return index
    }

    fun setCurrent(index: Int) {
        this.index = index
        this.position =
            (index - MAX_SIZE / 2).coerceAtMost(itemList.size - MAX_SIZE).coerceAtLeast(0)
        refreshList()
    }

    fun shake() {
        // RecyclerView 中不需要实现 shake，或者可以通过其他方式实现
    }

    fun removeCurrentItem() {
        if (index < itemList.size) {
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
    }

    fun refreshList() {
        adapter.notifyDataSetChanged()
        updateScrollBar()
        updateHighlight()
    }

    private fun updateScrollBar() {
        val oldShowScrollBar = scrollBar.visibility == View.VISIBLE
        scrollBar.setScrollBar(position, MAX_SIZE, itemList.size)
        val newShowScrollBar = itemList.size > MAX_SIZE
        // 根据是否显示滚动条调整 RecyclerView 的 rightMargin
        recyclerParams.rightMargin = if (newShowScrollBar) DEFAULT_PADDING else 0
        recyclerView.layoutParams = recyclerParams
        // 如果滚动条显示状态发生变化，刷新所有可见 item
        if (oldShowScrollBar != newShowScrollBar) {
            adapter.notifyDataSetChanged()
        }
    }

    private fun updateHighlight() {
        // 更新高亮状态
        recyclerView.post {
            for (i in 0 until recyclerView.childCount) {
                val viewHolder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i))
                if (viewHolder is ItemViewHolder) {
                    val itemIndex = position + i
                    viewHolder.setHighlight(itemIndex == index)
                }
            }
        }
    }

    open fun refreshItem() {
        if (itemList.isEmpty() || index >= itemList.size) return
        val item = itemList[index]
        // 刷新当前项
        adapter.notifyItemChanged(index - position)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            refreshList()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            refreshList()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartY = event.y
                Log.d("RecyclerListView", "onTouchEvent down: $touchStartY")
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = event.y - touchStartY
                Log.d("RecyclerListView", "onTouchEvent move: deltaY=$deltaY")
                
                if (Math.abs(deltaY) > 10) {
                    if (deltaY < 0) {
                        Log.d("RecyclerListView", "swipe up")
                        onSlide(1)
                    } else {
                        Log.d("RecyclerListView", "swipe down")
                        onSlide(-1)
                    }
                    touchStartY = event.y
                }
            }
            MotionEvent.ACTION_UP -> {
                Log.d("RecyclerListView", "onTouchEvent up")
            }
        }
        return true
    }

    open fun onItemCreated(index: Int, itemView: ItemView) {
        itemView.itemIndex = index
        itemView.onItemClickListener = object : ItemView.OnItemClickListener {
            override fun onItemClick(index: Int) {
                this@RecyclerListView.index = index
                onItemClick()
            }
        }
    }

    protected var index = 0
    private var position = 0

    var sorted = false
    private var prevSlideTime = 0L
    private var slideCount = 0

    private var timer: Timer? = null

    fun onSlide(direction: Int): Boolean {
        Log.d("RecyclerListView", "onSlide: direction=$direction, current index=$index, itemList size=${itemList.size}")
        
        if (direction == 0 || itemList.isEmpty()) {
            Log.d("RecyclerListView", "onSlide: no items or zero direction")
            return false
        }

        if ((direction < 0 && index == 0) || (direction > 0 && index == itemList.size - 1)) {
            Log.d("RecyclerListView", "onSlide: reached boundary")
            return false
        }

        if (direction < 0) {
            if (index > 0) {
                if (position > 0 && index == position) {
                    position--
                }
                index--
                Log.d("RecyclerListView", "onSlide: moved up to index=$index")
            }
        } else {
            if (index < itemList.size - 1) {
                if (itemList.size > MAX_SIZE && index == position + MAX_SIZE - 1) {
                    position++
                }
                index++
                Log.d("RecyclerListView", "onSlide: moved down to index=$index")
            }
        }
        refreshList()
        Log.d("RecyclerListView", "onSlide: completed, returning true")
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
        fun onItemClick(index: Int, listView: RecyclerListView): Boolean
        fun onItemLongClick(index: Int, listView: RecyclerListView): Boolean {
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

    // RecyclerView Adapter
    private inner class ItemAdapter : RecyclerView.Adapter<ItemViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
            val itemView = ItemView(context)
            // 设置 item 宽度为 match_parent
            itemView.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
            return ItemViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: ItemViewHolder, listPosition: Int) {
            val actualIndex = position + listPosition
            if (actualIndex < itemList.size) {
                val item = itemList[actualIndex]
                // 判断是否显示滚动条（item 数量大于 MAX_SIZE 时显示）
                val showScrollBar = itemList.size > MAX_SIZE
                holder.bind(item, actualIndex == index, item.enable, item.rightText, showScrollBar)
                holder.itemView.setOnClickListener {
                    this@RecyclerListView.index = actualIndex
                    onItemClick()
                }
            }
        }

        override fun getItemCount(): Int {
            return minOf(MAX_SIZE, itemList.size - position).coerceAtLeast(0)
        }
    }

    inner class ItemViewHolder(private val listItemView: ItemView) : RecyclerView.ViewHolder(listItemView) {
        fun bind(item: Item, isHighlighted: Boolean, isEnabled: Boolean, rightText: String, showScrollBar: Boolean = true) {
            listItemView.setText(item.name)
            listItemView.setHighlight(isHighlighted)
            listItemView.setEnable(isEnabled)
            listItemView.setRightText(rightText, showScrollBar)
        }

        fun setHighlight(highlight: Boolean) {
            listItemView.setHighlight(highlight)
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

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            paint.shader = shader
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            paint.shader = null
            paint.color = Colors.line
            canvas.drawRect(0f, 0f, Values.LINE_WIDTH.toFloat(), measuredHeight.toFloat(), paint)
        }

        private val bar = object : View(context) {
            private val paint by lazy {
                Paint().apply { shader = null }
            }

            override fun onDraw(canvas: Canvas) {
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
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
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
        
        companion object {
            private const val RIGHT_TEXT_MAX_WIDTH_DP = 80f
        }
        
        var onItemClickListener: OnItemClickListener? = null
        var itemIndex = -1
        
        private var touchStartX = 0f
        private var touchStartY = 0f
        
        private var rightTextWidth = 0
        private var rightTextHeight = 0

        init {
            val density = context.resources.displayMetrics.density
            // 固定右边距，给 rightText 留出空间（约 60dp 足够显示时间格式 05:00）
            val rightTextAreaWidth = (60 * density).toInt()
            
            // rightText 宽度自适应，靠右对齐
            val layoutParams2 = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            layoutParams2.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            layoutParams2.rightMargin = DEFAULT_PADDING
            
            // leftText 占据剩余空间，右侧留出固定区域给 rightText
            val layoutParams1 = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            layoutParams1.rightMargin = rightTextAreaWidth + DEFAULT_PADDING * 2

            leftText.setSingleLine()
            rightText.setSingleLine()
            // 不放省略号，直接截断
            leftText.ellipsize = null
            rightText.ellipsize = android.text.TextUtils.TruncateAt.END
            rightText.maxWidth = rightTextAreaWidth
            rightText.visibility = View.VISIBLE
            rightText.setPadding(DEFAULT_PADDING / 4, DEFAULT_PADDING / 4, DEFAULT_PADDING / 4, DEFAULT_PADDING / 4)
            rightText.textSize = 14f
            rightText.typeface = android.graphics.Typeface.MONOSPACE
            rightText.setBackgroundColor(0x00000000)
            rightText.setTextColor(Colors.text)
            // 文本在 TextView 内右对齐
            rightText.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            addView(leftText, layoutParams1)
            addView(rightText, layoutParams2)
            
            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartX = event.x
                        touchStartY = event.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.x - touchStartX
                        val deltaY = event.y - touchStartY
                        val distance = Math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()
                        
                        if (distance > 10) {
                            return@setOnTouchListener false
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        val deltaX = event.x - touchStartX
                        val deltaY = event.y - touchStartY
                        val distance = Math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()
                        
                        if (distance < 20) {
                            onItemClickListener?.onItemClick(itemIndex)
                            return@setOnTouchListener true
                        } else {
                            return@setOnTouchListener false
                        }
                    }
                }
                true
            }
        }
        
        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            // 手动布局 rightText，确保它紧贴右边（留出滚动条空间）
            if (rightText.visibility != View.GONE && rightTextWidth > 0 && rightTextHeight > 0) {
                val parentWidth = width
                val parentHeight = height
                val right = parentWidth - DEFAULT_PADDING
                val leftPos = right - rightTextWidth
                val topPos = (parentHeight - rightTextHeight) / 2
                val bottomPos = topPos + rightTextHeight
                rightText.layout(leftPos, topPos, right, bottomPos)
            }
        }
        
        interface OnItemClickListener {
            fun onItemClick(index: Int)
        }

        fun setHighlight(highlight: Boolean) {
            if (this.highlight == highlight) {
                return
            }
            this.highlight = highlight
            leftText.scrollable = highlight
            if (highlight) {
                leftText.isSelected = true
                leftText.requestFocus()
                val text = leftText.text.toString()
                leftText.text = text
            } else {
                leftText.isSelected = false
            }
            if (highlight) {
                setBackgroundColor(Colors.main)
                leftText.setTextColor(0xFFFFFFFF.toInt())
                rightText.setTextColor(0xFFFFFFFF.toInt())
            } else {
                background = null
                leftText.setTextColor(0xFF1A1A1A.toInt())
                rightText.setTextColor(Colors.text)
            }
        }

        fun setEnable(enable: Boolean) {
            if (this.enable == enable) {
                return
            }
            this.enable = enable
        }

        fun setPlaying(playing: Boolean) {
            if (this.playing == playing) {
                return
            }
            this.playing = playing
        }

        fun setText(text: String) {
            leftText.text = text
        }

        fun setRightText(text: String, showScrollBar: Boolean = true) {
            rightText.text = text
            val layoutParams = leftText.layoutParams as FrameLayout.LayoutParams
            if (text.isEmpty()) {
                rightText.visibility = View.GONE
                rightTextWidth = 0
                rightTextHeight = 0
                // 没有 rightText 时，根据是否显示滚动条设置边距
                layoutParams.rightMargin = if (showScrollBar) DEFAULT_PADDING else 0
                leftText.layoutParams = layoutParams
            } else {
                rightText.visibility = View.VISIBLE
                // 测量 rightText 的实际宽高
                rightText.measure(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                )
                rightTextWidth = rightText.measuredWidth
                rightTextHeight = rightText.measuredHeight
                // 有 rightText 时，根据实际宽度和是否显示滚动条设置边距
                val scrollBarMargin = if (showScrollBar) DEFAULT_PADDING * 2 else DEFAULT_PADDING
                layoutParams.rightMargin = rightTextWidth + scrollBarMargin
                leftText.layoutParams = layoutParams
                // 请求重新布局以更新 rightText 位置
                requestLayout()
            }
            // 颜色由 setHighlight 统一管理
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

            highlight = false
            enable = false
            playing = false

            background = null
            leftText.setTextColor(0xFF1A1A1A.toInt())
            rightText.setTextColor(0xFF1A1A1A.toInt())
        }
    }
}
