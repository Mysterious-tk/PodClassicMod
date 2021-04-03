package com.example.podclassic.`object`

import android.annotation.SuppressLint
import android.view.View
import com.example.podclassic.activity.MainActivity
import com.example.podclassic.base.ScreenView
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.MediaUtil
import com.example.podclassic.util.ThreadUtil
import com.example.podclassic.view.MainView
import com.example.podclassic.view.MusicPlayerView
import com.example.podclassic.widget.AppWidget
import com.example.podclassic.widget.Screen
import com.example.podclassic.widget.SlideController
import com.example.podclassic.widget.TitleBar
import java.util.*

@SuppressLint("StaticFieldLeak")
object Core {
    private lateinit var controller: SlideController
    private lateinit var titleBar: TitleBar
    private lateinit var screen: Screen
    private lateinit var nightMode: View
    private var activity: MainActivity? = null

    fun bindActivity(controller: SlideController, screen: Screen, titleBar: TitleBar, darkMode: View, context: MainActivity) {
        this.controller = controller
        this.screen = screen
        this.titleBar = titleBar
        this.activity = context
        this.nightMode = darkMode
        setTitle(screen.currentView)
        controller.onTouchListener = object : SlideController.OnTouchListener {
            override fun onEnterClick(): Boolean {
                return screen.getView().enter()
            }

            override fun onEnterLongClick(): Boolean {
                return screen.getView().enterLongClick()
            }

            override fun onSlide(slideVal: Int): Boolean {
                return screen.getView().slide(slideVal)
            }
        }
        wake()

    }

    var lock = false
    fun lock(lock: Boolean) {
        this.lock = lock
        controller.lock(lock)
    }

    fun addView(view: ScreenView) {
        setTitle(view)
        screen.addView(view)
    }

    fun getView(): ScreenView {
        return screen.getView()
    }

    fun removeView(): Boolean {
        val view = screen.removeView()
        if (view != null) {
            setTitle(view)
            return true
        }
        return false
    }

    private fun setTitle(view: ScreenView) {
        if (SPManager.getBoolean(SPManager.SP_SHOW_TIME) && (view is MainView)) {
            titleBar.showTime()
        } else {
            titleBar.showTitle(view.getTitle())
        }
    }

    fun home() {
        screen.clearViews()
        setTitle(screen.currentView)
    }

    fun refresh() {
        controller.invalidate()
    }

    fun exit() {
        MediaPlayer.stop()
        /*
        MediaPlayer.release()
        activity?.stopService(Intent(activity, MediaPlayerService::class.java))
        VolumeUtil.releaseSoundPool()
        activity?.finish()
        exitProcess(0)
        */
    }

    fun setNightMode(darkMode: Boolean) {
        this.nightMode.visibility = if (darkMode) View.VISIBLE else View.GONE
    }

    private var timer: Timer? = null
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
                if (screen.currentView !is MusicPlayerView) {
                    ThreadUtil.runOnUiThread(Runnable {
                        if (activity != null) {
                            addView(MusicPlayerView(activity!!))
                        }
                    })
                }
            }
        }, DELAY)
    }

    fun init() {
        // 无论是否加载Activity都需要初始化
        MediaUtil.prepare()
        AppWidget.updateRemoteViews(null)
    }
}