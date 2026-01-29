package com.example.podclassic.view

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
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
import com.example.podclassic.widget.TextView
import java.util.*
import kotlin.math.abs
import kotlin.math.pow


class MusicPlayerView(context: Context) : FrameLayout(context), ScreenView {

    private val observer = Observer()

    override fun getObserver(): Observer {
        return observer
    }


    private var timer: Timer? = null
    private var prevTimerSetTime = 0L

    override fun getTitle(): String {
        return Strings.NOW_PLAYING
    }

    private var seekMode = false

    private val progressBar: SeekBar = SeekBar(context)
    private val volumeBar: SeekBar = SeekBar(context)

    private val screenLayout: ScreenLayout
    private val index: TextView
    private val image: com.example.podclassic.widget.ImageView
    private val title: TextView
    private val artist: TextView
    private val album: TextView
    private val stopTime: TextView?
    private val lyric: TextView?
    private val icon1: androidx.appcompat.widget.AppCompatImageView
    private val icon2: androidx.appcompat.widget.AppCompatImageView
    private var imageCenter = 0;

    private val progressTimer =
        com.example.podclassic.util.Timer(500L) { a -> ThreadUtil.runOnUiThread { onProgress(a.toInt()) } }

    private val container : ViewGroup

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.view_player, this, false)
            .apply { addView(this) }
        album = view.findViewById(R.id.tv_album)
        artist = view.findViewById(R.id.tv_artist)
        title = view.findViewById(R.id.tv_title)
        image = view.findViewById(R.id.image)
        
        // iPod Classic 风格的文本样式
        title.apply {
            setTextColor(Colors.text)
            setShadowLayer(0.5f, 0f, 1f, Colors.white)
        }
        artist.apply {
            setTextColor(Colors.main)
        }
        album.apply {
            setTextColor(Colors.text_secondary)
        }
        
        index = view.findViewById<TextView?>(R.id.tv_index).apply { 
            setPadding(0, 0, 0, 0)
            setTextColor(Colors.text_secondary)
        }
        screenLayout = (view.findViewById(R.id.seek_bar) as ScreenLayout).apply { add(progressBar) }
        icon1 = view.findViewById(R.id.ic_play_mode)
        icon2 = view.findViewById(R.id.ic_repeat_mode)
        stopTime = view.findViewById(R.id.tv_stop_time)
        container = view.findViewById(R.id.container)
        lyric = if (SPManager.getBoolean(SPManager.SP_SHOW_LYRIC)) {
            (view.findViewById(R.id.tv_lyric) as TextView).apply {
                scrollable = true
                typeface = Typeface.defaultFromStyle(Typeface.NORMAL)
                textSize = 12f
            }
        } else {
            null
        }

        observer.addLiveData(MediaPresenter.music, object : LiveData.OnDataChangeListener {
            override fun onStart() { onMusicChange() }
            override fun onDataChange() = ThreadUtil.runOnUiThread { onMusicChange() }
        })

        observer.addLiveData(MediaPresenter.stopTime, object : LiveData.OnDataChangeListener {
            override fun onStart() { setStopTime() }
            override fun onDataChange() = ThreadUtil.runOnUiThread { setStopTime() }
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
        val imageWidth = measuredHeight / 4 * 3
        val halfImageWidth = imageWidth / 2
        val imageBottom = measuredHeight / 2 + halfImageWidth
        val centerX = (image.left + image.right) / 2
        val centerY = imageBottom - halfImageWidth

        imageCenter = measuredWidth / 2
        val scale = -abs(imageCenter - centerX) / 4
        image.animate().scaleX(0.8f).scaleY(0.9f)

        val temp = (imageCenter - (image.left + image.right) / 2).toFloat()
        if (temp == 0f) {
            image.rotationY = 0f
        } else {
            val x = temp / width.toFloat()
            image.rotationY = sgn(x) * (1f / (1f + 3f.pow(-18f * abs(x))) - 0.5f) * 40f
        }
        image.z = abs(temp * 3)
/*
        val viewOutlineProvider: ViewOutlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(View : View?, outline: Outline) {
                outline.setRoundRect(0, 0, image.getWidth(), image.getHeight(), 5f)
            }
        }
        image.setOutlineProvider(viewOutlineProvider)
        image.setClipToOutline(true)
*/


    }

    override fun enter(): Boolean {
        seekMode = !seekMode
        progressBar.setSeekMode(seekMode)
        if (seekMode) {
            setSeekBar(progressBar)
        } else {
            MediaPresenter.seekTo(progressBar.getProgress())
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
            stopTime?.visibility = GONE
        } else {
            stopTime?.visibility = VISIBLE
            stopTime?.text = time.toString()
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
        if (!broadcastReceiverRegistered) {
            context.registerReceiver(volumeBroadcastReceiver, intentFilter)
            broadcastReceiverRegistered = true
        }
        //onMusicChange()
        //onPlayStateChange()

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
        if (seekMode) {
            seekMode = false
            progressBar.setSeekMode(false)
        }
        val music = MediaPresenter.getCurrent()
        if (music == null) {
            postDelayed({
                if (observer.enable && MediaPresenter.getCurrent() == null) {
                    Core.removeView()
                    return@postDelayed
                }
            }, 1000)
            return
        }
        loadImage(music)
        val duration = if (music.duration > 1) {
            music.duration.toInt()
        } else {
            MediaPresenter.getDuration()
        }
        progressBar.set(MediaPresenter.getProgress(), duration)

        // iPod Classic 风格：带动画的文本更新
        title.text = music.title
        title.alpha = 0f
        title.animate().alpha(1f).setDuration(400).start()
        
        artist.text = music.artist
        artist.alpha = 0f
        artist.animate().alpha(1f).setDuration(400).setStartDelay(100).start()
        
        album.text = music.album
        album.alpha = 0f
        album.animate().alpha(1f).setDuration(400).setStartDelay(200).start()

        index.text = "${(MediaPresenter.getIndex() + 1)}/${MediaPresenter.getPlaylist().size}"
        
        // 添加专辑封面进入动画
        image.apply {
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(500)
                .setInterpolator(android.view.animation.OvershootInterpolator())
                .start()
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
        var bitmap: Bitmap? = null

        image.setImageBitmap(empty)

        ThreadUtil.asyncTask({
            bitmap = music.image
        }, {
            if (bitmap == null) {
                image.visibility = GONE
                title.gravity = Gravity.CENTER
                artist.gravity = Gravity.CENTER
                album.gravity = Gravity.CENTER
                lyric?.gravity = Gravity.CENTER
                //image.setImageBitmap(null)
                container.requestLayout()
            } else {
                // 设置专辑封面并添加圆角效果
                image.setImageBitmap(bitmap)
                image.visibility = VISIBLE

                title.gravity = Gravity.START
                artist.gravity = Gravity.START
                album.gravity = Gravity.START
                lyric?.gravity = Gravity.START
                
                // 显示歌词区域（如果启用）
                lyric?.visibility = if (lyric?.text.isNullOrEmpty()) GONE else VISIBLE
            }

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
        }
        lyric?.setBufferedText(MediaPresenter.getCurrent()?.lyric?.getLyric(progress))
    }


    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_SINGLE
    }
}
