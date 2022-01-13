package com.example.podclassic.`object`

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.service.MediaPlayerService
import com.example.podclassic.storage.SPManager
import com.example.podclassic.storage.SaveMusics
import com.example.podclassic.util.AudioFocusManager
import com.example.podclassic.util.MediaMetadataUtil
import com.example.podclassic.util.MediaMetadataUtil.Lyric
import com.example.podclassic.util.MediaStoreUtil
import com.example.podclassic.util.ThreadUtil
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.math.max
import kotlin.math.min


object MediaPlayer : MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, AudioFocusManager.OnAudioFocusChangeListener, MediaPlayer.OnPreparedListener {

    //audioFocus
    private val audioFocusManager = AudioFocusManager(this)
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
    //audioFocus

    private val mediaPlayer = MediaPlayer().apply {
        setOnCompletionListener(this@MediaPlayer)
        setOnErrorListener(this@MediaPlayer)
        setOnPreparedListener(this@MediaPlayer)
        setAudioAttributes(audioFocusManager.attribute)
    }

    //playmode
    private const val PLAY_MODE_ORDER = 0
    const val PLAY_MODE_SINGLE = 1
    const val PLAY_MODE_SHUFFLE = 2

    private const val PLAY_MODE_ORDER_STRING = "顺序播放"
    private const val PLAY_MODE_SINGLE_STRING = "单曲循环"
    private const val PLAY_MODE_SHUFFLE_STRING = "随机播放"

    private var currentPlayMode = SPManager.getInt(SPManager.SP_PLAY_MODE)

    fun setPlayMode() {
        currentPlayMode++
        currentPlayMode %= 3
        SPManager.setInt(SPManager.SP_PLAY_MODE, currentPlayMode)
        if (currentPlayMode == PLAY_MODE_SHUFFLE) {
            shufflePlayList()
        } else if (currentPlayMode == PLAY_MODE_ORDER) {
            val current = getCurrent()
            playList = ArrayList(orderedPlayList)
            currentIndex = playList.indexOf(current)
            onPlayStateChange()
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
    //playmode

    //equalizer
    val equalizerList = ArrayList<String>(10)
    private val equalizer = Equalizer(0, mediaPlayer.audioSessionId)

    fun setEqualizer(index: Int): Boolean {
        return if (index in 0 until equalizerList.size) {
            SPManager.setInt(SPManager.SP_EQUALIZER, index)
            equalizer.usePreset(index.toShort())
            true
        } else {
            false
        }
    }
    //equalizer

    //onMediaChangeListener
    interface OnMediaChangeListener {
        //确定下一首歌的music对象
        fun onMediaChange()
        //图片和歌词加载完成
        fun onMediaChangeFinished()
        //开始播放
        fun onPlayStateChange()

        fun onSeek(progress: Int)
    }

    private val onMediaChangeListeners = HashSet<OnMediaChangeListener>()

    fun addOnMediaChangeListener(onMediaChangeListener: OnMediaChangeListener) {
        onMediaChangeListeners.add(onMediaChangeListener)
    }

    @Synchronized fun removeOnMediaChangeListener(onMediaChangeListener: OnMediaChangeListener) {
        onMediaChangeListeners.remove(onMediaChangeListener)
    }
    //onMediaChangeListener

    //onProgressListener
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
    //onProgressListener

    //playlist
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

        playList = ArrayList(list)
        orderedPlayList = ArrayList(list)

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
        val temp = currentIndex
        currentIndex = playList.indexOf(current)
        if (index != temp) {
            onMediaChange()
        }
        onPlayStateChange()
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

        if (list != orderedPlayList) {
            playList = ArrayList(list)
            orderedPlayList = ArrayList(list)
        }

        playList.shuffle()
        currentIndex = 0
        startMediaPlayer()
    }

    fun shufflePlay() : Boolean {
        val list = if (SPManager.getBoolean(SPManager.SP_PLAY_ALL)) MediaStoreUtil.musics else SaveMusics.loveList.getList()
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
    //playlist

    //timer
    private var timer: Timer? = null
    private var timerCount : Int = 0
    private var duration : Int = 1
    private fun setTimer() {
        if (onProgressListeners.isEmpty()) {
            cancelTimer()
        } else if (timer == null) {
            timer = Timer()
            if (isPrepared) {
                timerCount = mediaPlayer.currentPosition
            }
            timer!!.schedule(object : TimerTask() {
                override fun run() {
                    timerCount += 500
                    onProgress(timerCount)
                }
            }, 500, 500)
        }
    }

    private fun updateTimer() {
        timerCount = if (isPrepared) {
            mediaPlayer.currentPosition
        } else {
            0
        }
    }

    private fun cancelTimer() {
        timer?.cancel()
        timer = null
    }
    //timer

    //stopTime
    var stopTime = 0L
    private var pendingIntent: PendingIntent? = null

    @SuppressLint("UnspecifiedImmutableFlag")
    fun scheduleToStop(min: Int) {
        val context = BaseApplication.context
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
            pendingIntent = PendingIntent.getService(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
            stopTime = System.currentTimeMillis() + min * 60 * 1000
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, stopTime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, stopTime, pendingIntent)
            }
        }
    }
    //stopTime

    var isPrepared = false
    var isPlaying = false

    init {
        MediaStoreUtil.prepare()
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
        }
        scheduleToStop(0)
        ThreadManager.clearTask()
        mediaPlayer.reset()
        audioFocusManager.abandonAudioFocus()
        playList.clear()
        orderedPlayList.clear()
        isPlaying = false
        isPrepared = false
        currentIndex = -1
        onPlayStateChange()
        onMediaChange()
    }

    private object ThreadManager {
        private val handler = Handler(Looper.getMainLooper())

        private var threadPoolExecutor = createThreadPoolExecutor()

        //ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, SynchronousQueue<Runnable>())

        private var next : Music? = null
        private var running = false

        fun addTask(music: Music) {
            synchronized(Core) {
                next = music
                if (!running) {
                    execTask()
                }
            }
        }

        fun clearTask() {
            try {
                threadPoolExecutor.shutdownNow()
            } catch (ignored : Exception) {}
            threadPoolExecutor = createThreadPoolExecutor()
        }


        private fun execTask() {
            running = true

            val music = next!!
            next = null

            threadPoolExecutor.execute {
                val image = MediaStoreUtil.getMusicImage(music)
                synchronized(Core) {
                    if (next != null) {
                        execTask()
                        return@execute
                    }
                }
                val lyrics = if (SPManager.getBoolean(SPManager.SP_SHOW_LYRIC)) MediaMetadataUtil.getLyric(music.path) else null
                synchronized(Core) {
                    if (next == null) {
                        running = false
                        MusicInfo.load(lyrics, image, music)
                        handler.post {
                            onMediaChangeFinished()
                        }
                    } else {
                        execTask()
                    }
                }
            }
        }

        private fun createThreadPoolExecutor() : ThreadPoolExecutor {
            return ThreadPoolExecutor(
                1,
                1,
                10000L,
                TimeUnit.MILLISECONDS,
                LinkedBlockingQueue()
            )
        }
    }

    private object MusicInfo {
        private var lyrics: ArrayList<Lyric>? = null
        var image: Bitmap? = null
            private set
        var music: Music? = null
            private set

        private var prevIndex = 0
        fun getLyric(time: Int): String {
            lyrics?.let {
                if (prevIndex >= 0 && prevIndex < it.size - 1 && it[prevIndex].time <= time && it[prevIndex + 1].time > time) {
                    return it[prevIndex].lyric
                } else if (prevIndex >= 0 && prevIndex < it.size - 2 && it[prevIndex + 1].time <= time && it[prevIndex + 2].time > time) {
                    return it[++prevIndex].lyric
                }
                var left = 0
                var right = it.size - 1
                while (left <= right) {
                    val mid = (left + right) / 2
                    if (it[mid].time > time) {
                        right = mid - 1
                    } else {
                        left = mid + 1
                    }
                }
                if (right < 0) {
                    right = 0
                }
                prevIndex = right
                return it[right].lyric
            }
            return ""
        }

        fun load(lyrics : ArrayList<Lyric>?, image : Bitmap?, music: Music) {
            synchronized(Core) {
                this.image = image
                this.lyrics = lyrics
                this.music = music
                prevIndex = 0
            }
        }
    }

    fun getImage() : Bitmap? {
        val current = getCurrent() ?: return null
        return if (MusicInfo.music == current) {
            MusicInfo.image
        } else {
            null
        }
    }

    fun getLyric(progress: Int) : String {
        return MusicInfo.getLyric(progress)
    }

    private fun startMediaPlayer() {
        if (playList.isEmpty() || currentIndex !in 0 until playList.size) {
            stop()
            return
        }

        onMediaChange()

        cancelTimer()

        isPrepared = false
        isPlaying = false
        mediaPlayer.reset()

        val music = playList[currentIndex]

        ThreadManager.addTask(music)
        mediaPlayer.setDataSource(music.path)

        ThreadUtil.newThread {
            mediaPlayer.prepareAsync()
        }

        BaseApplication.context.apply {
            startService(Intent(this, MediaPlayerService::class.java))
        }
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
        if (getProgress() > 3000) {
            seekTo(0)
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
        updateTimer()
        onProgress(timerCount)
        onSeek(timerCount)
    }

    fun forward() {
        if (!isPrepared) {
            return
        }
        seekTo(min(getDuration(), timerCount + 12000))
    }

    fun backward() {
        if (!isPrepared) {
            return
        }
        seekTo(max(0, timerCount - 12000))
    }

    fun getDuration() : Int {
        return duration
    }

    fun getProgress(): Int {
        return timerCount
    }

    override fun onCompletion(p0: MediaPlayer?) {
        if (currentPlayMode == PLAY_MODE_SINGLE) {
            startMediaPlayer()
            return
        }
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

    override fun onPrepared(mp: MediaPlayer?) {
        isPrepared = true
        audioFocusManager.requestAudioFocus()
        mediaPlayer.start()
        isPlaying = true
        duration = mediaPlayer.duration
        onPlayStateChange()
        setTimer()
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

    private fun onSeek(progress: Int) {
        for (item in onMediaChangeListeners) {
            item.onSeek(progress)
        }
    }

    private fun onProgress(progress: Int) {
        for (item in onProgressListeners) {
            ThreadUtil.runOnUiThread { item.onProgress(progress) }
        }
    }
}

