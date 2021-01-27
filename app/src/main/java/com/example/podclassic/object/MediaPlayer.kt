package com.example.podclassic.`object`

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.ThumbnailUtils
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.service.MediaPlayerService
import com.example.podclassic.storage.SPManager
import com.example.podclassic.storage.SaveMusics
import com.example.podclassic.util.*
import java.util.*
import java.util.concurrent.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.math.max
import kotlin.math.min


object MediaPlayer : MediaPlayer.OnCompletionListener,
    MediaPlayer.OnErrorListener, AudioFocusManager.OnAudioFocusChangeListener,
    MediaPlayer.OnPreparedListener {

    private const val PLAY_MODE_ORDER = 0
    const val PLAY_MODE_SINGLE = 1
    const val PLAY_MODE_SHUFFLE = 2

    private const val PLAY_MODE_ORDER_STRING = "顺序播放"
    private const val PLAY_MODE_SINGLE_STRING = "单曲循环"
    private const val PLAY_MODE_SHUFFLE_STRING = "随机播放"

    /*
    private val NORMAL = shortArrayOf(0,0,0,0,0,0,0,0,0,0)
    private val CLASSICAL = shortArrayOf(0,8,8,4,0,0,0,0,2,2)
    private val DANCE = shortArrayOf(-1.5, 3, 4,1,-1.5,-1.5,0,0,4,4)
    private val FLAT = shortArrayOf(0,0,0,0,0,0,0,-1,-2,0)
    private val FOLK = shortArrayOf(0, 2, 0, 0, 1, 4, 5, 3, 0, 1)
    private val METAL = shortArrayOf(-5.5,0,0,0,0,0,2,0,2,0)
    private val HIP_HOP = shortArrayOf(5,5,4,0,-1,1,3,0,3,4)
    private val JAZZ = shortArrayOf(0,0,0,4,4,4,0,2,3,4)
    private val POP = shortArrayOf(3,1,0,-1.5,-3.5,-3.5,-1.5,0,1,2)
    private val ROCK = shortArrayOf(-1.5, 0,2,4,-1.5,-1.5, 0,0,4,4)
     */
    /*

    private val NORMAL = shortArrayOf(0, 0, 0, 0, 0)
    private val CLASSICAL = shortArrayOf(533, 400, 0, 66, 200)
    private val DANCE = shortArrayOf(183, 116, -100, 133, 400)
    private val FLAT = shortArrayOf(0, 0, 0, -100, -100)
    private val FOLK = shortArrayOf(66, 33, 333, 266, 50)
    private val METAL = shortArrayOf(-183, 0, 66, 133, 100)
    private val HIP_HOP = shortArrayOf(466, 100, 100, 200, 350)
    private val JAZZ = shortArrayOf(0, 266, 266, 166, 350)
    private val POP = shortArrayOf(144, -166, -283, -16, 150)
    private val ROCK = shortArrayOf(16, 150, -100, 133, 400)
    private val EQ = arrayOf(NORMAL, CLASSICAL, DANCE, FLAT, FOLK, METAL, HIP_HOP, JAZZ, POP, ROCK)

     */

    private var currentPlayMode = SPManager.getInt(SPManager.SP_PLAY_MODE)

    private val audioFocusManager = AudioFocusManager(this)

    fun release() {
        stop()
        mediaPlayer.release()
        onMediaChangeListeners.clear()
        onProgressListeners.clear()

        equalizer.release()
    }

    fun stop() {
        cancelTimer()
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            mediaPlayer.reset()
        }
        audioFocusManager.abandonAudioFocus()
        playList.clear()
        isPlaying = false
        isPrepared = false
        currentIndex = -1
        onPlayStateChange()
        onMediaChange()
    }

    fun setPlayMode() {
        currentPlayMode++
        currentPlayMode %= 3
        SPManager.setInt(SPManager.SP_PLAY_MODE, currentPlayMode)
        if (currentPlayMode == PLAY_MODE_SHUFFLE) {
            shufflePlayList()
        } else if (currentPlayMode == PLAY_MODE_ORDER) {
            val current = getCurrent()
            playList = orderedPlayList.clone() as ArrayList<Music>
            currentIndex = playList.indexOf(current)
            onMediaChange()
        }
    }

    fun getPlayMode(): Int {
        return currentPlayMode
    }

    fun getPlayModeString(): String {
        return when (currentPlayMode) {
            PLAY_MODE_SHUFFLE -> PLAY_MODE_SHUFFLE_STRING
            PLAY_MODE_ORDER -> PLAY_MODE_ORDER_STRING
            PLAY_MODE_SINGLE -> PLAY_MODE_SINGLE_STRING
            else -> ""
        }
    }

    var isPrepared = false
    var isPlaying = false

    val equalizerList = ArrayList<String>(10)


    private val mediaPlayer = MediaPlayer().apply {
        setOnCompletionListener(this@MediaPlayer)
        setOnErrorListener(this@MediaPlayer)
        setOnPreparedListener(this@MediaPlayer)
        setAudioAttributes(audioFocusManager.attribute)
    }
    private val equalizer = Equalizer(0, mediaPlayer.audioSessionId)

    init {
        equalizer.apply {
            if (hasControl()) {
                enabled = true
                for (i in 0 until numberOfPresets) {
                    equalizerList.add(getPresetName(i.toShort()))
                }
                setEqualizer(SPManager.getInt(SPManager.SP_EQUALIZER))
            }
        }
    }


    fun setEqualizer(index: Int): Boolean {
        return if (index in 0 until equalizerList.size) {
            SPManager.setInt(SPManager.SP_EQUALIZER, index)
            equalizer.usePreset(index.toShort())
            true
        } else {
            false
        }
    }

    interface OnMediaChangeListener {
        //确定下一首歌的music对象
        fun onMediaChange()
        //图片和歌词加载完成, 开始播放
        fun onMediaChangeFinished()
        fun onPlayStateChange()
    }

    private val onMediaChangeListeners = HashSet<OnMediaChangeListener>()

    fun addOnMediaChangeListener(onMediaChangeListener: OnMediaChangeListener) {
        onMediaChangeListeners.add(onMediaChangeListener)
    }

    fun removeOnMediaChangeListener(onMediaChangeListener: OnMediaChangeListener) {
        onMediaChangeListeners.remove(onMediaChangeListener)
    }

    interface OnProgressListener {
        fun onProgress(progress: Int)
    }

    private val onProgressListeners = HashSet<OnProgressListener>()

    fun addOnProgressListener(onProgressListener: OnProgressListener) {
        onProgressListeners.add(onProgressListener)
        if (onProgressListeners.isNotEmpty() && isPlaying && timer == null) {
            setTimer()
        }
    }

    fun removeOnProgressListener(onProgressListener: OnProgressListener) {
        onProgressListeners.remove(onProgressListener)
        if (onProgressListeners.isEmpty() && timer != null) {
            cancelTimer()
        }
    }

    private var timer: Timer? = null

    private fun setTimer() {
        if (onProgressListeners.isEmpty()) {
            cancelTimer()
        } else if (timer == null) {
            timer = Timer()
            timer!!.schedule(object : TimerTask() {
                override fun run() {
                    onProgress()
                }
            }, 500, 1000)
        }
    }

    private fun cancelTimer() {
        timer?.cancel()
        timer = null
    }

    private var playList = ArrayList<Music>()

    private var orderedPlayList = ArrayList<Music>()

    private var currentIndex = -1
    fun setPlayList(list: ArrayList<Music>, index: Int) {
        if (list.isEmpty() || index !in 0 until list.size) {
            return
        }
        if (list == orderedPlayList) {
            setCurrent(list[index])
            return
        }
        playList = list.clone() as ArrayList<Music>
        orderedPlayList = list.clone() as ArrayList<Music>

        if (currentPlayMode == PLAY_MODE_SHUFFLE) {
            shufflePlayList(index)
        } else {
            currentIndex = index
        }
        startMediaPlayer()
    }

    private fun shufflePlayList(index: Int = currentIndex) {
        if (index !in 0 until playList.size) {
            return
        }
        val current = playList[index]
        playList.shuffle()
        currentIndex = playList.indexOf(current)
        onMediaChange()
    }


    fun getPlayListSize(): Int {
        return playList.size
    }

    fun getPlayList(): ArrayList<Music> {
        return playList
    }

    fun shufflePlay(list: ArrayList<Music>) {
        currentPlayMode = PLAY_MODE_SHUFFLE
        SPManager.setInt(SPManager.SP_PLAY_MODE, currentPlayMode)
        playList = list.clone() as ArrayList<Music>
        orderedPlayList = list.clone() as ArrayList<Music>
        playList.shuffle()
        currentIndex = 0
        startMediaPlayer()
    }

    fun shufflePlay() : Boolean {
        val list = if (SPManager.getBoolean(SPManager.SP_PLAY_ALL)) MediaUtil.musics else SaveMusics.loveList.getList()
        return if (list.isNotEmpty()) {
            shufflePlay(list)
            true
        } else {
            false
        }
    }

    fun add(music: Music) {
        val index = playList.indexOf(music)
        if (index == -1) {
            playList.add(currentIndex + 1, music)
            orderedPlayList.add(currentIndex + 1, music)
            setCurrent(currentIndex + 1)
        } else {
            setCurrent(index)
        }
    }

    fun remove(index: Int) {
        if (index !in 0 until playList.size) {
            return
        }
        val music = playList.removeAt(index)
        orderedPlayList.remove(music)

        if (index == currentIndex) {
            if (currentIndex >= playList.size) {
                currentIndex--
            }
            startMediaPlayer()
        } else if (index < currentIndex) {
            currentIndex--
        }
    }

    fun getCurrentIndex(): Int {
        return currentIndex
    }

    fun setCurrent(music: Music) {
        val index = playList.indexOf(music)
        setCurrent(index)
    }

    fun setCurrent(index: Int) {
        if ((index !in 0 until playList.size) || (index == currentIndex && isPlaying)) {
            return
        }
        if (index == currentIndex && isPrepared && !isPlaying) {
            pause()
        }
        currentIndex = index
        startMediaPlayer()
    }

    fun getCurrent(): Music? {
        if (currentIndex in 0 until playList.size) {
            return playList[currentIndex]
        }
        return null
    }

    var stopTime = 0L
    private var pendingIntent: PendingIntent? = null

    fun scheduleToStop(min: Int) {
        val context = BaseApplication.getContext()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
        if (min == 0) {
            stopTime = 0
            return
        } else {
            val intent = Intent(context, MediaPlayerService::class.java)
            intent.action = MediaPlayerService.ACTION_STOP
            pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            stopTime = System.currentTimeMillis() + min * 60 * 1000
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    stopTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, stopTime, pendingIntent)
            }
        }
    }

    private object ThreadManager {
        private val handler = Handler(Looper.getMainLooper())

        private val threadPoolExecutor = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue())
        //ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, SynchronousQueue<Runnable>())

        private var next : Music? = null

        private var running = false

        fun addTask(music : Music) {
            next = music
            execTask()
        }

        private fun execTask() {
            if (next == null) {
                running = false
                return
            }
            if (running) {
                return
            }

            val music = next!!
            next = null
            doInBackground(Runnable {
                running = true
                loadImage(music)
                if (next != null) {
                    running = false
                    return@Runnable
                }
                lyricSet = LyricUtil.getLyric(music)
                running = false
            }, Runnable {
                if (next == null) {
                    onMediaChangeFinished()
                } else {
                    execTask()
                }
            })
            return
        }

        private fun doInBackground(doInBackground: Runnable, onFinish: Runnable) {
            threadPoolExecutor.execute {
                doInBackground.run()
                handler.post(onFinish)
            }
        }
    }

    private fun startMediaPlayer() {
        if (playList.isEmpty() || currentIndex !in 0 until playList.size) {
            stop()
            return
        }

        onMediaChange()

        isPrepared = false
        mediaPlayer.reset()
        isPlaying = false

        val music = playList[currentIndex]

        ThreadManager.addTask(music)

        try {
            mediaPlayer.setDataSource(music.path)
            mediaPlayer.prepareAsync()
        } catch (ignored: Exception) {
            onCompletion(mediaPlayer)
        }

    }

    override fun onPrepared(mp: MediaPlayer?) {
        isPrepared = true
        audioFocusManager.requestAudioFocus()
        mediaPlayer.start()
        isPlaying = true
        onPlayStateChange()
        setTimer()
    }

    var image : Bitmap? = null
    var lyricSet : LyricUtil.LyricSet? = null

    private fun loadImage(music: Music) {
        synchronized(this) {
            if (this.image?.isRecycled == false) {
                this.image?.recycle()
            }
            this.image = null
        }

        val mediaMetadataRetriever = MediaMetadataRetriever()
        mediaMetadataRetriever.setDataSource(music.path)
        val byteArray = mediaMetadataRetriever.embeddedPicture
        mediaMetadataRetriever.release()
        val image = if (byteArray == null || byteArray.isEmpty()) {
            null
        } else {
            ThumbnailUtils.extractThumbnail(BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size), Values.IMAGE_WIDTH, Values.IMAGE_WIDTH)
        }
        this.image = image
    }


    fun next() {
        if (playList.isEmpty()) {
            return
        }
        currentIndex++
        currentIndex %= playList.size
        startMediaPlayer()
    }


    fun prev() {
        if (playList.isEmpty()) {
            return
        }
        currentIndex--
        if (currentIndex < 0) {
            currentIndex += playList.size
        }
        startMediaPlayer()
    }

    fun pause() {
        if (isPlaying) {
            audioFocusManager.abandonAudioFocus()
            cancelTimer()
            isPlaying = false
            mediaPlayer.pause()
            onPlayStateChange()
        } else {
            if (isPrepared) {
                mediaPlayer.start()
                isPlaying = true
                audioFocusManager.requestAudioFocus()
                setTimer()
                onPlayStateChange()
            } else {
                startMediaPlayer()
            }
        }
    }

    fun seekTo(position: Int) {
        mediaPlayer.seekTo(position)
    }

    fun forward() {
        if (!isPrepared) {
            return
        }
        mediaPlayer.seekTo(min(getDuration(), getProgress() + 12000))
        onProgress()
    }

    fun backward() {
        if (!isPrepared) {
            return
        }
        mediaPlayer.seekTo(max(0, getProgress() - 12000))
        onProgress()
    }

    fun getDuration(): Int {
        return mediaPlayer.duration
    }

    fun getProgress(): Int {
        return mediaPlayer.currentPosition
    }

    override fun onCompletion(p0: MediaPlayer?) {
        currentIndex ++
        if (currentIndex == playList.size) {
            if (SPManager.getBoolean(SPManager.SP_REPEAT)) {
                currentIndex = 0
                startMediaPlayer()
            } else {
                stop()
            }
        } else {
            startMediaPlayer()
        }
    }

    override fun onError(p0: MediaPlayer?, what: Int, extra: Int): Boolean {
        if (what == 1 && extra == -2147483648) {
            next()
        }
        return true
    }

    private fun onMediaChangeFinished() {
        for (item in onMediaChangeListeners) {
            item.onMediaChangeFinished()
        }
    }

    private fun onMediaChange() {
        for (item in onMediaChangeListeners) {
            item.onMediaChange()
        }
    }

    private fun onPlayStateChange() {
        for (item in onMediaChangeListeners) {
            item.onPlayStateChange()
        }
    }

    private fun onProgress() {
        for (item in onProgressListeners) {
            ThreadUtil.runOnUiThread(Runnable { item.onProgress(mediaPlayer.currentPosition) })
        }
    }

    private var wasPlaying = false

    override fun onAudioFocusGain() {
        if (wasPlaying && !isPlaying) {
            pause()
        }
    }

    override fun onAudioFocusLoss() {
        if (isPlaying) {
            wasPlaying = true
            pause()
        } else {
            wasPlaying = false
        }
    }

}

