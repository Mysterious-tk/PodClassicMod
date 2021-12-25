package com.example.podclassic.widget

import android.animation.*
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import java.util.*

open class ScreenLayout : ViewGroup {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)


    var onViewChangeListener : OnViewChangeListener? = null

    interface OnViewChangeListener {
        fun onViewRemove(view : View)
        fun onViewAdd(view : View)
        fun onViewAdded(view : View)
    }

    private var currAnimator : Animator? = null

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (currAnimator?.isRunning == true) {
            return
        }
        if (childCount == 0) {
            return
        }
        getChildAt(childCount - 1).layout(0, 0, width, height)
    }

    fun stackSize() : Int {
        return viewStack.size
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var maxHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(widthMeasureSpec, heightMeasureSpec)
            val height = if (child.layoutParams.height == LayoutParams.WRAP_CONTENT) child.measuredHeight else heightMeasureSpec
            maxHeight = maxHeight.coerceAtLeast(height)
        }
        setMeasuredDimension(widthMeasureSpec, maxHeight)
    }


    private val viewStack = Stack<View>()

    open fun currView() : View? {
        if (childCount == 0) {
            return null
        }
        return getChildAt(childCount - 1)
    }

    open fun removeView() : View? {
        if (currAnimator != null && currAnimator?.isRunning == true) {
            currAnimator?.cancel()
        }

        if (childCount <= 0 || viewStack.isEmpty()) {
            return currView()
        }

        val currChild = getChildAt(childCount - 1)
        val child = viewStack.pop()

        if (indexOfChild(child) != -1) {
            return currView()
        }
        super.addView(child)
        // requestLayout()
        // invalidate(true)
        onViewChangeListener?.onViewAdd(child)


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
                        onViewChangeListener?.onViewRemove(currChild)
                        super@ScreenLayout.removeView(currChild)
                    }
                    onViewChangeListener?.onViewAdded(child)
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

    override fun addView(view: View?) {
        if (currAnimator != null && currAnimator?.isRunning == true) {
            currAnimator?.cancel()
        }

        if (view == null) {
            return
        }
        if (indexOfChild(view) != -1) {
            return
        }
        super.addView(view)//, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        onViewChangeListener?.onViewAdd(view)


        if (childCount == 1) {
            //onViewChangeListener?.onViewAdded(view)
            return
        }

        val currChild = getChildAt(0)

        currAnimator = ValueAnimator.ofInt(width, 0).apply {
            duration = 300
            addUpdateListener {
                val currValue = animatedValue as Int
                view.layout(currValue, 0, currValue + width, height)
                currChild.layout(currValue - width, 0, currValue, height)
            }

            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator?) {}
                override fun onAnimationEnd(animation: Animator?) {
                    super@ScreenLayout.removeView(currChild)
                    viewStack.push(currChild)
                    onViewChangeListener?.onViewRemove(currChild)
                    onViewChangeListener?.onViewAdded(view)
                }
                override fun onAnimationCancel(animation: Animator?) {
                    view.layout(0, 0, width, height)
                }
                override fun onAnimationRepeat(animation: Animator?) {}
            })
            start()
        }
    }

    override fun removeAllViews() {
        if (currAnimator != null && currAnimator?.isRunning == true) {
            currAnimator?.cancel()
        }
        if (onViewChangeListener != null) {
            for (view in viewStack) {
                onViewChangeListener?.onViewRemove(view as View)
            }
        }
        viewStack.clear()
        onViewChangeListener?.onViewRemove(getChildAt(0))
        super.removeAllViews()
    }
}