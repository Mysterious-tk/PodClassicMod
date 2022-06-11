package com.example.podclassic.widget

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
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
        if (currAnimator?.isRunning == true) {
            return
        }
        if (childCount == 0) {
            return
        }
        getChildAt(childCount - 1).layout(0, 0, width, height)
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
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator?) {}
                override fun onAnimationEnd(animation: Animator?) {
                    if (childCount > 1) {
                        onViewRemove(currChild)
                        onViewDelete(currChild)
                        super@ScreenLayout.removeView(currChild)
                    }
                    //onViewAdd(child)
                }

                override fun onAnimationCancel(animation: Animator?) {
                    child.layout(0, 0, width, height)
                }

                override fun onAnimationRepeat(animation: Animator?) {}
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
        if (indexOfChild(view) != -1) {
            return
        }

        addView(view)//, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
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

            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator?) {}
                override fun onAnimationEnd(animation: Animator?) {
                    super@ScreenLayout.removeView(currChild)
                    viewStack.push(currChild)
                    onViewRemove(currChild)

                }

                override fun onAnimationCancel(animation: Animator?) {
                    view.layout(0, 0, width, height)
                }

                override fun onAnimationRepeat(animation: Animator?) {}
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