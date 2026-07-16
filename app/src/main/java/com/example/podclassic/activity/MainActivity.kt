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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
            if (music != null) {
                MediaPresenter.set(music)
                ThreadUtil.runOnUiThread {
                    Core.addView(MusicPlayerView(this))
                }
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
        // 根据当前选择的主题来决定绑定哪个SlideController到Core
        val isIpod3rdTheme = SPManager.getInt(SPManager.Theme.SP_NAME) == SPManager.Theme.IPOD_3RD.id
        
        Core.bindActivity(
            if (isIpod3rdTheme) {
                findViewById<View>(R.id.slide_controller_3rd)
            } else {
                findViewById<View>(R.id.slide_controller)
            },
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
                // 启动MediaService
                val intent = Intent(this@MainActivity, MediaService::class.java)
                try {
                    startService(intent)
                } catch (e: Exception) {
                    // Android 12+ 后台限制，尝试使用 startForegroundService
                    try {
                        startForegroundService(intent)
                    } catch (e2: Exception) {
                        android.util.Log.e("MainActivity", "Failed to start service: ${e2.message}")
                    }
                }
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

    fun initView() {
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
        val slideController = findViewById<View>(R.id.slide_controller)
        val slideController3rd = findViewById<View>(R.id.slide_controller_3rd)
        val slideControllerParams = (slideController.layoutParams as LinearLayout.LayoutParams)
        
        // 检查是否选择了iPod 3rd主题
        val isIpod3rdTheme = SPManager.getInt(SPManager.Theme.SP_NAME) == SPManager.Theme.IPOD_3RD.id
        
        // 检查是否是横屏模式且当前显示的是CoverFlowView、MainView或MusicListView
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val currentViewName = try {
            Core.getView()::class.java.simpleName
        } catch (e: Exception) {
            ""
        }
        // CoverFlow 已迁移到 Compose，兼容旧类名以免后续切换实现时布局退化。
        val isCoverFlowView = currentViewName == "CoverFlowView" ||
            currentViewName == "ComposeCoverFlowView"
        val isMainView = currentViewName == "MainView"
        val isMusicListView = currentViewName == "MusicListView"
        val shouldFullScreen = isCoverFlowView || isMainView || isMusicListView
        
        if (isLandscape && shouldFullScreen) {
            // 横屏且是CoverFlowView、MainView或MusicListView，全屏显示
            layoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT
            layoutParams.topMargin = 0
            layoutParams.leftMargin = 0
            layoutParams.rightMargin = 0
            layoutParams.bottomMargin = 0
            slideControllerParams.height = 0
            slideController3rd.layoutParams.height = 0
            // 高度变为 0 后也隐藏控制器，避免配置切换期间继续触发绘制。
            slideController.visibility = View.GONE
            slideController3rd.visibility = View.GONE
            // 移除 iPod 屏幕容器的圆角底板，避免横屏四周露出黑框。
            linearLayout.background = null
        } else {
            // 其他情况，保持默认布局
            // 使用dp单位设置高度，确保在不同分辨率屏幕上显示一致
            layoutParams.height = resources.getDimensionPixelSize(
                if (isIpod3rdTheme) {
                    R.dimen.layout_rectangle_height_ipod_3rd
                } else {
                    R.dimen.layout_rectangle_height
                }
            )
            // 全局隐藏状态栏，不再为系统栏额外预留高度。
            val topMargin = TOP_MARGIN
            layoutParams.topMargin = topMargin
            val horizontalCardMargin = resources.getDimensionPixelSize(
                R.dimen.player_card_horizontal_margin
            )
            layoutParams.leftMargin = horizontalCardMargin
            layoutParams.rightMargin = horizontalCardMargin
            layoutParams.bottomMargin = 0
            slideControllerParams.height = LinearLayout.LayoutParams.MATCH_PARENT
            slideController3rd.layoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT
            // 非全屏布局只显示当前主题对应的控制器。
            slideController.visibility = if (isIpod3rdTheme) View.GONE else View.VISIBLE
            slideController3rd.visibility = if (isIpod3rdTheme) View.VISIBLE else View.GONE
            linearLayout.setBackgroundResource(R.drawable.round_rectangle)
        }

        // 所有页面隐藏顶部状态栏；CoverFlow 横屏进一步隐藏导航栏进入沉浸式全屏。
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
            if (isLandscape && isCoverFlowView) {
                hide(WindowInsetsCompat.Type.navigationBars())
            } else {
                show(WindowInsetsCompat.Type.navigationBars())
            }
        }

        // 将包含屏幕和控制轮的整个主内容放入前摄开孔/刘海安全区。
        // 这样横屏时位于左侧或右侧的摄像头也不会遮挡控制器。
        ViewCompat.setOnApplyWindowInsetsListener(linearLayout, null)
        val contentLayout = linearLayout.parent as ViewGroup
        ViewCompat.setOnApplyWindowInsetsListener(contentLayout) { view, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            if (view.paddingLeft != cutout.left ||
                view.paddingTop != cutout.top ||
                view.paddingRight != cutout.right ||
                view.paddingBottom != cutout.bottom
            ) {
                view.setPadding(cutout.left, cutout.top, cutout.right, cutout.bottom)
            }
            insets
        }
        ViewCompat.requestApplyInsets(contentLayout)

        //linearLayout.clipToOutline = true


        //隐藏虚拟按键
        //window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN;
    }

    fun setColor(color: Int) {
        window.statusBarColor = color
        window.navigationBarColor = color
        findViewById<View>(R.id.main_layout)?.setBackgroundColor(color)
    }

    // 重新绑定控制器，确保主题切换后控制器仍然可以触摸
    fun rebindController() {
        val isIpod3rdTheme = SPManager.getInt(SPManager.Theme.SP_NAME) == SPManager.Theme.IPOD_3RD.id
        val controllerView = if (isIpod3rdTheme) {
            findViewById<View>(R.id.slide_controller_3rd)
        } else {
            findViewById<View>(R.id.slide_controller)
        }
        
        Core.bindActivity(
            controllerView,
            findViewById(R.id.screen),
            findViewById(R.id.dark_mode),
            this
        )
    }

    private fun checkPermission() {
        // Android 13 及以上版本使用 READ_MEDIA_AUDIO 权限
        val READ_MEDIA_AUDIO = "android.permission.READ_MEDIA_AUDIO"
        val isAndroid13OrHigher = android.os.Build.VERSION.SDK_INT >= 33 // TIRAMISU is API 33
        
        val permissions = if (isAndroid13OrHigher) {
            // Android 13 及以上版本
            arrayOf(
                READ_MEDIA_AUDIO
            )
        } else {
            // Android 12 及以下版本
            arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
        
        val permissionToCheck = if (isAndroid13OrHigher) {
            READ_MEDIA_AUDIO
        } else {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        
        if (ContextCompat.checkSelfPermission(
                this,
                permissionToCheck
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                permissions,
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
