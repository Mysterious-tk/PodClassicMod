package com.example.podclassic.view

import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.RelativeLayout
import com.example.podclassic.R
import com.example.podclassic.`object`.Core
import com.example.podclassic.`object`.MediaPlayer
import com.example.podclassic.base.ScreenView
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.*
import com.example.podclassic.util.Values.DEFAULT_PADDING
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

    override fun enter() : Boolean {
        MediaPlayer.pause()
        return true
    }

    override fun enterLongClick() : Boolean {
        MediaPlayer.setPlayMode()
        setPlayMode()
        return true
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

    private val progressBar = SeekBar(context)

    private val volumeBar = SeekBar(context)
    private val index = TextView(context)
    private val stopTime = if (MediaPlayer.stopTime == 0L) null else TextView(context)
    private val image = ImageView(context)
    private val name = TextView(context)
    private val singer = TextView(context)
    private val album = TextView(context)
    private val lyric = if (SPManager.getBoolean(SPManager.SP_SHOW_LYRIC)) TextView(context) else null
    private val infoSet = LinearLayout(context)
    private val icon1 = android.widget.ImageView(context)
    private val icon2 = android.widget.ImageView(context)

    @SuppressLint("ObjectAnimatorBinding")
    fun setSeekBar(seekBar : SeekBar) {
        if (seekBar == progressBar) {
            //layoutTransition.setAnimator(LayoutTransition.APPEARING, ObjectAnimator.ofFloat(null, "translationX", -measuredWidth.toFloat(), 0f))
            layoutTransition.setAnimator(LayoutTransition.DISAPPEARING, ObjectAnimator.ofFloat(null, "translationX", 0f, measuredWidth.toFloat()))
            removeView(volumeBar)
            addView(progressBar)
        } else if (seekBar == volumeBar) {
            layoutTransition.setAnimator(LayoutTransition.APPEARING, ObjectAnimator.ofFloat(null, "translationX", measuredWidth.toFloat(), 0f))
            //layoutTransition.setAnimator(LayoutTransition.DISAPPEARING, ObjectAnimator.ofFloat(null, "translationX", 0f, -measuredWidth.toFloat()))
            removeView(progressBar)
            addView(volumeBar)
        }
    }

    private var stopTimer : Timer? = null

    init {
        layoutTransition = LayoutTransition()
            .apply {
                setDuration(300L)
                setStartDelay(LayoutTransition.APPEARING, 0)
                setStartDelay(LayoutTransition.DISAPPEARING, 0)
            }

        val padding = DEFAULT_PADDING * 2

        setPadding(padding, padding, padding, padding)

        volumeBar.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT).apply { addRule(ALIGN_PARENT_BOTTOM) }

        progressBar.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT).apply { addRule(ALIGN_PARENT_BOTTOM) }

        index.id = R.id.index
        image.id = R.id.image

        index.setPadding(0, 0, DEFAULT_PADDING * 3 , DEFAULT_PADDING)


        addView(index)//, LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT))
        addView(progressBar)

        image.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { addRule(BELOW, index.id) }
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

    }

    private fun setStopTimer() {
        if (stopTime != null && stopTimer == null) {
            stopTimer = Timer()
            stopTimer!!.schedule(object : TimerTask() {
                override fun run() {
                    ThreadUtil.runOnUiThread(Runnable {
                        stopTime.setBufferedText(((MediaPlayer.stopTime - System.currentTimeMillis()) / 1000 / 60 + 1).toString())
                    })
                }
            }, 100, 1000 * 60)
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
    private fun onStart() {
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
        onMediaChangeFinished()
    }


    private fun onStop() {
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

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            removeView(volumeBar)
            if (indexOfChild(progressBar) == -1) {
                addView(progressBar)
            }
            onStart()
        } else {
            onStop()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        onStop()
    }

    override fun slide(slideVal: Int) : Boolean {
        val currentTime = System.currentTimeMillis()

        if (currentTime - prevTimerSetTime >= 1000) {
            prevTimerSetTime = currentTime
            timer?.cancel()
            timer = Timer()
            timer?.schedule(object : TimerTask() {
                override fun run() {
                    ThreadUtil.runOnUiThread(Runnable {
                        if (indexOfChild(volumeBar) != -1) {
                            setSeekBar(progressBar)
                        }
                    })
                }
            }, 2000)
        }

        if (indexOfChild(volumeBar) == -1) {
            volumeBar.setCurrent(VolumeUtil.getCurrentVolume())
            setSeekBar(volumeBar)
            return true
        } else {
            var curVolume = VolumeUtil.getCurrentVolume()
            val maxVolume = VolumeUtil.maxVolume

            if ((curVolume == 0 && slideVal < 0) || (curVolume == maxVolume && slideVal > 0)) {
                return false
            }

            curVolume += slideVal
            VolumeUtil.setCurrentVolume(curVolume)
            volumeBar.setCurrent(curVolume)
            return true
        }
    }

    private var lyricSet : LyricUtil.LyricSet? = null

    @SuppressLint("SetTextI18n", "RtlHardcoded")
    override fun onMediaChangeFinished() {
        if (!hasWindowFocus()) {
            return
        }

        val music = MediaPlayer.getCurrent()
        if (music == null) {
            Core.removeView()
            return
        }

        val progress = MediaPlayer.getProgress()

        progressBar.setMax(MediaPlayer.getDuration())
        progressBar.setCurrent(progress)


        val bitmap = MediaPlayer.image
        if (bitmap == null) {
            name.gravity = Gravity.CENTER
            singer.gravity = Gravity.CENTER
            album.gravity = Gravity.CENTER
            lyric?.gravity = Gravity.CENTER
            image.setImageBitmap(null)
        } else {
            name.gravity = Gravity.LEFT
            singer.gravity = Gravity.LEFT
            album.gravity = Gravity.LEFT
            lyric?.gravity = Gravity.LEFT

            if (hasHeight) {
                loadImage(bitmap)
            } else {
                post { loadImage(bitmap) }
            }
        }
        if (lyric != null) {
            if (MediaPlayer.lyricSet != null) {
                lyricSet = MediaPlayer.lyricSet
                lyric.text = lyricSet?.getLyric(progress)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onMediaChange() {
        if (!hasWindowFocus()) {
            return
        }
        val music = MediaPlayer.getCurrent()

        if (music == null) {
            Core.removeView()
            return
        }

        progressBar.setMax(MediaPlayer.getDuration())
        progressBar.setCurrent(MediaPlayer.getProgress())

        name.text = music.name
        singer.text = music.singer
        album.text = music.album

        index.text = "${(MediaPlayer.getCurrentIndex() + 1)}/${MediaPlayer.getPlayListSize()}"

        lyricSet = null
        lyric?.text = null
        if (name.gravity == Gravity.CENTER) {
            image.setImageBitmap(null)
        } else {
            loadImage(Icons.EMPTY)
        }
    }

    private fun loadImage(bitmap: Bitmap) {
        if (!hasHeight) {
            return
        }
        val imageHeight = min(measuredHeight / 2f, measuredWidth / 2f - DEFAULT_PADDING * 4)
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
        progressBar.setCurrent(progress)
        if (lyric != null && lyricSet != null) {
            lyric.setBufferedText(lyricSet?.getLyric(progress))
        }
    }

    private var hasHeight = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        hasHeight = hasWindowFocus() && measuredHeight != 0
    }

    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_SINGLE
    }

    class ImageView(context: Context) : androidx.appcompat.widget.AppCompatImageView(context) {

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