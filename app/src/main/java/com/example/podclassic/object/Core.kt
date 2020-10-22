package com.example.podclassic.`object`

import android.content.Intent
import android.view.View
import com.example.podclassic.activity.MainActivity
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.base.ScreenView
import com.example.podclassic.service.MediaPlayerService
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.*
import com.example.podclassic.view.*
import java.util.*
import kotlin.system.exitProcess


object Core {

    private var controller : SlideController? = null
    private var titleBar : TitleBar? = null
    private var screen : Screen? = null
    private var nightMode : View? = null
    private var context : MainActivity? = null

    fun init(controller: SlideController, screen: Screen, titleBar: TitleBar, darkMode: View, context: MainActivity) {
        this.controller = controller
        this.screen = screen
        this.titleBar = titleBar
        this.context = context
        this.nightMode = darkMode
        setTitle(screen.currentView)
        setNightMode(SPManager.NightMode.nightMode(SPManager.getInt(SPManager.NightMode.SP_NAME)))
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
        screen!!.clearViews()
        setTitle(screen!!.currentView)
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

    fun setNightMode(darkMode: Boolean) {
        this.nightMode?.visibility = if (darkMode) View.VISIBLE else View.GONE
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