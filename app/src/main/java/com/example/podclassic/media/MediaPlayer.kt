package com.example.podclassic.media

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import java.util.*
import kotlin.math.max
import kotlin.math.min

class MediaPlayer<E>(context: Context, mediaAdapter: MediaAdapter<E>) :
    MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener {

    private val playlist: Playlist<E> = Playlist(mediaAdapter)

    private var playState: PlayState = PlayState.STATE_STOP

    val isPlaying: Boolean
        get() = playState == PlayState.STATE_PLAYING

    val isPrepared: Boolean
        get() = playState == PlayState.STATE_PLAYING || playState == PlayState.STATE_PAUSE

    var enableAudioFocus: Boolean = true
        set(value) {
            if (value) {
                if (playState == PlayState.STATE_PLAYING) {
                    audioFocusManager.requestAudioFocus()
                }
            } else {
                audioFocusManager.abandonAudioFocus()
            }
            onDataChangeListener?.onAudioManagerChange()
            field = value
        }

    private val audioFocusManager: AudioFocusManager

    private val mediaPlayer: MediaPlayer

    private var equalizer: Equalizer? = null

    var equalizerId: Int = 0
        set(value) {
            equalizer?.let {
                if (value in 0 until it.numberOfPresets) {
                    it.usePreset(value.toShort())
                }
            }
            field = value
            onDataChangeListener?.onEqualizerChange()
        }

    private var presetList: Array<String?> = arrayOf()

    fun getPresetList(): Array<String?> {
        return presetList
    }

    interface OnDataChangeListener {
        fun onPlaylistChange() {}
        fun onPlayModeChange() {}
        fun onRepeatModeChange() {}
        fun onEqualizerChange() {}
        fun onAudioManagerChange() {}
        fun onStopTimeChange() {}

    }

    var onDataChangeListener: OnDataChangeListener? = null

    interface OnMediaChangeListener<E> {
        fun onMediaMetadataChange(mediaPlayer: com.example.podclassic.media.MediaPlayer<E>) {}
        fun onPlaybackStateChange(mediaPlayer: com.example.podclassic.media.MediaPlayer<E>) {}
    }

    private fun onMediaMetadataChange() {
        for (listener in onMediaChangeListeners) {
            listener.onMediaMetadataChange(this)
        }
    }

    private fun onPlayStateChange() {
        for (listener in onMediaChangeListeners) {
            listener.onPlaybackStateChange(this)
        }
    }

    private val onMediaChangeListeners = HashSet<OnMediaChangeListener<E>>()

    @Synchronized
    fun addOnMediaChangeListener(onMediaChangeListener: OnMediaChangeListener<E>) {
        onMediaChangeListeners.add(onMediaChangeListener)
    }

    @Synchronized
    fun removeOnMediaChangeListener(onMediaChangeListener: OnMediaChangeListener<E>) {
        onMediaChangeListeners.remove(onMediaChangeListener)
    }

    private var stopTimer: Timer? = null

    var stopTime = 0
        set(value) {
            //stopTime自减也会走这个set
            if (value == 0) {
                //stop timer
                if (stopTime > 1) {
                    stopTimer()
                }
            } else {
                // decrease ? ignore it
                if (value != stopTime - 1) {
                    stopTimer()
                    stopTimer = Timer().apply {
                        schedule(object : TimerTask() {
                            override fun run() {
                                updateTimer()
                            }
                        }, 60 * 1000L, 60 * 1000L)
                    }
                }
            }
            onDataChangeListener?.onStopTimeChange()
            field = value
        }

    private fun updateTimer() {
        if (stopTime == 1) {
            stop()
            stopTime = 0
        } else {
            stopTime--
        }
    }

    private fun stopTimer() {
        stopTimer?.cancel()
        stopTimer = null
    }

    fun release() {
        stop()
        mediaPlayer.release()
        onMediaChangeListeners.clear()
        equalizer?.release()
    }

    fun stop() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        }
        stopTime = 0
        mediaPlayer.reset()
        abandonAudioFocus()
        playlist.clear()
        playState = PlayState.STATE_STOP

        onPlayStateChange()
        onMediaMetadataChange()
    }

    private fun startMediaPlayer() {
        if (playlist.isEmpty()) {
            stop()
            return
        }
        onMediaMetadataChange()

        playState = PlayState.STATE_STOP
        mediaPlayer.reset()

        if (playlist.play(mediaPlayer)) {
            mediaPlayer.prepareAsync()
        } else {
            stop()
        }
    }

    fun next() {
        playlist.next()
        startMediaPlayer()
    }

    fun prev() {
        if (getProgress() > 3000) {
            seekTo(0)
        } else {
            playlist.prev()
            startMediaPlayer()
        }
    }

    fun playPause() {
        if (playState == PlayState.STATE_PLAYING) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        if (playState != PlayState.STATE_PLAYING) {
            if (playState == PlayState.STATE_PAUSE) {
                mediaPlayer.start()
                playState = PlayState.STATE_PLAYING
                requestAudioFocus()
                onPlayStateChange()
            } else {
                startMediaPlayer()
            }
        }
    }

    fun pause() {
        if (playState == PlayState.STATE_PLAYING) {
            abandonAudioFocus()
            playState = PlayState.STATE_PAUSE
            mediaPlayer.pause()
            onPlayStateChange()
        }
    }

    fun seekTo(position: Int) {
        mediaPlayer.seekTo(position)
        onPlayStateChange()
    }

    var defaultSeekDuration = 12000
    fun forward(): Boolean {
        if (!(playState == PlayState.STATE_PLAYING || playState == PlayState.STATE_PAUSE)) {
            return false
        }
        val progress = getProgress()
        val duration = getDuration()

        return if (progress == duration) {
            false
        } else {
            seekTo(min(progress + defaultSeekDuration, duration))
            true
        }
    }

    fun backward(): Boolean {
        if (!(playState == PlayState.STATE_PLAYING || playState == PlayState.STATE_PAUSE)) {
            return false
        }
        val progress = getProgress()
        return if (progress == 0) {
            false
        } else {
            seekTo(max(0, progress - defaultSeekDuration))
            true
        }
    }

    fun getDuration(): Int {
        return try {
            mediaPlayer.duration
        } catch (e: IllegalStateException) {
            0
        }
    }

    fun getProgress(): Int {
        return try {
            mediaPlayer.currentPosition
        } catch (e: IllegalStateException) {
            0
        }
    }

    fun getPlaylist(): ArrayList<E> {
        return playlist.getPlayList()
    }

    fun setPlayMode(playMode: PlayMode) {
        playlist.playMode = playMode
        onDataChangeListener?.onPlayModeChange()
    }

    fun getPlayMode(): PlayMode {
        return playlist.playMode
    }

    fun setRepeatMode(repeatMode: RepeatMode) {
        playlist.repeatMode = repeatMode
        onDataChangeListener?.onRepeatModeChange()
    }

    fun getRepeatMode(): RepeatMode {
        return playlist.repeatMode
    }

    fun getPlayState(): PlayState {
        return playState
    }

    fun add(e: E) {
        playlist.add(e)
        onDataChangeListener?.onPlaylistChange()
    }

    fun set(e: E) {
        playlist.add(e)
        playlist.setCurrent(e)
        startMediaPlayer()
        /*
        if (getCurrent() != e) {
            playlist.setCurrent(e)
        }
        if (playState == PlayState.STATE_STOP) {
                startMediaPlayer()
        } else if (playState == PlayState.STATE_PAUSE) {
            play()
        }

         */
    }

    fun remove(e: E) {
        playlist.remove(e)
        onDataChangeListener?.onPlaylistChange()
    }

    fun removeAt(index: Int) {
        playlist.removeAt(index)
        onDataChangeListener?.onPlaylistChange()
    }

    fun setIndex(index: Int) {
        playlist.index = index
        startMediaPlayer()
    }

    fun getIndex(): Int {
        return playlist.index
    }

    fun setPlaylist(list: ArrayList<E>) {
        playlist.setPlaylist(list)
        startMediaPlayer()
        onDataChangeListener?.onPlaylistChange()
    }

    fun setPlaylist(list: ArrayList<E>, index: Int) {
        playlist.setPlaylist(list, index)
        startMediaPlayer()
        onDataChangeListener?.onPlaylistChange()
    }

    fun shufflePlay(list: ArrayList<E>?) {
        if (list == null) {
            playlist.shufflePlay(playlist.getPlayList())
        } else {
            playlist.shufflePlay(list)
            onDataChangeListener?.onPlaylistChange()
        }
        startMediaPlayer()
    }

    fun shufflePlay(list: ArrayList<E>, index: Int) {
        playlist.shufflePlay(list, index)
        onDataChangeListener?.onPlaylistChange()
        startMediaPlayer()
    }

    fun getCurrent(): E? {
        return playlist.getCurrent()
    }

    override fun onCompletion(p0: MediaPlayer?) {
        playlist.onCompletion()
        startMediaPlayer()
    }

    override fun onPrepared(mp: MediaPlayer?) {
        mediaPlayer.start()
        playState = PlayState.STATE_PLAYING
        requestAudioFocus()
        //onMediaMetadataChange()
        onPlayStateChange()
    }

    override fun onError(p0: MediaPlayer?, what: Int, extra: Int): Boolean {
        if (what == 1 && extra == -2147483648) {
            next()
        }
        return true
    }

    private fun requestAudioFocus() {
        if (enableAudioFocus) {
            audioFocusManager.requestAudioFocus()
        }
    }

    private fun abandonAudioFocus() {
        if (enableAudioFocus) {
            audioFocusManager.abandonAudioFocus()
        }
    }

    interface MediaAdapter<E> {
        fun onLoadMedia(e: E, mediaPlayer: MediaPlayer): Boolean
    }


    init {
        this.audioFocusManager =
            AudioFocusManager(context, object : AudioFocusManager.OnAudioFocusChangeListener {
                private var wasPlaying = false
                override fun onAudioFocusGain() {
                    if (!enableAudioFocus) {
                        return
                    }
                    if (wasPlaying && playState != PlayState.STATE_PLAYING) {
                        play()
                    }
                }

                override fun onAudioFocusLoss() {
                    if (!enableAudioFocus) {
                        return
                    }
                    if (playState == PlayState.STATE_PLAYING) {
                        wasPlaying = true
                        pause()
                    } else {
                        wasPlaying = false
                    }
                }
            })
        this.mediaPlayer = MediaPlayer().apply {
            setOnCompletionListener(this@MediaPlayer)
            setOnErrorListener(this@MediaPlayer)
            setOnPreparedListener(this@MediaPlayer)
            setAudioAttributes(audioFocusManager.audioAttributes)
        }
        try {
            this.equalizer = Equalizer(1000, mediaPlayer.audioSessionId).apply {
                if (hasControl()) {
                    enabled = true
                }
            }
            this.equalizer?.let {
                this.presetList = arrayOfNulls(it.numberOfPresets.toInt())
                for (i in 0 until it.numberOfPresets) {
                    this.presetList[i] = it.getPresetName(i.toShort())
                }
            }
        } catch (e: Exception) {
            // 均衡器初始化失败，跳过均衡器设置
            this.equalizer = null
            this.presetList = arrayOf()
        }
    }

}

