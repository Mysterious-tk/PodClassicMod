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
import java.util.*
import kotlin.math.abs


open class ListView(context: Context, private val MAX_SIZE: Int) : FrameLayout(context) {
    
    // 标志：是否需要为右侧时间预留空间
    var reserveSpaceForTime = true

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
        // 设置ItemView是否需要为右侧时间预留空间
        itemView.setReserveSpaceForTime(reserveSpaceForTime)
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
        private val rightIcon = ImageView(context)
        
        var onItemClickListener: OnItemClickListener? = null
        var itemIndex = -1
        
        // 触摸起始位置
        private var touchStartX = 0f
        private var touchStartY = 0f

        init {

            val layoutParams1 = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            val layoutParams2 = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            val layoutParams3 = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

            layoutParams2.gravity = Gravity.END

            rightIcon.scaleType = ImageView.ScaleType.CENTER
            leftText.setSingleLine()
            rightText.setSingleLine()
            // 左侧文本不显示省略号，填不下就直接截断
            leftText.ellipsize = null
            // 右侧文本（时长）也不显示省略号，避免秒钟被截断成...
            rightText.ellipsize = null
            addView(leftText, layoutParams1)
            addView(rightText, layoutParams2)
            addView(rightIcon, layoutParams3)
            
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
        private var reserveSpaceForTime = true // 默认需要为右侧时间预留空间
        
        fun setReserveSpaceForTime(reserve: Boolean) {
            reserveSpaceForTime = reserve
        }
        
        private fun setRightIcon(drawable: Drawable?) {
            rightIcon.setImageDrawable(drawable)
            hasRightIcon = (drawable != null)
        }

        private fun setIcons() {
            if (highlight) {
                setBackgroundColor(Colors.main)
                leftText.setTextColor(Colors.text_light)
                rightText.setTextColor(Colors.text_light)
                // 只在播放时显示图标，移除箭头图标
                if (playing) {
                    setRightIcon(Icons.PLAYING_WHITE.drawable)
                } else {
                    setRightIcon(null)
                }
            } else {
                background = null
                leftText.setTextColor(Colors.text)
                rightText.setTextColor(Colors.text)
                // 只在播放时显示图标，移除箭头图标
                if (playing) {
                    setRightIcon(Icons.PLAYING_BLACK.drawable)
                } else {
                    setRightIcon(null)
                }
            }
            // 根据是否需要为右侧时间预留空间来设置左侧文本的右侧内边距
            if (reserveSpaceForTime) {
                leftText.paddingRight = DEFAULT_PADDING * 8
            } else {
                leftText.paddingRight = DEFAULT_PADDING
            }
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