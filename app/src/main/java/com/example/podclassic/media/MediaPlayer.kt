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
import android.os.Handler
import android.os.Looper
import com.example.podclassic.storage.SPManager
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
    
    // 用于区分用户手动暂停和音频焦点丢失导致的暂停
    private var pausedByUser: Boolean = false

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

    // 胆机音效相关
    var tubeAmpEnabled: Boolean
        get() = tomSteadyProcessor?.tubeAmpEnabled ?: false
        set(value) {
            // 确保TomSteadyProcessor已初始化
            if (tomSteadyProcessor == null) {
                initTomSteadyProcessor()
            }
            tomSteadyProcessor?.tubeAmpEnabled = value
            SPManager.setBoolean(SPManager.SP_TUBE_AMP_ENABLED, value)
            onDataChangeListener?.onTubeAmpChange()
        }

    fun setTubeAmpParameters(
        gain: Float? = null,
        saturation: Float? = null,
        harmonics: Float? = null,
        ratio: Float? = null,
        attack: Float? = null,
        release: Float? = null,
        warmth: Float? = null
    ) {
        tomSteadyProcessor?.setTubeAmpParameters(
            gain = gain,
            saturation = saturation,
            harmonics = harmonics,
            ratio = ratio,
            attack = attack,
            release = release,
            warmth = warmth
        )

        // 保存参数到SharedPreferences
        warmth?.let { SPManager.setFloat(SPManager.SP_TUBE_AMP_WARMTH, it) }
        saturation?.let { SPManager.setFloat(SPManager.SP_TUBE_AMP_SATURATION, it) }
        harmonics?.let { SPManager.setFloat(SPManager.SP_TUBE_AMP_HARMONICS, it) }
        onDataChangeListener?.onTubeAmpChange()
    }

    fun applyTubeAmpPreset(preset: TubeAmpPreset) {
        tomSteadyProcessor?.applyTubeAmpPreset(preset)
        SPManager.setInt(SPManager.SP_TUBE_AMP_PRESET, preset.ordinal)
        onDataChangeListener?.onTubeAmpChange()
    }

    fun getTubeAmpProcessor(): TubeAmpProcessor? {
        return tomSteadyProcessor?.getTubeAmpProcessor()
    }

    // DC Phase Linearizer 相关
    var dcPhaseEnabled: Boolean
        get() = tomSteadyProcessor?.dcPhaseEnabled ?: false
        set(value) {
            // 确保TomSteadyProcessor已初始化
            if (tomSteadyProcessor == null) {
                initTomSteadyProcessor()
            }
            tomSteadyProcessor?.dcPhaseEnabled = value
            SPManager.setBoolean(SPManager.SP_DC_PHASE_ENABLED, value)
            onDataChangeListener?.onDCPhaseChange()
        }

    fun setDCPhaseParameters(
        strength: Float? = null,
        lowDelay: Float? = null,
        midDelay: Float? = null,
        highDelay: Float? = null,
        crossover: Float? = null,
        highCrossover: Float? = null
    ) {
        tomSteadyProcessor?.setDCPhaseParameters(
            strength = strength,
            lowDelay = lowDelay,
            midDelay = midDelay,
            highDelay = highDelay,
            crossover = crossover,
            highCrossover = highCrossover
        )

        // 保存参数到SharedPreferences
        strength?.let { SPManager.setFloat(SPManager.SP_DC_PHASE_STRENGTH, it) }
        lowDelay?.let { SPManager.setFloat(SPManager.SP_DC_PHASE_LOW_DELAY, it) }
        midDelay?.let { SPManager.setFloat(SPManager.SP_DC_PHASE_MID_DELAY, it) }
        highDelay?.let { SPManager.setFloat(SPManager.SP_DC_PHASE_HIGH_DELAY, it) }
        crossover?.let { SPManager.setFloat(SPManager.SP_DC_PHASE_CROSSOVER, it) }
        onDataChangeListener?.onDCPhaseChange()
    }

    fun applyDCPhasePreset(preset: DCPhasePreset) {
        tomSteadyProcessor?.applyDCPhasePreset(preset)
        SPManager.setInt(SPManager.SP_DC_PHASE_PRESET, preset.ordinal)
        onDataChangeListener?.onDCPhaseChange()
    }

    fun getDCPhaseProcessor(): DCPhaseLinearizerProcessor? {
        return tomSteadyProcessor?.getDCPhaseProcessor()
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
        fun onTubeAmpChange() {}
        fun onDCPhaseChange() {}
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
                if (stopTime > 0) {
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
        android.util.Log.d("MediaPlayer", "stop() called")
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
            // 重置用户暂停标志
            pausedByUser = false
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
            android.util.Log.d("MediaPlayer", "pause() called by user")
            pausedByUser = true
            abandonAudioFocus()
            playState = PlayState.STATE_PAUSE
            mediaPlayer.pause()
            tomSteadyProcessor?.reset()
            onPlayStateChange()
        }
    }
    
    // 由音频焦点管理器调用，不设置 pausedByUser 标志
    private fun pauseByAudioFocus() {
        if (playState == PlayState.STATE_PLAYING) {
            android.util.Log.d("MediaPlayer", "pauseByAudioFocus() called")
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
                private var isTemporaryLoss = false
                private var lossTime: Long = 0
                private var isDucked: Boolean = false
                private var resumeAttempts: Int = 0
                private val maxResumeAttempts = 5
                private val resumeInterval = 2000L // 2秒

                private val resumeRunnable = Runnable {
                    attemptResume()
                }

                private fun attemptResume() {
                    if (!enableAudioFocus || pausedByUser) {
                        android.util.Log.d("MediaPlayer", "Resume aborted: enableAudioFocus=$enableAudioFocus, pausedByUser=$pausedByUser")
                        return
                    }
                    
                    val currentTime = System.currentTimeMillis()
                    val lossDuration = currentTime - lossTime
                    
                    android.util.Log.d("MediaPlayer", "Attempting resume, wasPlaying=$wasPlaying, isTemporaryLoss=$isTemporaryLoss, lossDuration=$lossDuration, attempts=$resumeAttempts")
                    
                    // 如果丢失时间超过30秒，放弃恢复
                    if (lossDuration > 30000) {
                        android.util.Log.d("MediaPlayer", "Giving up resume after 30 seconds")
                        resetState()
                        return
                    }
                    
                    // 尝试重新请求音频焦点
                    if (wasPlaying && playState != PlayState.STATE_PLAYING) {
                        resumeAttempts++
                        android.util.Log.d("MediaPlayer", "Requesting audio focus, attempt $resumeAttempts")
                        requestAudioFocus()
                        
                        // 如果还没有收到焦点恢复，继续尝试
                        if (resumeAttempts < maxResumeAttempts && isTemporaryLoss) {
                            Handler(Looper.getMainLooper()).postDelayed(resumeRunnable, resumeInterval)
                        }
                    }
                }

                private fun resetState() {
                    isTemporaryLoss = false
                    wasPlaying = false
                    pausedByUser = false
                    lossTime = 0
                    resumeAttempts = 0
                }

                override fun onAudioFocusGain() {
                    if (!enableAudioFocus) {
                        return
                    }
                    val currentTime = System.currentTimeMillis()
                    val lossDuration = currentTime - lossTime
                    android.util.Log.d("MediaPlayer", "Audio focus gained, wasPlaying=$wasPlaying, isTemporaryLoss=$isTemporaryLoss, pausedByUser=$pausedByUser, lossDuration=$lossDuration")
                    
                    // 取消任何待定的恢复尝试
                    Handler(Looper.getMainLooper()).removeCallbacks(resumeRunnable)
                    
                    // 恢复音量（如果是被duck的情况）
                    if (isDucked) {
                        mediaPlayer.setVolume(1.0f, 1.0f)
                        isDucked = false
                    }
                    
                    // 只有不是用户手动暂停的情况下才自动恢复
                    // 临时丢失后恢复，或者丢失时间较短（小于30秒），自动继续播放
                    val shouldResume = wasPlaying && !pausedByUser && playState != PlayState.STATE_PLAYING && 
                        (isTemporaryLoss || lossDuration < 30000)
                    
                    if (shouldResume) {
                        android.util.Log.d("MediaPlayer", "Resuming playback after audio focus gain")
                        play()
                    }
                    // 重置标志
                    resetState()
                }
                
                override fun onAudioFocusLoss(permanent: Boolean, pausedByDuck: Boolean) {
                    if (!enableAudioFocus) {
                        return
                    }
                    lossTime = System.currentTimeMillis()
                    // 只要不是永久丢失，都认为是临时丢失
                    isTemporaryLoss = !permanent
                    resumeAttempts = 0
                    android.util.Log.d("MediaPlayer", "Audio focus lost, permanent=$permanent, pausedByDuck=$pausedByDuck, isTemporaryLoss=$isTemporaryLoss")
                    if (playState == PlayState.STATE_PLAYING) {
                        wasPlaying = true
                        if (pausedByDuck) {
                            // 降低音量继续播放
                            isDucked = true
                            mediaPlayer.setVolume(0.1f, 0.1f)
                        } else {
                            pauseByAudioFocus()
                        }
                        
                        // 启动恢复尝试定时器（针对高德地图等不释放焦点的应用）
                        if (!permanent) {
                            Handler(Looper.getMainLooper()).postDelayed(resumeRunnable, resumeInterval)
                        }
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

