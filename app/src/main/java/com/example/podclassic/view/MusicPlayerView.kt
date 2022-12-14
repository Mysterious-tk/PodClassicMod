package com.example.podclassic.view

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
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
import com.example.podclassic.values.Icons
import com.example.podclassic.values.Strings
import com.example.podclassic.widget.ScreenLayout
import com.example.podclassic.widget.SeekBar
import com.example.podclassic.widget.TextView
import java.util.*


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
        //image.visibility = GONE
        index = view.findViewById<TextView?>(R.id.tv_index).apply { setPadding(0, 0, 0, 0) }
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

        title.text = music.title
        artist.text = music.artist
        album.text = music.album

        index.text = "${(MediaPresenter.getIndex() + 1)}/${MediaPresenter.getPlaylist().size}"

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
                image.setImageBitmap(bitmap)
                image.visibility = VISIBLE

                title.gravity = Gravity.START
                artist.gravity = Gravity.START
                album.gravity = Gravity.START
                lyric?.gravity = Gravity.START
                /*
                bitmap?.apply {
                    val scaleWidth: Float = image.height / this.width.toFloat()
                    val scaleHeight: Float = image.height / this.height.toFloat()
                    val matrix = Matrix()
                    matrix.postScale(scaleWidth, scaleHeight)
                    val result = Bitmap.createBitmap(this, 0, 0, this.width, this.height, matrix, false)
                    image.setImageBitmap(result)
                }*/




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
