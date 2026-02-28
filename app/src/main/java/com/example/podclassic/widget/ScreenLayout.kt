package com.example.podclassic.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.contains
import androidx.core.view.isEmpty
import java.util.*

open class ScreenLayout : ViewGroup {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)


    var onViewChangeListener: OnViewChangeListener? = null

    interface OnViewChangeListener {
        fun onViewDelete(view: View)
        fun onViewCreate(view: View)
        fun onViewAdd(view: View)
        fun onViewRemove(view: View)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        val curr = curr() ?: return
        if (hasWindowFocus) {
            onViewAdd(curr)
        } else {
            onViewRemove(curr)
        }
    }

    private fun onViewCreate(view: View) {
        onViewChangeListener?.onViewCreate(view)

    }

    private fun onViewRemove(view: View) {
        onViewChangeListener?.onViewRemove(view)
    }

    private fun onViewDelete(view: View) {
        onViewChangeListener?.onViewDelete(view)
    }

    private fun onViewAdd(view: View) {
        onViewChangeListener?.onViewAdd(view)
    }

    private var currAnimator: Animator? = null

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (currAnimator?.isRunning == true) {
            return
        }
        if (isEmpty()) {
            return
        }
        val child = getChildAt(childCount - 1)
        val childWidth = r - l
        val childHeight = b - t
        child.layout(0, 0, childWidth, childHeight)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // 将触摸事件传递给当前显示的视图
        val currentView = curr()
        if (currentView != null) {
            val handled = currentView.dispatchTouchEvent(event)
            if (handled) {
                return true
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 将触摸事件传递给当前显示的视图
        val currentView = curr()
        if (currentView != null) {
            val handled = currentView.onTouchEvent(event)
            if (handled) {
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // 不拦截触摸事件，让子视图自己处理滑动和点击
        return false
    }

    fun stackSize(): Int {
        return viewStack.size
    }

    fun removeInstanceOf(clazz : Class<Any>) {
        for (view in viewStack) {
            if (view.javaClass == clazz) {
                onViewDelete(view)
                viewStack.remove(view)
            }
        }
        val current = curr()
        if (current?.javaClass == clazz) {
            removeView(current)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        /*
        var maxHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(widthMeasureSpec, heightMeasureSpec)
            val height = if (child.layoutParams.height == LayoutParams.WRAP_CONTENT) child.measuredHeight else heightMeasureSpec
            maxHeight = maxHeight.coerceAtLeast(height)
        }
        setMeasuredDimension(widthMeasureSpec, maxHeight)

         */
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(widthMeasureSpec, heightMeasureSpec)

        }
    }


    private val viewStack = Stack<View>()

    open fun curr(): View? {
        if (isEmpty()) {
            return null
        }
        return getChildAt(childCount - 1)
    }

    open fun remove(): View? {
        if (currAnimator != null && currAnimator?.isRunning == true) {
            currAnimator?.cancel()
        }

        if (childCount <= 0 || viewStack.isEmpty()) {
            return curr()
        }

        val currChild = getChildAt(childCount - 1)
        val child = viewStack.pop()

        if (child in this) {
            return curr()
        }
        super.addView(child)
        // requestLayout()
        // invalidate(true)
        onViewAdd(child)

        currAnimator = ValueAnimator.ofInt(0, width).apply {
            addUpdateListener {
                val curr = animatedValue as Int
                duration = 300
                currChild.layout(curr, 0, curr + width, height)
                child.layout(curr - width, 0, curr, height)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (childCount > 1) {
                        onViewRemove(currChild)
                        onViewDelete(currChild)
                        super@ScreenLayout.removeView(currChild)
                    }
                    //onViewAdd(child)
                }

                override fun onAnimationCancel(animation: Animator) {
                    child.layout(0, 0, width, height)
                }
            })
            setTarget(child)
            start()
        }
        return child
    }


    fun removeChild(child: View) {
        viewStack.remove(child)
    }

    fun add(view: View?) {
        if (currAnimator != null && currAnimator?.isRunning == true) {
            currAnimator?.cancel()
        }

        if (view == null) {
            return
        }
        if (view in this) {
            return
        }

        // 确保视图有正确的布局参数
        if (view.layoutParams == null) {
            view.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        addView(view)
        onViewCreate(view)
        //onViewAdd(view)

        if (childCount == 1) {
            onViewAdd(view)
            //onViewChangeListener?.onViewAdded(view)
            return
        }

        val currChild = getChildAt(0)
        var viewAdded = false
        currAnimator = ValueAnimator.ofInt(width, 0).apply {
            duration = 300
            addUpdateListener {
                val currValue = animatedValue as Int
                view.layout(currValue, 0, currValue + width, height)
                currChild.layout(currValue - width, 0, currValue, height)
                if (!viewAdded && currValue != width) {
                    onViewAdd(view)
                    viewAdded = true
                }

            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super@ScreenLayout.removeView(currChild)
                    viewStack.push(currChild)
                    onViewRemove(currChild)

                }

                override fun onAnimationCancel(animation: Animator) {
                    view.layout(0, 0, width, height)
                }
            })
            start()
        }
    }

    fun removeAll() {
        if (currAnimator != null && currAnimator?.isRunning == true) {
            currAnimator?.cancel()
        }
        if (onViewChangeListener != null) {
            for (view in viewStack) {
                onViewRemove(view)
                onViewDelete(view)
            }
        }
        viewStack.clear()
        onViewRemove(getChildAt(0))
        onViewDelete(getChildAt(0))
        super.removeAllViews()
    }
}