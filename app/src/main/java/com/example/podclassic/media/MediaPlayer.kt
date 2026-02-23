package com.example.podclassic.media

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.media.MediaCodec
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.Equalizer
import android.media.audiofx.NoiseSuppressor
import java.util.*
import kotlin.math.max
import kotlin.math.min

class MediaPlayer<E>(context: Context, private val mediaAdapter: MediaAdapter<E>) :
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
    private var automaticGainControl: AutomaticGainControl? = null
    private var tomSteadyProcessor: TomSteadyProcessor? = null
    
    // 音量控制相关
    private var isInitialVolumePhase: Boolean = false
    private var initialVolumeCounter: Int = 0
    private val INITIAL_VOLUME_SECONDS: Int = 3 // 初始音量阶段秒数 (3秒)
    private val initialVolumeIncrement: Float = 0.9f / INITIAL_VOLUME_SECONDS // 音量增量

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

    var agcEnabled: Boolean = false
        set(value) {
            automaticGainControl?.let {
                it.enabled = value
            }
            field = value
            onDataChangeListener?.onAgcChange()
        }

    fun isAgcAvailable(): Boolean {
        return AutomaticGainControl.isAvailable()
    }

    var tomSteadyEnabled: Boolean = false
        set(value) {
            // 确保TomSteadyProcessor已初始化
            if (tomSteadyProcessor == null) {
                initTomSteadyProcessor()
            }
            tomSteadyProcessor?.setParameters(enabled = value)
            field = value
            onDataChangeListener?.onTomSteadyChange()
        }

    fun initTomSteadyProcessor() {
        tomSteadyProcessor = TomSteadyProcessor(mediaPlayer.audioSessionId)
        tomSteadyProcessor?.init()
    }

    fun setTomSteadyParameters(
        targetLevel: Float? = null,
        maxGain: Float? = null,
        minGain: Float? = null,
        attackTime: Float? = null,
        releaseTime: Float? = null
    ) {
        tomSteadyProcessor?.setParameters(
            targetLevel = targetLevel,
            maxGain = maxGain,
            minGain = minGain,
            attackTime = attackTime,
            releaseTime = releaseTime
        )
        onDataChangeListener?.onTomSteadyChange()
    }

    fun isTomSteadyAvailable(): Boolean {
        return tomSteadyProcessor?.isAvailable() ?: false
    }

    fun getTomSteadyAGC(): TomSteadyAGC? {
        return tomSteadyProcessor?.getTomSteadyAGC()
    }

    /**
     * 检查TomSteady预分析是否完成
     */
    fun isTomSteadyPreAnalysisComplete(): Boolean {
        return tomSteadyProcessor?.isPreAnalysisComplete() ?: false
    }

    /**
     * 获取TomSteady参考电平
     */
    fun getTomSteadyReferenceLevel(): Float {
        return tomSteadyProcessor?.getReferenceLevel() ?: 0.0f
    }

    /**
     * 预分析TomSteady音频数据
     * @param buffer 音频数据缓冲区
     * @param size 数据大小
     * @return 是否完成预分析
     */
    fun preAnalyzeTomSteadyAudio(buffer: ShortArray, size: Int): Boolean {
        return tomSteadyProcessor?.preAnalyzeAudio(buffer, size) ?: false
    }

    /**
     * 设置TomSteady音乐时长（用于分段采样）
     * @param durationMs 音乐总时长（毫秒）
     */
    fun setTomSteadyDuration(durationMs: Long) {
        tomSteadyProcessor?.setDuration(durationMs)
    }

    /**
     * 从文件路径预分析TomSteady音频（后台线程调用）
     * @param filePath 音频文件路径
     * @param durationMs 音乐总时长（毫秒）
     * @return 是否成功完成预分析
     */
    fun preAnalyzeTomSteadyFromFile(filePath: String, durationMs: Long): Boolean {
        return tomSteadyProcessor?.preAnalyzeFromFile(filePath, durationMs) ?: false
    }

    // 模拟音频处理方法，实际项目中需要根据具体实现调整
    fun processAudio(buffer: ShortArray, size: Int): ShortArray {
        return tomSteadyProcessor?.processAudio(buffer, size) ?: buffer
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
        fun onAgcChange() {}
        fun onTomSteadyChange() {}
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
        automaticGainControl?.release()
        tomSteadyProcessor?.release()
    }

    fun stop() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        }
        stopTime = 0
        mediaPlayer.reset()
        abandonAudioFocus()
        playlist.clear()
        tomSteadyProcessor?.reset()
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

        // 如果启用了TomSteady，先进行预分析
        if (tomSteadyEnabled && tomSteadyProcessor?.isPreAnalysisComplete() != true) {
            val currentMedia = playlist.getCurrent()
            if (currentMedia != null) {
                val filePath = mediaAdapter.getFilePath(currentMedia)
                val duration = getDuration()
                if (filePath != null && duration > 0) {
                    // 在后台线程进行预分析
                    Thread {
                        val success = preAnalyzeTomSteadyFromFile(filePath, duration.toLong())
                        if (success) {
                            // 预分析完成，开始播放
                            mediaPlayer.start()
                        }
                    }.start()
                    // 先准备播放器
                    if (playlist.play(mediaPlayer)) {
                        mediaPlayer.prepareAsync()
                    }
                    return
                }
            }
        }

        if (playlist.play(mediaPlayer)) {
            mediaPlayer.prepareAsync()
        } else {
            // 加载失败时，尝试播放下一首，而不是清空播放列表
            val nextMedia = playlist.next()
            if (nextMedia != null) {
                startMediaPlayer()
            } else {
                stop()
            }
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
                tomSteadyProcessor?.reset()
                onPlayStateChange()
            } else {
                // 开始新的播放，启用初始音量控制阶段
                isInitialVolumePhase = true
                initialVolumeCounter = 0
                startMediaPlayer()
            }
        }
    }

    fun pause() {
        if (playState == PlayState.STATE_PLAYING) {
            abandonAudioFocus()
            playState = PlayState.STATE_PAUSE
            mediaPlayer.pause()
            tomSteadyProcessor?.reset()
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
        // 只有在TomSteady启用时才设置较低的初始音量
        if (tomSteadyEnabled) {
            mediaPlayer.setVolume(0.5f, 0.5f)
        } else {
            // 否则设置正常音量
            mediaPlayer.setVolume(1.0f, 1.0f)
        }
        
        mediaPlayer.start()
        playState = PlayState.STATE_PLAYING
        requestAudioFocus()
        tomSteadyProcessor?.reset()
        
        // 启动音量逐渐增加的定时器（仅在TomSteady启用时）
        if (isInitialVolumePhase && tomSteadyEnabled) {
            val volumeTimer = Timer()
            volumeTimer.schedule(object : TimerTask() {
                override fun run() {
                    if (isInitialVolumePhase && tomSteadyEnabled) {
                        initialVolumeCounter++
                        val currentVolume = 0.5f + (initialVolumeCounter * initialVolumeIncrement * 0.5f)
                        if (initialVolumeCounter < INITIAL_VOLUME_SECONDS) {
                            mediaPlayer.setVolume(currentVolume, currentVolume)
                        } else {
                            mediaPlayer.setVolume(1.0f, 1.0f)
                            isInitialVolumePhase = false
                            volumeTimer.cancel()
                        }
                    } else {
                        volumeTimer.cancel()
                    }
                }
            }, 0, 1000) // 每秒执行一次
        }
        
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
        fun getFilePath(e: E): String? = null
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
        
        try {
            if (AutomaticGainControl.isAvailable()) {
                this.automaticGainControl = AutomaticGainControl.create(mediaPlayer.audioSessionId).apply {
                    enabled = agcEnabled
                }
            }
        } catch (e: Exception) {
            // AGC初始化失败，跳过AGC设置
            this.automaticGainControl = null
        }
    }

}

