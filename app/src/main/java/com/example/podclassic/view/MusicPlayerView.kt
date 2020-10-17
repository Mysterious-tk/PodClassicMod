package com.example.podclassic.view

import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.RelativeLayout
import com.example.podclassic.R
import com.example.podclassic.`object`.MediaPlayer
import com.example.podclassic.base.ScreenView
import com.example.podclassic.storage.SPManager
import com.example.podclassic.storage.SaveMusics
import com.example.podclassic.util.*
import com.example.podclassic.util.Values.DEFAULT_PADDING
import java.util.*


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
        when (MediaPlayer.getPlayMode()) {
            MediaPlayer.PLAY_MODE_SHUFFLE -> index.setRightIcon(Icons.PLAY_MODE_SHUFFLE.drawable)
            MediaPlayer.PLAY_MODE_SINGLE -> index.setRightIcon(Icons.PLAY_MODE_SINGLE.drawable)
            else -> index.setRightIcon(null)
        }
    }

    private val progressBar = SeekBar(context)

    private val volumeBar = SeekBar(context)
    private val index = TextView(context)
    private val stopTime = TextView(context)
    private val image = ImageView(context)
    private val name = TextView(context)
    private val singer = TextView(context)
    private val album = TextView(context)
    private val lyric = TextView(context)
    private val infoSet = LinearLayout(context)

    @SuppressLint("ObjectAnimatorBinding")
    fun setSeekBar(seekBar : SeekBar) {
        if (seekBar == progressBar) {
            layoutTransition.setAnimator(LayoutTransition.APPEARING, ObjectAnimator.ofFloat(null, "translationX", -measuredWidth.toFloat(), 0f))
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

    init {
        val layoutTransition = LayoutTransition()
        layoutTransition.setDuration(300L)
        layoutTransition.setStartDelay(LayoutTransition.APPEARING, 0)
        layoutTransition.setStartDelay(LayoutTransition.DISAPPEARING, 0)
        setLayoutTransition(layoutTransition)

        val padding = DEFAULT_PADDING * 2
        setPadding(padding, padding, padding, padding)

        val layoutParams = LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT)
        layoutParams.addRule(ALIGN_PARENT_BOTTOM)
        volumeBar.layoutParams = layoutParams

        val progressBarLayoutParams = LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT)
        progressBarLayoutParams.addRule(ALIGN_PARENT_BOTTOM)
        progressBar.layoutParams = progressBarLayoutParams

        val stopTimeLayoutParams = LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT)
        stopTimeLayoutParams.addRule(CENTER_HORIZONTAL)

        index.id = R.id.index
        image.id = R.id.image

        index.setPadding(0, 0, 0, DEFAULT_PADDING)
        stopTime.setPadding(0,0,0, DEFAULT_PADDING)

        addView(index, LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT))
        addView(stopTime, stopTimeLayoutParams)
        addView(progressBar)

        val imageLayoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        imageLayoutParams.addRule(BELOW, index.id)
        addView(image, imageLayoutParams)

        lyric.scrollable = true
        lyric.typeface = Typeface.defaultFromStyle(Typeface.NORMAL)
        lyric.textSize = 12f
        lyric.visibility = if (SPManager.getBoolean(SPManager.SP_SHOW_LYRIC)) VISIBLE else GONE
        val lyricLayoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lyricLayoutParams.setMargins(0, DEFAULT_PADDING / 2, 0, 0)

        infoSet.setPadding(DEFAULT_PADDING, DEFAULT_PADDING, DEFAULT_PADDING , 0)
        infoSet.orientation = LinearLayout.VERTICAL
        infoSet.addView(name)
        infoSet.addView(singer)
        infoSet.addView(album)
        infoSet.addView(lyric, lyricLayoutParams)

        val infoSetLayoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        infoSetLayoutParams.addRule(BELOW, index.id)
        infoSetLayoutParams.addRule(RIGHT_OF, image.id)

        addView(infoSet, infoSetLayoutParams)

        setVolumeBar()
        setPlayMode()
    }

    private var stopTimer : Timer? = null

    private fun setStopTimer() {
        if (MediaPlayer.stopTime != 0L) {
            if (stopTimer == null) {
                stopTimer = Timer()
                stopTimer?.schedule(object : TimerTask() {
                    override fun run() {
                        if (MediaPlayer.stopTime == 0L && stopTimer != null) {
                            stopTimer!!.cancel()
                            stopTimer = null
                            ThreadUtil.runOnUiThread(Runnable {
                                stopTime.setLeftIcon(null)
                                stopTime.text = ""
                            })
                        } else {
                            val time = ((MediaPlayer.stopTime - System.currentTimeMillis()) / 1000 / 60 + 1).toString()
                            ThreadUtil.runOnUiThread(Runnable {
                                if (stopTime.text != time) {
                                    stopTime.setLeftIcon(Icons.STOP_TIME.drawable)
                                    stopTime.text = time
                                }
                            })
                        }
                    }
                }, 100, 1000 * 60)
            }
        }
    }

    private fun cancelStopTimer() {
        stopTimer?.cancel()
        stopTimer = null
    }

    private fun setVolumeBar() {
        volumeBar.setMax(VolumeUtil.maxVolume)
        volumeBar.setLeftIcon(Icons.VOLUME_DOWN.drawable)
        volumeBar.setRightIcon(Icons.VOLUME_UP.drawable)
        volumeBar.textVisibility = GONE
    }

    private val volumeBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            volumeBar.setCurrent(VolumeUtil.getCurrentVolume())
        }
    }

    private var broadcastReceiverRegistered = false

    private fun onStart() {
        MediaPlayer.addOnMediaChangeListener(this)
        MediaPlayer.addOnProgressListener(this)

        val intentFilter = IntentFilter()
        intentFilter.addAction("android.media.VOLUME_CHANGED_ACTION")
        if (!broadcastReceiverRegistered) {
            context.registerReceiver(volumeBroadcastReceiver, intentFilter)
            broadcastReceiverRegistered = true
        }
        setStopTimer()
        onMediaChange()
    }


    private fun onStop() {
        MediaPlayer.removeOnProgressListener(this)
        MediaPlayer.removeOnMediaChangeListener(this)

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
        val sv = if (slideVal > 0) 1 else if (slideVal < 0) -1 else 0
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

            if ((curVolume == 0 && sv < 0) || (curVolume == maxVolume && sv > 0)) {
                return false
            }

            curVolume += sv
            VolumeUtil.setCurrentVolume(curVolume)
            volumeBar.setCurrent(curVolume)
            return true
        }
    }

    private var lyricSet : MediaMetadataUtil.LyricSet? = null

    @SuppressLint("SetTextI18n", "RtlHardcoded")
    override fun onMediaChange() {
        if (!hasWindowFocus()) {
            return
        }
        val progress = MediaPlayer.getProgress()
        progressBar.setCurrent(progress)
        progressBar.setMax(MediaPlayer.getDuration())

        val music = MediaPlayer.getCurrent()
        if (music == null) {
            name.text = Values.NULL
            singer.text = Values.NULL
            album.text = Values.NULL
            image.setImageBitmap(null)
            name.gravity = Gravity.CENTER
            singer.gravity = Gravity.CENTER
            album.gravity = Gravity.CENTER
            lyric.gravity = Gravity.CENTER
        } else {
            val bitmap = music.getImage()
            if (bitmap == null) {
                image.setImageBitmap(null)
                name.gravity = Gravity.CENTER
                singer.gravity = Gravity.CENTER
                album.gravity = Gravity.CENTER
                lyric.gravity = Gravity.CENTER
            } else {
                name.gravity = Gravity.LEFT
                singer.gravity = Gravity.LEFT
                album.gravity = Gravity.LEFT
                lyric.gravity = Gravity.LEFT
                if (hasHeight) {
                    loadImage(bitmap)
                } else {
                    post { loadImage(bitmap) }
                }
            }
            name.text = music.name
            singer.text = music.singer
            album.text = music.album

            if (SPManager.getBoolean(SPManager.SP_SHOW_LYRIC)) {
                lyricSet = music.getLyric()
                lyric.text = lyricSet?.getLyric(progress)
            }
        }

        index.text = (MediaPlayer.getCurrentIndex() + 1).toString() + "/" + MediaPlayer.getPlayListSize().toString()
    }

    private fun loadImage(bitmap: Bitmap) {
        if (!hasHeight) {
            return
        }
        val imageHeight = measuredHeight / 2f
        val scaleWidth : Float = imageHeight / bitmap.width
        val scaleHeight : Float= imageHeight / bitmap.height
        val matrix = Matrix()
        matrix.postScale(scaleWidth, scaleHeight)
        val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
        image.setImageBitmap(result)
    }

    override fun onPlayStateChange() {}

    override fun onProgress(progress: Int) {
        progressBar.setCurrent(progress)
        if (lyricSet != null) {
            val text = lyricSet?.getLyric(progress)
            if (lyric.text.toString() != text) {
                lyric.text = text
            }
        }
    }

    private var hasHeight = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        hasHeight = hasWindowFocus() && measuredHeight != 0
    }

    class ImageView(context: Context) : androidx.appcompat.widget.AppCompatImageView(context) {
        private var bitmap : Bitmap? = null

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

        override fun setImageBitmap(bm: Bitmap?) {
            if (bm == bitmap) {
                return
            }
            bitmap?.recycle()
            bitmap = bm
            super.setImageBitmap(bm)
        }
    }

    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_SINGLE
    }
}