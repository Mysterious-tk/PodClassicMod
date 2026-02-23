package com.example.podclassic.view

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.podclassic.R
import com.example.podclassic.base.Core
import com.example.podclassic.base.Observer
import com.example.podclassic.base.ScreenView
import com.example.podclassic.bean.Music
import com.example.podclassic.media.PlayMode
import com.example.podclassic.media.RepeatMode
import com.example.podclassic.service.MediaPresenter
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.LiveData
import com.example.podclassic.util.ThreadUtil
import com.example.podclassic.util.VolumeUtil
import com.example.podclassic.values.Colors
import com.example.podclassic.values.Icons
import com.example.podclassic.values.Strings
import com.example.podclassic.widget.ScreenLayout
import com.example.podclassic.widget.SeekBar
import java.util.*
import kotlin.math.abs
import kotlin.math.pow
import android.util.Log


class MusicPlayerView(context: Context) : FrameLayout(context), ScreenView {
    init {
        // 设置布局参数为 match_parent
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    private val observer = Observer()

    override fun getObserver(): Observer {
        return observer
    }


    private var timer: Timer? = null
    private var prevTimerSetTime = 0L

    override fun getTitle(): String? {
        return null
    }

    private var seekMode = false

    private val progressBar: SeekBar = SeekBar(context)
    private val volumeBar: SeekBar = SeekBar(context)

    private val screenLayout: ScreenLayout
    private val index: androidx.appcompat.widget.AppCompatTextView
    private val currentTime: androidx.appcompat.widget.AppCompatTextView
    private val remainingTime: androidx.appcompat.widget.AppCompatTextView
    private val image: com.example.podclassic.widget.ImageView
    private val title: androidx.appcompat.widget.AppCompatTextView
    private val artist: androidx.appcompat.widget.AppCompatTextView
    private val album: androidx.appcompat.widget.AppCompatTextView
    private val stopTime: androidx.appcompat.widget.AppCompatTextView?
    private val lyric: com.example.podclassic.widget.TextView?
    private val icon1: androidx.appcompat.widget.AppCompatImageView
    private val icon2: androidx.appcompat.widget.AppCompatImageView
    private val icon3: androidx.appcompat.widget.AppCompatImageView
    private var imageCenter = 0;

    private val progressTimer =
        com.example.podclassic.util.Timer(500L) { a -> ThreadUtil.runOnUiThread { onProgress(a.toInt()) } }

    

    private val container : ViewGroup

    init {
        Log.d("MusicPlayerView", "Init started")
        val view = LayoutInflater.from(context).inflate(R.layout.view_player, this, true)
        album = view.findViewById(R.id.tv_album)
        artist = view.findViewById(R.id.tv_artist)
        title = view.findViewById(R.id.tv_title)
        image = view.findViewById(R.id.image)
        
        Log.d("MusicPlayerView", "Views found: album=$album, artist=$artist, title=$title, image=$image")
        
        // 初始化所有视图
        index = view.findViewById(R.id.tv_index)
        
        // iPod Classic 风格的文本样式
        title.apply {
            setTextColor(Colors.text)
            setShadowLayer(0.5f, 0f, 1f, Colors.white)
            // 标准TextView没有scrollable属性，使用标准的跑马灯设置
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            isSelected = true
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(0, 0, 0, 0)
            gravity = Gravity.START
        }
        artist.apply {
            setTextColor(Colors.text)
            setPadding(0, 0, 0, 0)
            gravity = Gravity.START
        }
        album.apply {
            setTextColor(Colors.text)
            setPadding(0, 0, 0, 0)
            gravity = Gravity.START
        }
        index.apply {
            setPadding(0, 0, 0, 0)
            setTextColor(Colors.text)
            gravity = Gravity.START
        }

        // 当前播放时间和剩余时间（底部进度条区域）
        currentTime = view.findViewById<androidx.appcompat.widget.AppCompatTextView>(R.id.tv_current_time).apply {
            setTextColor(Colors.text)
        }
        remainingTime = view.findViewById<androidx.appcompat.widget.AppCompatTextView>(R.id.tv_remaining_time).apply {
            setTextColor(Colors.text)
        }
        
        Log.d("MusicPlayerView", "Time views found: index=$index, currentTime=$currentTime, remainingTime=$remainingTime")

        // 禁用SeekBar内置的时间显示，使用独立的TextView
        progressBar.textVisibility = View.GONE
        
        screenLayout = (view.findViewById(R.id.seek_bar) as ScreenLayout).apply { add(progressBar) }
        icon1 = view.findViewById(R.id.ic_play_mode)
        icon2 = view.findViewById(R.id.ic_repeat_mode)
        icon3 = view.findViewById(R.id.ic_stop_time)
        stopTime = view.findViewById<androidx.appcompat.widget.AppCompatTextView>(R.id.tv_stop_time).apply {
            setTextColor(Colors.text)
        }
        container = view.findViewById(R.id.container)
        lyric = if (SPManager.getBoolean(SPManager.SP_SHOW_LYRIC)) {
            view.findViewById<com.example.podclassic.widget.TextView>(R.id.tv_lyric).apply {
                scrollable = true
                typeface = Typeface.defaultFromStyle(Typeface.NORMAL)
                textSize = 12f
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
            }
        } else {
            null
        }
        
        observer.addLiveData(MediaPresenter.stopTime, object : LiveData.OnDataChangeListener {
            override fun onStart() { setStopTime() }
            override fun onDataChange() = ThreadUtil.runOnUiThread { setStopTime() }
        })

        observer.addLiveData(MediaPresenter.music, object : LiveData.OnDataChangeListener {
            override fun onStart() { onMusicChange() }
            override fun onDataChange() = ThreadUtil.runOnUiThread { onMusicChange() }
        })

        observer.addLiveData(MediaPresenter.playState, object : LiveData.OnDataChangeListener {
            override fun onStart() { onPlayStateChange() }
            override fun onDataChange() = ThreadUtil.runOnUiThread { onPlayStateChange() }
            override fun onStop() { progressTimer.pause() }
        })
        observer.addLiveData(MediaPresenter.playMode, object : LiveData.OnDataChangeListener {
            override fun onStart() { setPlayMode(); }
            override fun onDataChange() = ThreadUtil.runOnUiThread { setPlayMode(); }
        })


        //setPlayMode()
        //setStopTime()
        //onPlayStateChange()
        //onMusicChange()
        setVolumeBar()
    }
    private fun sgn(x: Float): Float {
        return when {
            x < 0f -> -1f
            x > 0f -> 1f
            else -> 0f
        }
    }
    override fun onMeasure(widthMeasureSec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSec, heightMeasureSpec)
        imageCenter = measuredWidth / 2
    }

    override fun enter(): Boolean {
        seekMode = !seekMode
        progressBar.setSeekMode(seekMode)
        if (seekMode) {
            setSeekBar(progressBar)
        } else {
            val seekProgress = progressBar.getProgress()
            MediaPresenter.seekTo(seekProgress)
            // 确认快进后立即更新时间显示，避免显示旧时间
            currentTime.text = formatTime(seekProgress)
            remainingTime.text = "-" + formatTime(progressBar.getMax() - seekProgress)
        }
        return true
    }

    override fun enterLongClick(): Boolean {
        MediaPresenter.nextPlayMode()
        return true
    }

    fun setSeekBar(seekBar: SeekBar) {
        if (screenLayout.curr() != seekBar) {
            if (screenLayout.stackSize() == 0) {
                screenLayout.add(seekBar)
            } else {
                screenLayout.remove()
            }
        }
    }

    private fun setStopTime() {
        val time = MediaPresenter.getStopTime()
        if (time == 0) {
            stopTime?.visibility = View.GONE
            icon3.visibility = View.GONE
        } else {
            stopTime?.visibility = View.VISIBLE
            icon3.visibility = View.VISIBLE
            stopTime?.text = "${time}分钟"
        }
    }

    private fun setPlayMode() {
        val playMode = when (MediaPresenter.getPlayMode()) {
            PlayMode.SHUFFLE -> Icons.PLAY_MODE_SHUFFLE.drawable
            PlayMode.SINGLE -> Icons.PLAY_MODE_SINGLE.drawable
            else -> null
        }
        if (SPManager.getInt(SPManager.SP_REPEAT_MODE) == RepeatMode.ALL.id) {
            if (playMode == null) {
                icon1.setImageDrawable(Icons.PLAY_MODE_REPEAT.drawable)
                icon2.setImageDrawable(null)
            } else {
                icon1.setImageDrawable(playMode)
                icon2.setImageDrawable(Icons.PLAY_MODE_REPEAT.drawable)
            }
        } else {
            icon1.setImageDrawable(playMode)
            icon2.setImageDrawable(null)
        }
    }

    private fun setVolumeBar() {
        volumeBar.apply {
            setMax(VolumeUtil.maxVolume)
            setLeftIcon(Icons.VOLUME_DOWN.drawable)
            setRightIcon(Icons.VOLUME_UP.drawable)
            textVisibility = GONE
        }
    }

    private val volumeBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            volumeBar.setCurrent(VolumeUtil.getCurrentVolume())
        }
    }

    private var broadcastReceiverRegistered = false
    private val intentFilter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")

    override fun onViewAdd() {
        Log.d("MusicPlayerView", "========== onViewAdd() started ==========")
        Log.d("MusicPlayerView", "MusicPlayerView width=$width, height=$height")
        Log.d("MusicPlayerView", "MusicPlayerView measuredWidth=$measuredWidth, measuredHeight=$measuredHeight")
        Log.d("MusicPlayerView", "Parent=${parent?.javaClass?.simpleName}, Parent size=${(parent as? ViewGroup)?.width}x${(parent as? ViewGroup)?.height}")
        
        if (!broadcastReceiverRegistered) {
            context.registerReceiver(volumeBroadcastReceiver, intentFilter)
            broadcastReceiverRegistered = true
            Log.d("MusicPlayerView", "Broadcast receiver registered")
        }
        
        // 获取根视图并记录其状态
        val rootView = getChildAt(0)
        Log.d("MusicPlayerView", "RootView=$rootView, RootView type=${rootView?.javaClass?.simpleName}")
        Log.d("MusicPlayerView", "RootView width=${rootView?.width}, height=${rootView?.height}")
        Log.d("MusicPlayerView", "RootView measuredWidth=${rootView?.measuredWidth}, measuredHeight=${rootView?.measuredHeight}")
        Log.d("MusicPlayerView", "RootView paddingTop=${rootView?.paddingTop}, paddingBottom=${rootView?.paddingBottom}")
        Log.d("MusicPlayerView", "RootView layoutParams=${rootView?.layoutParams}")
        
        // 记录container状态
        Log.d("MusicPlayerView", "Container width=${container.width}, height=${container.height}")
        Log.d("MusicPlayerView", "Container measuredWidth=${container.measuredWidth}, measuredHeight=${container.measuredHeight}")
        Log.d("MusicPlayerView", "Container layoutParams=${container.layoutParams}")
        
        // 记录图片状态
        Log.d("MusicPlayerView", "Image width=${image.width}, height=${image.height}")
        Log.d("MusicPlayerView", "Image y=${image.y}, top=${image.top}, bottom=${image.bottom}")
        
        // 重置专辑封面状态
        image.apply {
            scaleX = 1f
            scaleY = 1f
            rotationY = 15f
            z = 0f
            visibility = VISIBLE
            alpha = 1f
        }
        
        // 重置所有文本视图的状态
        listOf(title, artist, album, index, currentTime, remainingTime).forEach {
            it.alpha = 1f
            it.visibility = VISIBLE
        }
        
        // 延迟执行布局检查
        post {
            Log.d("MusicPlayerView", "---------- post() callback ----------")
            val postRootView = getChildAt(0) as? LinearLayout
            Log.d("MusicPlayerView", "After post - RootView width=${postRootView?.width}, height=${postRootView?.height}")
            Log.d("MusicPlayerView", "After post - RootView paddingTop=${postRootView?.paddingTop}, paddingBottom=${postRootView?.paddingBottom}")
            Log.d("MusicPlayerView", "After post - MusicPlayerView width=$width, height=$height")
            Log.d("MusicPlayerView", "After post - Container width=${container.width}, height=${container.height}")
            Log.d("MusicPlayerView", "After post - Container top=${container.top}, y=${container.y}")
            Log.d("MusicPlayerView", "After post - Image y=${image.y}, top=${image.top}")
            Log.d("MusicPlayerView", "---------- post() end ----------")
        }
        
        // 注意：onMusicChange() 和 onPlayStateChange() 会通过 Observer.onViewAdd() -> LiveData.onStart() 自动调用
        // 这里不需要手动调用，避免重复执行
        // 只需要更新进度显示
        onProgress(MediaPresenter.getProgress())
        Log.d("MusicPlayerView", "onProgress() called to update time display")

    }

    override fun onViewRemove() {
        if (broadcastReceiverRegistered) {
            context.unregisterReceiver(volumeBroadcastReceiver)
            broadcastReceiverRegistered = false
        }
    }

    override fun slide(slideVal: Int): Boolean {
        if (seekMode) {
            var progress = progressBar.getProgress()
            val duration = progressBar.getMax()

            if ((progress == 0 && slideVal < 0) || (progress == duration && slideVal > 0)) {
                return false
            }

            progress += slideVal * 2000
            progress = progress.coerceAtLeast(0).coerceAtMost(duration)
            progressBar.setCurrent(progress)
            // 拖动进度条时也更新时间显示
            currentTime.text = formatTime(progress)
            remainingTime.text = "-" + formatTime(duration - progress)
            return true
        } else {
            val currentTime = System.currentTimeMillis()

            if (currentTime - prevTimerSetTime >= 1000) {
                prevTimerSetTime = currentTime
                timer?.cancel()
                timer = Timer()
                timer?.schedule(object : TimerTask() {
                    override fun run() {
                        ThreadUtil.runOnUiThread {
                            setSeekBar(progressBar)
                        }
                    }
                }, 2000)
            }

            if (screenLayout.curr() == volumeBar) {
                var curVolume = VolumeUtil.getCurrentVolume()
                val maxVolume = VolumeUtil.maxVolume

                if ((curVolume == 0 && slideVal < 0) || (curVolume == maxVolume && slideVal > 0)) {
                    return false
                }

                curVolume += slideVal
                VolumeUtil.setCurrentVolume(curVolume)
                volumeBar.setCurrent(curVolume)
                return true
            } else {
                volumeBar.setCurrent(VolumeUtil.getCurrentVolume())
                setSeekBar(volumeBar)
                return true
            }
        }
    }

    private fun onMusicChange() {
        Log.d("MusicPlayerView", "onMusicChange() started")
        if (seekMode) {
            seekMode = false
            progressBar.setSeekMode(false)
        }
        val music = MediaPresenter.getCurrent()
        Log.d("MusicPlayerView", "Current music: $music")
        if (music == null) {
            Log.d("MusicPlayerView", "Music is null, waiting for media controller")
            // 延迟重试，不要移除视图
            postDelayed({  
                if (observer.enable) {
                    Log.d("MusicPlayerView", "Retrying onMusicChange()")
                    onMusicChange()
                }
            }, 1000)
            return
        }
        Log.d("MusicPlayerView", "Music data: title=${music.title}, artist=${music.artist}, album=${music.album}, duration=${music.duration}")
        loadImage(music)
        val duration = if (music.duration > 1) {
            music.duration.toInt()
        } else {
            MediaPresenter.getDuration()
        }
        progressBar.set(MediaPresenter.getProgress(), duration)

        

        // iPod Classic 风格：直接设置文本，不使用动画避免显示问题
        title.text = music.title
        title.alpha = 1f
        
        artist.text = music.artist
        artist.alpha = 1f
        
        album.text = music.album
        album.alpha = 1f

        index.text = "${(MediaPresenter.getIndex() + 1)}/${MediaPresenter.getPlaylist().size}"

        // 初始化时间显示 - 使用当前实际进度，避免显示00:00
        val currentProgress = MediaPresenter.getProgress()
        currentTime.text = formatTime(currentProgress)
        remainingTime.text = "-" + formatTime(duration - currentProgress)

        // 确保图片可见
        image.apply {
            visibility = VISIBLE
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
        }

        lyric?.setBufferedText(null)
    }

    private val empty : Bitmap?
    get() = if (image.height == 0) null else {
        Icons.EMPTY.let {

            val scaleWidth: Float = image.height / it.width.toFloat()
            val scaleHeight: Float = image.height / it.height.toFloat()
            val matrix = Matrix()
            matrix.postScale(scaleWidth, scaleHeight)
            val result = Bitmap.createBitmap(it, 0, 0, it.width, it.height, matrix, false)
            result
        }
    }

    private fun loadImage(music : Music) {
        Log.d("MusicPlayerView", "loadImage() started for music: ${music.title}")
        var bitmap: Bitmap? = null

        // 先显示占位图或保持可见
        Log.d("MusicPlayerView", "Setting image visibility to VISIBLE")
        image.visibility = VISIBLE
        Log.d("MusicPlayerView", "Setting placeholder bitmap: ${empty != null}")
        image.setImageBitmap(empty)

        ThreadUtil.asyncTask({
            bitmap = music.image
            Log.d("MusicPlayerView", "Bitmap loaded: ${bitmap != null}")
        }, {
            Log.d("MusicPlayerView", "Image load callback: bitmap=$bitmap")
            if (bitmap == null) {
                Log.d("MusicPlayerView", "Bitmap is null, using default placeholder")
                // 没有封面时使用默认占位图
                image.setImageResource(android.R.drawable.ic_media_play)
                title.gravity = Gravity.START
                title.setPadding(0, 0, 0, 0)
                artist.gravity = Gravity.START
                artist.setPadding(0, 0, 0, 0)
                album.gravity = Gravity.START
                album.setPadding(0, 0, 0, 0)
                index.gravity = Gravity.START
                index.setPadding(0, 0, 0, 0)
                lyric?.gravity = Gravity.START
            } else {
                Log.d("MusicPlayerView", "Bitmap is not null, setting to image view")
                // 设置专辑封面
                image.setImageBitmap(bitmap)
                image.visibility = VISIBLE

                title.gravity = Gravity.START
                title.setPadding(0, 0, 0, 0)
                artist.gravity = Gravity.START
                artist.setPadding(0, 0, 0, 0)
                album.gravity = Gravity.START
                album.setPadding(0, 0, 0, 0)
                index.gravity = Gravity.START
                index.setPadding(0, 0, 0, 0)
                lyric?.gravity = Gravity.START
                
                // 显示歌词区域（如果启用）
                lyric?.visibility = if (lyric?.text.isNullOrEmpty()) GONE else VISIBLE
            }
            Log.d("MusicPlayerView", "Image load completed")
        })
    }

    private fun onPlayStateChange() {
        val progress = MediaPresenter.getProgress()
        progressBar.set(progress, MediaPresenter.getDuration())
        if (MediaPresenter.isPlaying()) {
            progressTimer.start(progress.toLong())
        } else {
            progressTimer.pause()
        }
    }

    private fun onProgress(progress: Int) {
        if (!seekMode) {
            progressBar.setCurrent(progress)
            // 只在非拖动模式下更新时间显示，保持拖动后的时间显示方便确认
            val duration = MediaPresenter.getDuration()
            currentTime.text = formatTime(progress)
            remainingTime.text = "-" + formatTime(duration - progress)
        }
        lyric?.setBufferedText(MediaPresenter.getCurrent()?.lyric?.getLyric(progress))
    }
    
    // 格式化时间为 mm:ss 格式
    private fun formatTime(milliseconds: Int): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }


    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_SINGLE
    }
}
