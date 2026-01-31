package com.example.podclassic.base

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.view.View
import com.example.podclassic.activity.MainActivity
import com.example.podclassic.service.MediaPresenter
import com.example.podclassic.service.MediaService
import com.example.podclassic.storage.MusicListTable
import com.example.podclassic.storage.MusicTable
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.MediaStoreUtil
import com.example.podclassic.util.ThreadUtil
import com.example.podclassic.util.VolumeUtil
import com.example.podclassic.values.Colors
import com.example.podclassic.values.Strings
import com.example.podclassic.values.Values
import com.example.podclassic.view.MusicPlayerView
import com.example.podclassic.widget.Screen
import com.example.podclassic.widget.SlideController
import java.util.*
import kotlin.system.exitProcess


@SuppressLint("StaticFieldLeak")
object Core {
    private lateinit var controller: SlideController
    private lateinit var screen: Screen
    private lateinit var nightMode: View
    private var activity: MainActivity? = null

    fun bindActivity(
        controller: SlideController,
        screen: Screen,
        darkMode: View,
        context: MainActivity
    ) {
        this.controller = controller
        this.screen = screen
        activity = context
        nightMode = darkMode
        controller.onTouchListener = object : SlideController.OnTouchListener {
            override fun onEnterClick(): Boolean {
                return screen.get().enter()
            }

            override fun onEnterLongClick(): Boolean {
                return screen.get().enterLongClick()
            }

            override fun onTouch() {
                wake()
            }

            override fun onSlide(slideVal: Int): Boolean {
                return screen.get().slide(slideVal)
            }


            override fun onMenuClick(): Boolean {
                return removeView()
            }

            override fun onMenuLongClick(): Boolean {
                home()
                return true
            }

            override fun onNextClick(): Boolean {
                MediaPresenter.next()
                return true
            }

            override fun onNextLongClick(): Boolean {
                return MediaPresenter.forward()
            }

            override fun onPauseClick(): Boolean {
                MediaPresenter.playPause()
                return true
            }

            override fun onPrevClick(): Boolean {
                MediaPresenter.prev()
                return true
            }

            override fun onPrevLongClick(): Boolean {
                return MediaPresenter.backward()
            }
        }
        refresh()
        wake()

    }

    var lock = false
        private set

    fun lock(lock: Boolean) {
        this.lock = lock
        controller.enable = !lock
    }

    fun addView(view: ScreenView) {
        screen.add(view)
    }

    fun getView(): ScreenView {
        return screen.get()
    }

    fun removeView(): Boolean {
        return screen.remove()
    }

    fun home() {
        screen.home()
    }

    fun refresh() {
        controller.colorController = Colors.controller
        controller.colorButton = Colors.button
        controller.invalidate()
        activity?.setColor(Colors.screen)
    }

    fun setNightMode() {
        val id = SPManager.getInt(
            SPManager.NightMode.SP_NAME
        )
        val darkMode = if (SPManager.NightMode.AUTO.id == id) {
            val nightModeFlags =
                BaseApplication.context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (nightModeFlags == Configuration.UI_MODE_NIGHT_UNDEFINED || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val calendar = Calendar.getInstance()
                val hour = calendar[Calendar.HOUR_OF_DAY]
                hour >= 23 || hour <= 5
            } else {
                nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            }
        } else {
            SPManager.NightMode.ENABLE.id == id
        }
        nightMode.visibility = if (darkMode) View.VISIBLE else View.GONE
    }

    fun setLanguage() {
        val context = BaseApplication.context
        val resources = context.resources
        val configuration: Configuration = resources.configuration

        when (SPManager.getInt(SPManager.Language.SP_NAME)) {
            SPManager.Language.CN.id -> configuration.setLocale(Locale.SIMPLIFIED_CHINESE)
            SPManager.Language.TW.id -> configuration.setLocale(Locale.TRADITIONAL_CHINESE)
            SPManager.Language.EN.id -> configuration.setLocale(Locale.US)
            SPManager.Language.AUTO.id -> configuration.setLocale(Locale.getDefault())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            context.createConfigurationContext(configuration)
        } else {
            @Suppress("DEPRECATION")
            resources.updateConfiguration(configuration, resources.displayMetrics)
        }
        Strings.init()
    }

    private const val DELAY = 10000L

    private var timer = com.example.podclassic.util.Timer(DELAY) {
        if (MediaPresenter.isPlaying() && screen.currentView !is MusicPlayerView && activity != null) {
            ThreadUtil.runOnUiThread {
                addView(MusicPlayerView(activity!!))
            }
        }
    }

    private var prevTimerSetTime = 0L
    fun wake() {
        if (!MediaPresenter.isPlaying()) {
            return
        }
        val currentMillis = System.currentTimeMillis()
        if (currentMillis - prevTimerSetTime <= 1000) {
            return
        }
        prevTimerSetTime = currentMillis
        timer.start(0L)
    }

    fun reboot() {
        if (Values.LAUNCHER) {
            SPManager.save()
            exitProcess(0)
        } else {
            val context = BaseApplication.context
            activity?.finish()
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            BaseApplication.context.startActivity(intent)
            init()
        }
    }

    fun exit() {
        if (Values.LAUNCHER) {
            MediaPresenter.stop()
        } else {
            MediaPresenter.stop()
            SPManager.save()
            VolumeUtil.releaseSoundPool()
            activity?.stopService(Intent(activity, MediaService::class.java))
            activity?.finish()
            activity = null
            //exitProcess(0)
        }
    }

    fun reset() {
        SPManager.reset()
        MusicListTable.album.clear()
        MusicListTable.folder.clear()
        MusicListTable.artist.clear()
        MusicTable.favourite.clear()
    }

    fun initUI() {
        MediaStoreUtil.init()
        MediaPresenter.init()
        //AppsView.init()
    }


    fun init() {
        setLanguage()
        // 无论是否加载Activity都需要初始化
        //MediaStoreUtil.prepare()
        //AppWidget.updateRemoteViews(null)
    }
}