package com.example.podclassic.`object`

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.service.MediaPlayerService
import com.example.podclassic.storage.SPManager
import com.example.podclassic.storage.SaveMusics
import com.example.podclassic.util.AudioFocusManager
import com.example.podclassic.util.MediaUtil
import com.example.podclassic.util.ThreadUtil
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random


object MediaPlayer : MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener,
    MediaPlayer.OnErrorListener, AudioFocusManager.OnAudioFocusChangeListener {

    private const val PLAY_MODE_ORDER = 0
    const val PLAY_MODE_SINGLE = 1
    const val PLAY_MODE_SHUFFLE = 2

    private const val PLAY_MODE_ORDER_STRING = "顺序播放"
    private const val PLAY_MODE_SINGLE_STRING = "单曲循环"
    private const val PLAY_MODE_SHUFFLE_STRING = "随机播放"

    private const val BLACK_LIST = "LG"
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
        cancelTimer()
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        audioFocusManager.abandonAudioFocus()
        mediaPlayer.release()
        onMediaChangeListeners.clear()
        onProgressListeners.clear()

        if (!Build.BRAND.equals(BLACK_LIST, true) && equalizer.hasControl()) {
            equalizer.release()
        }
    }

    fun clearPlayList() {
        cancelTimer()

        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        audioFocusManager.abandonAudioFocus()
        playList.clear()
    }

    fun setPlayMode() {
        currentPlayMode++
        currentPlayMode %= 3
        SPManager.setInt(SPManager.SP_PLAY_MODE, currentPlayMode)
        if (currentPlayMode == PLAY_MODE_SHUFFLE) {
            RandomPlayer.reset()
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

    private val mediaPlayer = MediaPlayer()
    private val equalizer by lazy { Equalizer(1000, mediaPlayer.audioSessionId) }
    val equalizerList = ArrayList<String>()

    init {
        mediaPlayer.setOnCompletionListener(this)
        mediaPlayer.setOnPreparedListener(this)
        mediaPlayer.setOnErrorListener(this)
        mediaPlayer.setAudioAttributes(audioFocusManager.attribute)

        if (!Build.BRAND.equals(BLACK_LIST, true) && equalizer.hasControl()) {
            equalizer.enabled = true
            for (i in 0 until equalizer.numberOfPresets) {
                equalizerList.add(equalizer.getPresetName(i.toShort()))
            }
            setEqualizer(SPManager.getInt(SPManager.SP_EQUALIZER))
        }
    }

    fun setEqualizer(index: Int): Boolean {
        if (Build.BRAND.equals(BLACK_LIST, true)) {
            return false
        }
        return if (index in 0 until equalizerList.size) {
            SPManager.setInt(SPManager.SP_EQUALIZER, index)
            equalizer.usePreset(index.toShort())
            true
        } else {
            false
        }
    }

    interface OnMediaChangeListener {
        fun onMediaChange()
        fun onPlayStateChange()
    }

    private val onMediaChangeListeners = HashSet<OnMediaChangeListener>()

    fun addOnMediaChangeListener(onMediaChangeListener: OnMediaChangeListener): Boolean {
        return onMediaChangeListeners.add(onMediaChangeListener)
    }

    fun removeOnMediaChangeListener(onMediaChangeListener: OnMediaChangeListener): Boolean {
        return onMediaChangeListeners.remove(onMediaChangeListener)
    }

    interface OnProgressListener {
        fun onProgress(progress: Int)
    }

    private val onProgressListeners = HashSet<OnProgressListener>()

    fun addOnProgressListener(onProgressListener: OnProgressListener): Boolean {
        val result = onProgressListeners.add(onProgressListener)
        if (result && isPlaying && timer == null) {
            setTimer()
        }
        return result
    }

    fun removeOnProgressListener(onProgressListener: OnProgressListener): Boolean {
        val result = onProgressListeners.remove(onProgressListener)
        if (result && onProgressListeners.isEmpty() && timer != null) {
            cancelTimer()
        }
        return result
    }

    private var timer: Timer? = null

    private fun setTimer() {
        timer?.cancel()
        timer = null
        if (onProgressListeners.isEmpty()) {
            return
        }
        timer = Timer()
        timer!!.schedule(object : TimerTask() {
            override fun run() {
                onProgress()
            }
        }, 500, 1000)
    }

    private fun cancelTimer() {
        timer?.cancel()
        timer = null
    }

    private var playList = ArrayList<Music>()
        private set(value) {
            field = value.clone() as ArrayList<Music>
        }

    private var currentIndex = -1
    fun setPlayList(list: ArrayList<Music>, index: Int) {
        if (list.isEmpty() || index !in 0 until list.size) {
            return
        }
        if (list == playList) {
            setCurrent(index)
            return
        }
        playList = list
        if (currentPlayMode == PLAY_MODE_SHUFFLE) {
            RandomPlayer.reset()
        }
        currentIndex = index
        startMediaPlayer()
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
        setPlayList(list, Random.nextInt(list.size))
    }

    fun shufflePlay() {
        val list = if (SPManager.getBoolean(SPManager.SP_PLAY_ALL)) MediaUtil.musics else SaveMusics.loveList.getList()
        if (list.isNotEmpty()) {
            shufflePlay(list)
        }
    }

    fun add(music: Music) {
        val index = playList.indexOf(music)
        if (index == -1) {
            if (currentPlayMode == PLAY_MODE_SHUFFLE) {
                RandomPlayer.add(music)
            }
            playList.add(currentIndex + 1, music)
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
        if (currentPlayMode == PLAY_MODE_SHUFFLE) {
            RandomPlayer.remove(music)
        }
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

    fun setCurrent(index: Int) {
        if (index !in 0 until playList.size) {
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
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, stopTime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, stopTime, pendingIntent)
            }
        }
    }

    private fun startMediaPlayer() {
        if (playList.isEmpty() || currentIndex !in 0 until playList.size) {
            if (isPlaying) {
                mediaPlayer.stop()
                isPlaying = false
                isPrepared = false
                onPlayStateChange()
                onMediaChange()
            }
            return
        }
        isPrepared = false
        isPlaying = false

        val music = playList[currentIndex]

        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(music.path)
            mediaPlayer.prepareAsync()
            RandomPlayer.setPlayed(music)
        } catch (e: Exception) {
            onCompletion(mediaPlayer)
        }
    }

    fun next() {
        if (playList.isEmpty()) {
            return
        }
        if (currentPlayMode == PLAY_MODE_SHUFFLE) {
            currentIndex = RandomPlayer.next()
        } else {
            currentIndex++
            currentIndex %= playList.size
        }
        startMediaPlayer()
    }


    fun prev() {
        if (playList.isEmpty()) {
            return
        }
        if (currentPlayMode == PLAY_MODE_SHUFFLE) {
            currentIndex = RandomPlayer.prev()
        } else {
            currentIndex--
            if (currentIndex < 0) {
              currentIndex += playList.size
            }
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
        if (currentPlayMode != PLAY_MODE_SINGLE) {
            next()
        } else {
            startMediaPlayer()
        }
    }

    override fun onPrepared(p0: MediaPlayer?) {
        audioFocusManager.requestAudioFocus()
        isPrepared = true
        mediaPlayer.start()
        isPlaying = true
        onMediaChange()
        onPlayStateChange()
        setTimer()
    }

    override fun onError(p0: MediaPlayer?, what: Int, extra: Int): Boolean {
        if (what == 1 && extra == -2147483648) {
            next()
        }
        return true
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
        wasPlaying = false
    }

    override fun onAudioFocusLoss() {
        if (isPlaying) {
            wasPlaying = true
            pause()
        }
    }

    private object RandomPlayer {
        private class Pair(var music : Music, var played: Boolean)

        private var played = Stack<Music>()
        private var shuffled = ArrayList<Pair>()
        private var index = 0

        fun reset() {
            played.clear()
            shuffled.clear()
            for (music in playList) {
                shuffled.add(Pair(music, false))
            }
            shuffled.shuffle()
            index = 0
        }

        fun next() : Int {
            while (shuffled[index].played) {
                index++
                if (index >= playList.size) {
                    reset()
                    break
                }
            }
            return playList.indexOf(shuffled[index].music)
        }

        fun prev() : Int {
            played.pop()
            return if (!played.isEmpty()) {
                playList.indexOf(played.pop())
            } else {
                next()
            }
        }

        fun add(music : Music) {
            shuffled.add(0, Pair(music, false))
        }

        fun remove(music : Music) {
            played.remove(music)
            for (i in shuffled.indices) {
                if (shuffled[i].music == music) {
                    shuffled.removeAt(i)
                    break
                }
            }
        }

        fun setPlayed(music: Music) {
            played.add(music)
            for (i in shuffled.indices) {
                if (shuffled[i].music == music) {
                    shuffled[i].played = true
                    return
                }
            }
        }
    }

}

