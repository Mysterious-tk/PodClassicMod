package com.example.podclassic.activity

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Outline
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.example.podclassic.R
import com.example.podclassic.`object`.Core
import com.example.podclassic.`object`.MediaPlayer
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.fragment.SplashFragment
import com.example.podclassic.service.MediaPlayerService
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.*
import com.example.podclassic.view.MusicPlayerView
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    companion object {
        const val TOP_MARGIN = 24
    }

    private var statusBarHeight = 0

    private var mini = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initScreen()
        if ((Values.screenHeight.toFloat() / Values.screenWidth.toFloat()) < (6f / 5f)) {
            mini = true
        }
        if (mini) {
            setContentView(R.layout.activity_main_mini)
            MediaStoreUtil.prepare()
        } else {
            setContentView(R.layout.activity_main)
            initView()
            if (!SPManager.getBoolean(SPManager.SP_STARTED)) {
                SPManager.setBoolean(SPManager.SP_STARTED, true)
                SPManager.reset()
            }
            Core.bindActivity(findViewById(R.id.slide_controller), findViewById(R.id.screen), findViewById(R.id.title_bar), findViewById(R.id.dark_mode), this)
        }

        checkPermission()
    }

    private fun prepare() {
        if (mini) {
            return
        }
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.frame_layout, SplashFragment {
                Core.lock(true)
                //UI相关
                PinyinUtil.load()

                MediaStoreUtil.prepare()

                if (initMediaPlayer()) {
                    ThreadUtil.runOnUiThread {
                        Core.addView(MusicPlayerView(this))
                    }
                }
                Core.lock(false)
                Core.active = true
            })
            .commit()
    }

    override fun onBackPressed() {
        if (mini) {
            if (!Values.LAUNCHER) {
                Core.exit()
            }
            return
        }
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
        if (mini) {
            return
        }
        if (hasFocus) {
            Core.setNightMode(SPManager.NightMode.nightMode(SPManager.getInt(SPManager.NightMode.SP_NAME)))
        }
    }

    private fun initMediaPlayer() : Boolean {
        val uri = intent?.data
        val action = intent?.action
        val autoStop = SPManager.getInt(SPManager.AutoStop.SP_NAME)
        return when {
            action == MediaPlayerService.ACTION_SHUFFLE || (!Core.active && autoStop != 0) -> {
                MediaPlayer.shufflePlay()
                if (autoStop != SPManager.AutoStop.FOREVER_ID) {
                    MediaPlayer.scheduleToStop(SPManager.AutoStop.getMinute(autoStop))
                }
                true
            }
            uri != null -> {
                MediaPlayer.add(MediaStoreUtil.getMusic(FileUtil.uriToPath(uri)))
                true
            }
            else -> false
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
        val frameLayout = findViewById<FrameLayout>(R.id.frame_layout)
        val layoutParams = (frameLayout.layoutParams as LinearLayout.LayoutParams)
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


        frameLayout.clipToOutline = true

        //隐藏虚拟按键
        //window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN;
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        } else {
            prepare()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Core.exit()
        } else {
            prepare()
        }
    }
}

