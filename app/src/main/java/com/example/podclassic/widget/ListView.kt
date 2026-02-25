package com.example.podclassic.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.VERTICAL
import android.widget.RelativeLayout
import com.example.podclassic.util.PinyinUtil
import com.example.podclassic.util.ThreadUtil
import com.example.podclassic.values.Colors
import com.example.podclassic.values.Icons
import com.example.podclassic.values.Values
import com.example.podclassic.values.Values.DEFAULT_PADDING
import com.example.podclassic.widget.TextView.Companion.TEXT_SIZE
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

    // 触摸起始位置
    private var touchStartY = 0f

    init {
        linearLayout.orientation = VERTICAL
        // linearLayout 宽度为 MATCH_PARENT，但右侧留出 scrollBar 的空间
        val linearLayoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        linearLayoutParams.rightMargin = DEFAULT_PADDING
        this.addView(linearLayout, linearLayoutParams)
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

        // 确保 ListView 是可点击和可触摸的
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true

        // 添加触摸事件监听器
        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 记录触摸起始位置
                    touchStartY = event.y
                    Log.d("ListView", "touch down: $touchStartY")
                }
                MotionEvent.ACTION_MOVE -> {
                    // 计算触摸移动的距离
                    val deltaY = event.y - touchStartY
                    Log.d("ListView", "touch move: deltaY=$deltaY")
                    
                    // 只要有滑动就处理，不需要等待达到阈值
                    if (Math.abs(deltaY) > 10) {
                        if (deltaY < 0) {
                            // 向上滑动，选择下一个项目
                            Log.d("ListView", "swipe up")
                            onSlide(1)
                        } else {
                            // 向下滑动，选择上一个项目
                            Log.d("ListView", "swipe down")
                            onSlide(-1)
                        }
                        // 重置触摸起始位置，以便连续滑动
                        touchStartY = event.y
                    }
                }
                MotionEvent.ACTION_UP -> {
                    Log.d("ListView", "touch up")
                }
            }
            // 返回 true 表示事件已处理
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

    // 批量添加项目，只刷新一次
    fun addAll(items: List<Item>) {
        itemList.addAll(items)
        refreshList()
    }

    // 批量添加项目到指定位置，只刷新一次
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
        android.util.Log.d("ListView", "refreshList: itemList.size=${itemList.size}, position=$position, index=$index")
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
            val item = itemList[i]
            android.util.Log.d("ListView", "refreshList: i=$i, name='${item.name}', rightText='${item.rightText}', itemView.visibility=${itemView.visibility}")
            itemView.setText(item.name)
            //itemList.height = itemHeight
            //itemView.cancelShake()
            if (scrollBar.visibility == View.VISIBLE) {
                itemView.setPadding(0, 0, DEFAULT_PADDING, 0)
            }
            onItemCreated(i, itemView)
            itemView.setHighlight(i == index)
            itemView.setEnable(item.enable)
            itemView.setRightText(item.rightText) // 放在最后，确保颜色正确
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

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            // 布局改变时刷新列表，确保所有元素正确显示
            refreshList()
        }
    }

    // 重写onTouchEvent方法，确保触摸事件能够被正确处理
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 记录触摸起始位置
                touchStartY = event.y
                Log.d("ListView", "onTouchEvent down: $touchStartY")
            }
            MotionEvent.ACTION_MOVE -> {
                // 计算触摸移动的距离
                val deltaY = event.y - touchStartY
                Log.d("ListView", "onTouchEvent move: deltaY=$deltaY")
                
                // 只要有滑动就处理，不需要等待达到阈值
                if (Math.abs(deltaY) > 10) {
                    if (deltaY < 0) {
                        // 向上滑动，选择下一个项目
                        Log.d("ListView", "swipe up")
                        onSlide(1)
                    } else {
                        // 向下滑动，选择上一个项目
                        Log.d("ListView", "swipe down")
                        onSlide(-1)
                    }
                    // 重置触摸起始位置，以便连续滑动
                    touchStartY = event.y
                }
            }
            MotionEvent.ACTION_UP -> {
                Log.d("ListView", "onTouchEvent up")
            }
        }
        // 返回 true 表示事件已处理
        return true
    }

    open fun onItemCreated(index: Int, itemView: ItemView) {
        itemView.itemIndex = index
        itemView.onItemClickListener = object : ItemView.OnItemClickListener {
            override fun onItemClick(index: Int) {
                this@ListView.index = index
                onItemClick()
            }
        }
    }

    //index 为list中位置
    protected var index = 0

    //position为屏幕上第一个元素的index
    private var position = 0

    var sorted = false
    private var prevSlideTime = 0L
    private var slideCount = 0

    private var timer: Timer? = null

    fun onSlide(direction: Int): Boolean {
        Log.d("ListView", "onSlide: direction=$direction, current index=$index, itemList size=${itemList.size}")
        
        if (direction == 0 || itemList.isEmpty()) {
            Log.d("ListView", "onSlide: no items or zero direction")
            return false
        }

        // 检查边界条件
        if ((direction < 0 && index == 0) || (direction > 0 && index == itemList.size - 1)) {
            Log.d("ListView", "onSlide: reached boundary")
            return false
        }

        // 处理滑动逻辑
        if (direction < 0) {
            // 向上滑动，选择上一个项目
            if (index > 0) {
                if (position > 0 && index == position) {
                    position--
                }
                index--
                Log.d("ListView", "onSlide: moved up to index=$index")
            }
        } else {
            // 向下滑动，选择下一个项目
            if (index < itemList.size - 1) {
                if (itemList.size > MAX_SIZE && index == position + MAX_SIZE - 1) {
                    position++
                }
                index++
                Log.d("ListView", "onSlide: moved down to index=$index")
            }
        }
        refreshList()
        Log.d("ListView", "onSlide: completed, returning true")
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
        
        private val rightTextWidth: Int
        
        var onItemClickListener: OnItemClickListener? = null
        var itemIndex = -1
        
        // 触摸起始位置
        private var touchStartX = 0f
        private var touchStartY = 0f

        init {
            val density = context.resources.displayMetrics.density
            // rightText 宽度自适应，根据内容调整
            rightTextWidth = FrameLayout.LayoutParams.WRAP_CONTENT
            
            val layoutParams1 = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            // leftText 右侧留出空间给 rightText 和 scrollBar
            layoutParams1.rightMargin = (60 * density).toInt() + DEFAULT_PADDING * 2
            // rightText 宽度自适应，靠右对齐
            val layoutParams2 = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END)
            layoutParams2.rightMargin = DEFAULT_PADDING // 右侧留出 scrollBar 的空间

            leftText.setSingleLine()
            rightText.setSingleLine()
            leftText.ellipsize = null
            rightText.ellipsize = null
            rightText.gravity = Gravity.CENTER_VERTICAL or Gravity.END
            rightText.visibility = View.VISIBLE
            // 为rightText设置更小的padding，确保时间能够显示
            rightText.setPadding(DEFAULT_PADDING / 4, DEFAULT_PADDING / 4, DEFAULT_PADDING / 4, DEFAULT_PADDING / 4)
            // 确保rightText的文本大小合适
            rightText.textSize = 14f
            // 设置等宽字体，确保时间对齐
            rightText.typeface = android.graphics.Typeface.MONOSPACE
            // 设置背景色透明
            rightText.setBackgroundColor(0x00000000)
            // 设置默认文字颜色
            rightText.setTextColor(Colors.text)
            // 确保 rightText 在最上层
            rightText.bringToFront()
            addView(leftText, layoutParams1)
            addView(rightText, layoutParams2)
            
            // 添加触摸事件监听器
            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 记录触摸起始位置
                        touchStartX = event.x
                        touchStartY = event.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // 计算触摸移动的距离
                        val deltaX = event.x - touchStartX
                        val deltaY = event.y - touchStartY
                        val distance = Math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()
                        
                        // 如果有明显的滑动，不拦截事件，让事件传递给父视图
                        if (distance > 10) {
                            return@setOnTouchListener false
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        // 计算触摸移动的距离
                        val deltaX = event.x - touchStartX
                        val deltaY = event.y - touchStartY
                        val distance = Math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()
                        
                        // 只有当移动距离很小时才触发点击事件
                        if (distance < 20) {
                            onItemClickListener?.onItemClick(itemIndex)
                            return@setOnTouchListener true
                        } else {
                            // 如果有明显的滑动，不拦截事件
                            return@setOnTouchListener false
                        }
                    }
                }
                true
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
            // 高亮时请求焦点并重新设置文本，确保 Marquee 滚动效果生效
            if (highlight) {
                leftText.isSelected = true
                leftText.requestFocus()
                // 重新设置文本触发 Marquee
                val text = leftText.text.toString()
                leftText.text = text
            } else {
                leftText.isSelected = false
            }
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
            rightText.visibility = View.VISIBLE // 始终可见，即使文本为空
            // 设置文字颜色
            rightText.setTextColor(Colors.text)
            // 确保文字居中对齐
            rightText.gravity = Gravity.CENTER
            updateLayout()
        }

        private fun updateLayout() {
            // 根据右侧时间文本是否为空来设置左侧文本的布局
            val layoutParams = leftText.layoutParams as FrameLayout.LayoutParams
            if (rightText.text.isNotEmpty()) {
                // 有 rightText 时，留出较小空间（时间格式为 05:00，约 45dp 足够）
                layoutParams.rightMargin = (45 * context.resources.displayMetrics.density).toInt() + DEFAULT_PADDING * 2
                leftText.paddingRight = DEFAULT_PADDING
                rightText.visibility = View.VISIBLE
            } else {
                // 没有 rightText 时，占满整个空间（只留出 scrollBar 的空间）
                layoutParams.rightMargin = DEFAULT_PADDING
                leftText.paddingRight = DEFAULT_PADDING
                rightText.visibility = View.GONE
            }
            leftText.layoutParams = layoutParams
        }

        private fun setIcons() {
            if (highlight) {
                setBackgroundColor(Colors.main)
                leftText.setTextColor(0xFFFFFFFF.toInt()) // 白色
                rightText.setTextColor(0xFFFFFFFF.toInt()) // 白色
            } else {
                background = null
                leftText.setTextColor(0xFF1A1A1A.toInt()) // 深灰色
                rightText.setTextColor(0xFFFFFFFF.toInt()) // 白色（在黑色背景上）
            }
            updateLayout()
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

            setIcons()
        }


    }
}