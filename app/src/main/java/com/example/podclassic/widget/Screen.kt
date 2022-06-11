package com.example.podclassic.widget

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import com.example.podclassic.base.ScreenView
import com.example.podclassic.view.MainView

class Screen(context: Context, attributeSet: AttributeSet?) : LinearLayout(context, attributeSet) {

    private val mainView = MainView(context)
    var currentView: ScreenView = mainView

    private val screenLayout = ScreenLayout(context)
    private val titleBar = TitleBar(context)

    init {
        //addView(view)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(p0: View?, p1: Outline?) {
                p1?.setRoundRect(0, 0, width, height, 12f)
            }
        }
        clipToOutline = true

        addView(titleBar, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(screenLayout, LayoutParams(LayoutParams.MATCH_PARENT, 0, 9f))
        orientation = VERTICAL
        screenLayout.onViewChangeListener = object : ScreenLayout.OnViewChangeListener {
            override fun onViewDelete(view: View) {
                (view as ScreenView).onViewDelete()
                view.getObserver()?.onViewDelete()
            }

            override fun onViewCreate(view: View) {
                (view as ScreenView).onViewCreate()
                view.getObserver()?.onViewCreate()
            }

            override fun onViewAdd(view: View) {
                (view as ScreenView).onViewAdd()
                view.getObserver()?.onViewAdd()
            }

            override fun onViewRemove(view: View) {
                (view as ScreenView).onViewRemove()
                view.getObserver()?.onViewRemove()
            }
        }
        screenLayout.add(mainView as View)

        setTitle()
    }

    private fun setTitle() {
        val title = currentView.getTitle()
        if (title == null) {
            titleBar.showTime()
        } else {
            titleBar.showTitle(title)
        }
    }

    private fun removeView(): ScreenView? {
        return if (currentView == mainView) {
            null
        } else {
            val view = screenLayout.remove()
            currentView = view as ScreenView
            currentView
        }
    }

    private fun getCurrent(): ScreenView {
        return currentView
    }

    private fun clearViews() {
        screenLayout.removeAll()
        currentView = mainView
        screenLayout.add(mainView as View)
    }

    override fun addView(child: View) {
        screenLayout.add(child)
        currentView = child as ScreenView
    }

    fun add(view: ScreenView): Boolean {
        if (view.getLaunchMode() == ScreenView.LAUNCH_MODE_SINGLE) {
            screenLayout.removeInstanceOf(view.javaClass)
        }
        addView(view as View)
        setTitle()
        return true
    }

    fun remove(): Boolean {
        val view = removeView()
        return if (view != null) {
            setTitle()
            true
        } else {
            false
        }
    }

    fun get(): ScreenView {
        return currentView
    }

    fun home(): Boolean {
        clearViews()
        setTitle()
        return true
    }

}