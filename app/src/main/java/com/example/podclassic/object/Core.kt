package com.example.podclassic.`object`

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.fragment.app.Fragment
import com.example.podclassic.R
import com.example.podclassic.activity.MainActivity
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.base.ScreenView
import com.example.podclassic.fragment.SplashFragment
import com.example.podclassic.service.MediaPlayerService
import com.example.podclassic.storage.SPManager
import com.example.podclassic.storage.SaveMusics
import com.example.podclassic.util.*
import com.example.podclassic.view.*
import java.lang.Exception
import java.util.*
import kotlin.system.exitProcess


object Core {

    private var controller : SlideController? = null
    private var titleBar : TitleBar? = null
    private var screen : Screen? = null
    private var darkMode : View? = null
    private var context : MainActivity? = null

    fun init(controller: SlideController, screen: Screen, titleBar: TitleBar, darkMode: View, context: MainActivity) {
        this.controller = controller
        this.screen = screen
        this.titleBar = titleBar
        this.context = context
        this.darkMode = darkMode
        setTitle(screen.currentView)
        setDarkMode(SPManager.getBoolean(SPManager.SP_DARK_MODE))
        controller.onTouchListener = object : SlideController.OnTouchListener {
            override fun onEnterClick(): Boolean { return Core.screen!!.getView().enter() }
            override fun onEnterLongClick(): Boolean { return Core.screen!!.getView().enterLongClick() }
            override fun onSlide(slideVal: Int): Boolean { return Core.screen!!.getView().slide(slideVal) }
        }
        wake()
    }

    var lock = false
    fun lock(lock : Boolean) {
        this.lock = lock
        controller?.lock(lock)
    }

    fun addView(view: ScreenView) {
        setTitle(view)
        screen!!.addView(view)
    }

    fun getView() : ScreenView? {
        return screen!!.getView()
    }

    fun removeView() : Boolean {
        val view = screen!!.removeView()
        if (view != null) {
            setTitle(view)
            return true
        }
        return false
    }

    private fun setTitle(view: ScreenView) {
        if (SPManager.getBoolean(SPManager.SP_SHOW_TIME) && (view is MainView)) {
            titleBar!!.showTime()
        } else {
            titleBar!!.showTitle(view.getTitle())
        }
    }

    fun home() {
        setTitle(screen!!.currentView)
        screen!!.clearViews()
    }

    fun refresh() {
        controller?.invalidate()
    }

    fun exit() {
        MediaPlayer.release()
        val context = BaseApplication.getContext()
        context.stopService(Intent(context, MediaPlayerService::class.java))
        VolumeUtil.releaseSoundPool()
        exitProcess(0)

        /*
        val context = BaseApplication.getContext()
        context.stopService(Intent(context, MediaPlayerService::class.java))
        MediaPlayer.clearPlayList()
        home()
         */

    }

    fun setDarkMode(darkMode: Boolean) {
        this.darkMode?.visibility = if (darkMode) View.VISIBLE else View.GONE
    }

    private var timer : Timer? = null
    private var prevTimerSetTime = 0L

    private const val DELAY = 10000L

    fun wake() {
        if (!MediaPlayer.isPlaying) {
            return
        }

        val currentMillis = System.currentTimeMillis()
        if (currentMillis - prevTimerSetTime <= 1000) {
            return
        }
        prevTimerSetTime = currentMillis
        timer?.cancel()
        timer = Timer()
        timer!!.schedule(object : TimerTask() {
            override fun run() {
                if (!MediaPlayer.isPlaying) {
                    return
                }
                ThreadUtil.runOnUiThread(Runnable {
                    if (screen!!.currentView !is MusicPlayerView) {
                        addView(MusicPlayerView(context!!))
                    }
                })
            }
        }, DELAY)
    }

}