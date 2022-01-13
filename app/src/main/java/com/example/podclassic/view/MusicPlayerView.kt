package com.example.podclassic.view

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.util.Log
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Toast
import com.example.podclassic.R
import com.example.podclassic.`object`.Core
import com.example.podclassic.`object`.MediaPlayer
import com.example.podclassic.base.ScreenView
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.*
import com.example.podclassic.util.Values.DEFAULT_PADDING
import com.example.podclassic.widget.ScreenLayout
import com.example.podclassic.widget.SeekBar
import com.example.podclassic.widget.TextView
import java.util.*
import kotlin.math.min


@SuppressLint("RtlHardcoded")
class MusicPlayerView(context: Context) : RelativeLayout(context), ScreenView, MediaPlayer.OnMediaChangeListener, MediaPlayer.OnProgressListener {

    companion object { const val TITLE = "正在播放" }

    private var timer : Timer? = null
    private var prevTimerSetTime = 0L

    override fun getTitle(): String { return TITLE }

    private var seekMode = false

    private val progressBar = SeekBar(context)

    private val screenLayout = ScreenLayout(context)
    private val volumeBar = SeekBar(context)
    private val index = TextView(context)
    private val stopTime = if (MediaPlayer.stopTime < System.currentTimeMillis()) null else TextView(context)
    private val image = ImageView(context)
    private val name = TextView(context)
    private val singer = TextView(context)
    private val album = TextView(context)
    private val lyric = if (SPManager.getBoolean(SPManager.SP_SHOW_LYRIC)) TextView(context) else null
    private val infoSet = LinearLayout(context)
    private val icon1 = android.widget.ImageView(context)
    private val icon2 = android.widget.ImageView(context)

    private var stopTimer : Timer? = null

    init {
        val padding = DEFAULT_PADDING * 2

        setPadding(padding, padding, padding, padding)

        index.id = R.id.index
        image.id = R.id.image

        index.setPadding(0, 0, DEFAULT_PADDING * 3 , DEFAULT_PADDING)

        addView(index)//, LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT))
        screenLayout.apply {
            addView(progressBar)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT).apply { addRule(ALIGN_PARENT_BOTTOM) }
        }
        addView(screenLayout)

        image.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { addRule(BELOW, index.id) }
        image.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        addView(image)

        infoSet.apply {
            setPadding(DEFAULT_PADDING, DEFAULT_PADDING, DEFAULT_PADDING , 0)
            orientation = LinearLayout.VERTICAL
            addView(name)
            addView(singer)
            addView(album)
        }

        if (lyric != null) {
            lyric.apply {
                scrollable = true
                typeface = Typeface.defaultFromStyle(Typeface.NORMAL)
                textSize = 12f
            }
            lyric.layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { setMargins(0, DEFAULT_PADDING / 2, 0, 0) }
            infoSet.addView(lyric)
        }

        if (stopTime != null) {
            stopTime.setPadding(0,0,0, DEFAULT_PADDING)
            stopTime.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT).apply { addRule(CENTER_HORIZONTAL) }
            addView(stopTime)

            stopTime.setLeftIcon(Icons.STOP_TIME.drawable)
        }

        infoSet.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            addRule(BELOW, index.id)
            addRule(RIGHT_OF, image.id)
        }

        addView(infoSet)

        icon1.id = R.id.icon_1

        icon1.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { addRule(ALIGN_PARENT_RIGHT) }
        icon2.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { addRule(LEFT_OF, icon1.id) }

        icon1.setPadding(DEFAULT_PADDING / 2, 5,0, 0)
        icon2.setPadding(0, 5,0, 0)

        addView(icon1)
        addView(icon2)
        setVolumeBar()
        setPlayMode()

        //updateTextInfo()
        //onMediaChange()
        //onMediaChangeFinished()
    }

    override fun enter() : Boolean {
        seekMode = !seekMode
        progressBar.setSeekMode(seekMode)
        if (seekMode) {
            setSeekBar(progressBar)
        } else {
            MediaPlayer.seekTo(progressBar.getProgress())
        }
        return true
    }

    override fun enterLongClick() : Boolean {
        MediaPlayer.setPlayMode()
        setPlayMode()
        return true
    }

    @SuppressLint("ObjectAnimatorBinding")
    fun setSeekBar(seekBar : SeekBar) {
        if (screenLayout.currView() != seekBar) {
            if (screenLayout.stackSize() == 0) {
                screenLayout.addView(seekBar)
            } else {
                screenLayout.removeView()
            }
        }
    }

    private fun setPlayMode() {
        val playMode = when (MediaPlayer.getPlayMode()) {
            MediaPlayer.PLAY_MODE_SHUFFLE -> Icons.PLAY_MODE_SHUFFLE.drawable
            MediaPlayer.PLAY_MODE_SINGLE -> Icons.PLAY_MODE_SINGLE.drawable
            else -> null
        }
        if (SPManager.getBoolean(SPManager.SP_REPEAT)) {
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

    private fun setStopTimer() {
        if (stopTime != null && stopTimer == null) {
            stopTimer = Timer().apply {
                schedule(object : TimerTask() {
                    @SuppressLint("SetTextI18n")
                    override fun run() {
                        val time = MediaPlayer.stopTime - System.currentTimeMillis()
                        if (time > 0) {
                            ThreadUtil.runOnUiThread {
                                stopTime.text = (time / 1000 / 60 + 1).toString()
                            }
                        }
                    }
                }, 100, 60000)
            }
        }
    }


    private fun cancelStopTimer() {
        stopTimer?.cancel()
        stopTimer = null
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
    override fun onStart() {
        MediaPlayer.apply {
            addOnMediaChangeListener(this@MusicPlayerView)
            addOnProgressListener(this@MusicPlayerView)
        }

        if (!broadcastReceiverRegistered) {
            context.registerReceiver(volumeBroadcastReceiver, intentFilter)
            broadcastReceiverRegistered = true
        }
        setStopTimer()
        onMediaChange()
    }

    override fun onStop() {
        MediaPlayer.apply {
            removeOnProgressListener(this@MusicPlayerView)
            removeOnMediaChangeListener(this@MusicPlayerView)
        }

        if (broadcastReceiverRegistered) {
            context.unregisterReceiver(volumeBroadcastReceiver)
            broadcastReceiverRegistered = false
        }
        cancelStopTimer()
    }

    private var startFinished = false

    override fun onStartFinished() {
        startFinished = true
        onMediaChangeFinished()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            onStart()
            onStartFinished()
        } else {
            onStop()
        }
    }

    override fun slide(slideVal: Int) : Boolean {
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

            if (screenLayout.currView() == volumeBar) {
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

    @SuppressLint( "RtlHardcoded")
    override fun onMediaChangeFinished() {
        if (!hasWindowFocus() || !startFinished) {
            return
        }

        val music = MediaPlayer.getCurrent()
        if (music == null) {
            Core.removeView()
            return
        }

        val progress = MediaPlayer.getProgress()

        progressBar.set(progress, MediaPlayer.getDuration())
        lyric?.setBufferedText(MediaPlayer.getLyric(progress))

        loadImage()
    }

    @SuppressLint("SetTextI18n")
    private fun updateTextInfo() {
        progressBar.setMax(MediaPlayer.getDuration())
        progressBar.setCurrent(MediaPlayer.getProgress())

        val music = MediaPlayer.getCurrent()

        name.text = music?.name
        singer.text = music?.singer
        album.text = music?.album

        index.text = "${(MediaPlayer.getCurrentIndex() + 1)}/${MediaPlayer.getPlayListSize()}"

    }

    override fun onMediaChange() {
        if (!hasWindowFocus()) {
            return
        }

        if (seekMode) {
            seekMode = false
            progressBar.setSeekMode(false)
        }
        val music = MediaPlayer.getCurrent()

        if (music == null) {
            Core.removeView()
            return
        }

        updateTextInfo()

        lyric?.setBufferedText(null)

        if (name.gravity == Gravity.CENTER) {
            image.setImageBitmap(null)
        } else {
            loadDefaultImage()
        }
    }

    private val defaultImage by lazy {
        val bitmap = Icons.EMPTY
        val scaleWidth : Float = imageHeight / bitmap.width.toFloat()
        val scaleHeight : Float= imageHeight / bitmap.height.toFloat()
        val matrix = Matrix()
        matrix.postScale(scaleWidth, scaleHeight)
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
    }

    private fun loadDefaultImage() {
        if (!hasWindowFocus()) {
            return
        }
        image.setImageBitmap(defaultImage)
    }

    private fun loadImage() {
        if (!hasWindowFocus()) {
            return
        }

        val bitmap = MediaPlayer.getImage()

        if (bitmap == null) {
            name.gravity = Gravity.CENTER
            singer.gravity = Gravity.CENTER
            album.gravity = Gravity.CENTER
            lyric?.gravity = Gravity.CENTER
            image.setImageBitmap(null)
            return
        } else {
            name.gravity = Gravity.LEFT
            singer.gravity = Gravity.LEFT
            album.gravity = Gravity.LEFT
            lyric?.gravity = Gravity.LEFT
        }

        val scaleWidth : Float = imageHeight / bitmap.width.toFloat()
        val scaleHeight : Float= imageHeight / bitmap.height.toFloat()
        val matrix = Matrix()
        matrix.postScale(scaleWidth, scaleHeight)
        val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)

        image.setImageBitmap(result)
    }

    @SuppressLint("SetTextI18n")
    override fun onPlayStateChange() {
        progressBar.setMax(MediaPlayer.getDuration())
        index.text = "${(MediaPlayer.getCurrentIndex() + 1)}/${MediaPlayer.getPlayListSize()}"
    }

    override fun onProgress(progress: Int) {
        if (!seekMode) {
            progressBar.setCurrent(progress)
        }
        lyric?.setBufferedText(MediaPlayer.getLyric(progress))
    }

    override fun onSeek(progress: Int) {
        onProgress(progress)
    }

    private var imageHeight = Values.screenWidth / 2.7f

    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_SINGLE
    }

    private class ImageView(context: Context) : androidx.appcompat.widget.AppCompatImageView(context) {

        @SuppressLint("DrawAllocation")
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val rect = canvas.clipBounds
            rect.bottom--
            rect.right--
            rect.top++
            rect.left++
            val paint = Paint()
            paint.color = Colors.text
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRect(rect, paint)
        }
    }
}