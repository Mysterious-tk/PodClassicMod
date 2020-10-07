package com.example.podclassic.view

import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.example.podclassic.base.ScreenView
import com.example.podclassic.util.Colors
import java.util.*


@SuppressLint("ObjectAnimatorBinding")
class Screen(context: Context, attributeSet: AttributeSet?) : FrameLayout(context, attributeSet) {

    private val viewStack = Stack<ScreenView>()

    private var addAnimator : ObjectAnimator? = null
    private var removeAnimator : ObjectAnimator? = null

    var mainView : ScreenView = MainView(context)
    var currentView : ScreenView = mainView

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        addAnimator = ObjectAnimator.ofFloat(null, "translationX", width.toFloat(), 0f)
        removeAnimator = ObjectAnimator.ofFloat(null, "translationX", -width.toFloat(), 0f)
    }

    init {
        setBackgroundColor(Colors.background)
        layoutTransition = LayoutTransition()
        layoutTransition.setDuration(300L)
        addView(mainView as View)
    }

    fun addView(view: ScreenView) {
        if (childCount != 0) {
            val prevView = getChildAt(0) as ScreenView
            if (prevView.getLaunchMode() != ScreenView.LAUNCH_MODE_SINGLE) {
                viewStack.push(prevView)
            }
            currentView = view
            //layoutTransition.setAnimator(LayoutTransition.DISAPPEARING, ObjectAnimator.ofFloat(null, "translationX", 0f, -measuredWidth.toFloat()))
            removeViewAt(0)
        }
        layoutTransition.setAnimator(LayoutTransition.APPEARING, addAnimator)
        addView(view as View)
        view.onWindowFocusChanged(true)
    }


    fun getView(): ScreenView {
        return currentView
    }


    @SuppressLint("ObjectAnimatorBinding")
    fun removeView() : ScreenView? {
        if (currentView == mainView) {
            return null
        }
        if (childCount != 0) {
            //layoutTransition.setAnimator(LayoutTransition.DISAPPEARING, ObjectAnimator.ofFloat(null, "translationX", 0f, measuredWidth.toFloat()))
            removeViewAt(0)
            currentView = if (viewStack.isEmpty()) {
                mainView
            } else {
               viewStack.pop()
            }
            layoutTransition.setAnimator(LayoutTransition.APPEARING, removeAnimator)
            addView(currentView as View)
            (currentView as View).onWindowFocusChanged(true)
            return currentView
        }
        return null
    }

    @SuppressLint("ObjectAnimatorBinding")
    fun clearViews() {
        viewStack.clear()
        removeAllViews()
        currentView = mainView
        layoutTransition.setAnimator(LayoutTransition.APPEARING, null)
        addView(currentView as View)
    }

}