package com.example.podclassic.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.podclassic.R
import com.example.podclassic.base.Core
import com.example.podclassic.fragment.SplashFragment
import com.example.podclassic.service.MediaPresenter
import com.example.podclassic.service.MediaService
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.MediaStoreUtil
import com.example.podclassic.util.ThreadUtil
import com.example.podclassic.values.Values
import com.example.podclassic.view.MusicPlayerView

class MainActivity : AppCompatActivity() {

    companion object {
        const val TOP_MARGIN = 24
    }

    private var statusBarHeight = 0


    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val uri = intent?.data
        if (uri != null) {
            val music = MediaStoreUtil.getMusicFromUri(uri)
            MediaPresenter.set(music)
            ThreadUtil.runOnUiThread {
                Core.addView(MusicPlayerView(this))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initScreen()
        setContentView(R.layout.activity_main)
        initView()
        if (!SPManager.getBoolean(SPManager.SP_STARTED)) {
            SPManager.setBoolean(SPManager.SP_STARTED, true)
            SPManager.reset()
        }
        Core.bindActivity(
            findViewById(R.id.slide_controller),
            findViewById(R.id.screen),
            findViewById(R.id.dark_mode),
            this
        )
        checkPermission()
    }

    private fun prepare() {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.screen, SplashFragment {
                Core.lock(true)
                Core.initUI()
                initMediaPlayer()
                Core.lock(false)
            })
            .commit()
    }

    /*
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                Core.down()
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                Core.up()
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                MediaPresenter.prev()
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                MediaPresenter.next()
            }
            KeyEvent.KEYCODE_DEL -> {
                Core.removeView()
            }
            KeyEvent.KEYCODE_ENTER -> {
                Core.enter()
            }
            KeyEvent.KEYCODE_BACK -> {
                //Core.home()
            }
            else -> {
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                //Core.down()
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                //Core.up()
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                MediaPresenter.backward()
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                MediaPresenter.forward()
            }
            KeyEvent.KEYCODE_DEL -> {
                Core.home()
            }
            KeyEvent.KEYCODE_ENTER -> {
                Core.enter()
            }
            KeyEvent.KEYCODE_BACK -> {
                Core.home()
            }
            else -> {
            }
        }
        return super.onKeyUp(keyCode, event)
    }

     */

    override fun onBackPressed() {
        if (Values.LAUNCHER) {
            if (Core.lock) {
                return
            }
            Core.removeView()
        } else {
            if (!Core.removeView()) {
                Core.exit()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            Core.setNightMode()
        }
    }

    private fun initMediaPlayer() {
        when (intent?.action) {
            MediaService.ACTION_MAIN -> {
                return
            }
            MediaService.ACTION_SHUFFLE -> {
                MediaPresenter.shufflePlay()
                ThreadUtil.runOnUiThread {
                    Core.addView(MusicPlayerView(this))
                }
                return
            }
        }
        val uri = intent?.data
        if (uri != null) {
            onNewIntent(intent)
            return
        }

        val autoStop = SPManager.getInt(SPManager.AutoStop.SP_NAME)
        if (!MediaPresenter.isPlaying() && autoStop != 0) {
            MediaPresenter.shufflePlay()
            if (autoStop != SPManager.AutoStop.FOREVER_ID) {
                MediaPresenter.setStopTime(SPManager.AutoStop.getMinute(autoStop))
            }
            ThreadUtil.runOnUiThread {
                Core.addView(MusicPlayerView(this))
            }
        }
    }

    private fun initScreen() {
        val resourceId: Int = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = resources.getDimensionPixelSize(resourceId)
        }

        val displayMetrics = resources.displayMetrics

        Values.screenWidth = displayMetrics.widthPixels
        Values.screenHeight = displayMetrics.heightPixels
    }

    private fun initView() {
        /*
        val layoutParams = slide_controller.layoutParams as RelativeLayout.LayoutParams

        when {
            screenRatio < 16f / 9f -> {
                layoutParams.leftMargin = layoutParams.leftMargin * 7 / 6
                layoutParams.rightMargin = layoutParams.rightMargin * 7 / 6
                layoutParams.topMargin /= 2
                layoutParams.bottomMargin /= 4
            }
            screenRatio == 16f / 9f -> {
                layoutParams.topMargin /= 2
                layoutParams.bottomMargin /= 2
            }
            screenRatio <= 17f / 9f -> {
                layoutParams.bottomMargin /= 2
            }
            screenRatio >= 21f / 10f -> {
                layoutParams.topMargin = layoutParams.topMargin * 2
            }
            else -> {
                layoutParams.topMargin = layoutParams.topMargin * 3 / 2
            }
        }

        (frame_layout.layoutParams as RelativeLayout.LayoutParams).topMargin = statusBarHeight + TOP_MARGIN
         */


        val linearLayout = findViewById<ViewGroup>(R.id.layout_rectangle)
        val layoutParams = (linearLayout.layoutParams as LinearLayout.LayoutParams)
        val screenRatio = Values.screenHeight / Values.screenWidth
        val topMargin = statusBarHeight + when {
            screenRatio <= 16f / 9f -> {
                0
            }
            screenRatio <= 17f / 9f -> {
                TOP_MARGIN / 2
            }
            screenRatio >= 21f / 10f -> {
                TOP_MARGIN * 2
            }
            else -> {
                TOP_MARGIN
            }
        }

        layoutParams.topMargin = topMargin

        //linearLayout.clipToOutline = true


        //隐藏虚拟按键
        //window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN;
    }

    fun setColor(color: Int) {
        window.statusBarColor = color
        window.navigationBarColor = color
        findViewById<View>(R.id.main_layout)?.setBackgroundColor(color)
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ),
                1
            )
        } else {
            prepare()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Core.exit()
        } else {
            prepare()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 重新计算屏幕尺寸
        initScreen()
        // 重新初始化布局，适应新的屏幕方向
        initView()
        // 刷新界面，确保所有视图正确显示
        Core.refresh()
    }
}

