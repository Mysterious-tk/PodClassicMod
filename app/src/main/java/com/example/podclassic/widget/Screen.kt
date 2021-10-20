package com.example.podclassic.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.example.podclassic.base.ScreenView
import com.example.podclassic.util.Colors
import com.example.podclassic.view.MainView

class Screen(context: Context, attributeSet: AttributeSet?) : FrameLayout(context, attributeSet) {

    private val mainView = MainView(context)
    var currentView : ScreenView = mainView
    private val screenLayout = ScreenLayout(context)

    init {
        setBackgroundColor(Colors.background)
        addView(screenLayout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        screenLayout.onViewChangeListener = object : ScreenLayout.OnViewChangeListener {
            override fun onViewRemoved(view: View) {
                (view as ScreenView).onStop()
            }
            override fun onViewAdded(view: View) {
                (view as ScreenView).onStart()
            }
        }
        screenLayout.addView(mainView as View)

    }

    fun removeView() : ScreenView? {
        if (currentView == mainView) {
            return null
        } else {
            val view = screenLayout.removeView()
            if (view != null) {
                currentView = view as ScreenView
            }
        }
        return currentView
    }

    fun getCurrent(): ScreenView {
        return currentView
    }

    @SuppressLint("ObjectAnimatorBinding")
    fun clearViews() {
        screenLayout.removeAllViews()
        currentView = mainView
        screenLayout.addView(mainView as View)
    }

    fun addView(child: ScreenView) {
        screenLayout.addView(child as View)
        currentView = child
    }
}