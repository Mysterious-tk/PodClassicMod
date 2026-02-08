package com.example.podclassic.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
        android.util.Log.d("ScreenLayout", "onLayout() called: changed=$changed, l=$l, t=$t, r=$r, b=$b, width=$width, height=$height")
        if (currAnimator?.isRunning == true) {
            android.util.Log.d("ScreenLayout", "Animator is running, skipping layout")
            return
        }
        if (childCount == 0) {
            android.util.Log.d("ScreenLayout", "No children, skipping layout")
            return
        }
        val child = getChildAt(childCount - 1)
        val childWidth = r - l
        val childHeight = b - t
        android.util.Log.d("ScreenLayout", "Layout child: ${child.javaClass.simpleName}, childWidth=$childWidth, childHeight=$childHeight")
        child.layout(0, 0, childWidth, childHeight)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // 将触摸事件传递给当前显示的视图
        val currentView = curr()
        if (currentView != null) {
            android.util.Log.d("ScreenLayout", "dispatching touch event to current view: ${currentView.javaClass.simpleName}")
            val handled = currentView.dispatchTouchEvent(event)
            android.util.Log.d("ScreenLayout", "Current view handled: $handled")
            // 即使currentView没有处理事件，也要返回true，确保事件能够被正确传递
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 将触摸事件传递给当前显示的视图
        val currentView = curr()
        if (currentView != null) {
            android.util.Log.d("ScreenLayout", "onTouchEvent called for current view: ${currentView.javaClass.simpleName}")
            val handled = currentView.onTouchEvent(event)
            android.util.Log.d("ScreenLayout", "Current view onTouchEvent handled: $handled")
            // 即使currentView没有处理事件，也要返回true，确保事件能够被正确传递
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // 拦截触摸事件，确保它能够被传递给onTouchEvent方法
        android.util.Log.d("ScreenLayout", "onInterceptTouchEvent called")
        return true
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
        if (childCount == 0) {
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

        if (indexOfChild(child) != -1) {
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
        android.util.Log.d("ScreenLayout", "add() called with view: ${view?.javaClass?.simpleName}")
        if (currAnimator != null && currAnimator?.isRunning == true) {
            android.util.Log.d("ScreenLayout", "Cancelling running animator")
            currAnimator?.cancel()
        }

        if (view == null) {
            android.util.Log.d("ScreenLayout", "View is null, returning")
            return
        }
        if (indexOfChild(view) != -1) {
            android.util.Log.d("ScreenLayout", "View already added, returning")
            return
        }

        // 确保视图有正确的布局参数
        if (view.layoutParams == null) {
            android.util.Log.d("ScreenLayout", "Setting default layout params for view")
            view.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        android.util.Log.d("ScreenLayout", "View layoutParams: ${view.layoutParams}")
        addView(view)
        android.util.Log.d("ScreenLayout", "View added, childCount=$childCount")
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