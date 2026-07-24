@file:androidx.annotation.OptIn(
    markerClass = [androidx.media3.common.util.UnstableApi::class]
)

package com.example.podclassic.media

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.podclassic.storage.SPManager
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.FutureTask
import kotlin.math.max
import kotlin.math.min

/**
 * App-facing player facade backed by Media3.
 *
 * Its public API intentionally matches the old facade so the service and UI do not need to know
 * which playback engine is used.  Unlike android.media.MediaPlayer, Media3 lets the decoded PCM
 * pass through [PlaybackDspAudioProcessor] before it reaches AudioTrack.
 */
class MediaPlayer<E>(context: Context, private val mediaAdapter: MediaAdapter<E>) {
    private val playlist = Playlist(mediaAdapter)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dsp = PlaybackDspAudioProcessor()
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private val renderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParameters: Boolean
        ): AudioSink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(dsp))
            .setEnableFloatOutput(true)
            .build()
    }

    private val player = ExoPlayer.Builder(context, renderersFactory)
        .setAudioAttributes(audioAttributes, true)
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply { updateAudioOffloadPreference(this, dsp.hasActiveEffects()) }

    private var playState = PlayState.STATE_STOP
    private var released = false

    val isPlaying: Boolean get() = onPlayerThread { player.isPlaying }
    val isPrepared: Boolean get() = onPlayerThread {
        player.playbackState == Player.STATE_READY || playState == PlayState.STATE_PAUSE
    }

    var enableAudioFocus: Boolean = true
        set(value) {
            field = value
            onPlayerThread { player.setAudioAttributes(audioAttributes, value) }
            onDataChangeListener?.onAudioManagerChange()
        }

    var equalizerId: Int = 0
        set(value) {
            field = value.coerceIn(0, EqualizerPreset.values().lastIndex)
            dsp.equalizer.parameters = dsp.equalizer.parameters.copy(
                preset = EqualizerPreset.values()[field]
            )
            onDataChangeListener?.onEqualizerChange()
        }

    var equalizerEnabled: Boolean
        get() = dsp.equalizer.parameters.enabled
        set(value) { dsp.equalizer.parameters = dsp.equalizer.parameters.copy(enabled = value) }

    fun setEqualizerStrength(strength: Float) {
        dsp.equalizer.parameters = dsp.equalizer.parameters.copy(strength = strength.coerceIn(0.25f, 1f))
    }

    var tomSteadyEnabled: Boolean
        get() = dsp.nightVolume.parameters.enabled
        set(value) {
            dsp.nightVolume.parameters = dsp.nightVolume.parameters.copy(enabled = value)
            onDataChangeListener?.onTomSteadyChange()
        }

    fun setTomSteadyParameters(
        targetLevel: Float? = null,
        maxGain: Float? = null,
        minGain: Float? = null,
        attackTime: Float? = null,
        releaseTime: Float? = null
    ) {
        val current = dsp.nightVolume.parameters
        dsp.nightVolume.parameters = current.copy(
            targetLevel = targetLevel?.coerceIn(0.08f, 0.20f) ?: current.targetLevel,
            maxGainDb = maxGain?.coerceIn(0f, 9f) ?: current.maxGainDb,
            attackMs = attackTime?.coerceIn(10f, 500f) ?: current.attackMs,
            releaseMs = releaseTime?.coerceIn(100f, 3_000f) ?: current.releaseMs
        )
        onDataChangeListener?.onTomSteadyChange()
    }

    fun setVolumeNormalization(enabled: Boolean, targetDb: Float, maxBoostDb: Float) {
        dsp.normalizer.parameters = VolumeNormalizerDsp.Parameters(
            enabled = enabled,
            targetDb = targetDb.coerceIn(-20f, -12f),
            maxBoostDb = maxBoostDb.coerceIn(0f, 9f)
        )
    }

    fun setClearBass(enabled: Boolean, level: Int) {
        dsp.clearBass.parameters = ClearBassDsp.Parameters(enabled, level.coerceIn(1, 5))
    }

    fun setCrossfeed(enabled: Boolean, level: CrossfeedLevel) {
        dsp.crossfeed.parameters = CrossfeedDsp.Parameters(enabled, level)
    }

    /**
     * Reconfigures the Media3 audio path after a batch of DSP setting changes.
     *
     * Track reselection preserves the current media item, position and play/pause state. With no
     * active DSP, offload is enabled where the device and media format support it; otherwise Media3
     * automatically falls back to the normal decoded path. Active DSP always disables offload so
     * decoded PCM continues through [PlaybackDspAudioProcessor].
     */
    fun refreshAudioPipeline() = onPlayerThread {
        if (released) return@onPlayerThread
        updateAudioOffloadPreference(player, dsp.hasActiveEffects())
    }

    var tubeAmpEnabled: Boolean
        get() = dsp.tube.parameters.enabled
        set(value) {
            dsp.tube.parameters = dsp.tube.parameters.copy(enabled = value)
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
        val current = dsp.tube.parameters
        dsp.tube.parameters = current.copy(
            drive = gain?.coerceIn(1f, 2.2f) ?: current.drive,
            saturation = saturation?.coerceIn(0f, 1f) ?: current.saturation,
            harmonics = harmonics?.coerceIn(0f, 1f) ?: current.harmonics,
            compressionRatio = ratio?.coerceIn(1f, 2f) ?: current.compressionRatio,
            attackMs = attack?.coerceIn(5f, 200f) ?: current.attackMs,
            releaseMs = release?.coerceIn(50f, 1_000f) ?: current.releaseMs,
            warmth = warmth?.coerceIn(0f, 1f) ?: current.warmth
        )
        warmth?.let { SPManager.setFloat(SPManager.SP_TUBE_AMP_WARMTH, it) }
        saturation?.let { SPManager.setFloat(SPManager.SP_TUBE_AMP_SATURATION, it) }
        harmonics?.let { SPManager.setFloat(SPManager.SP_TUBE_AMP_HARMONICS, it) }
        onDataChangeListener?.onTubeAmpChange()
    }

    fun applyTubeAmpPreset(preset: TubeAmpPreset) {
        dsp.tube.parameters = when (preset) {
            TubeAmpPreset.NONE -> TubeAmpDsp.Parameters(enabled = false)
            TubeAmpPreset.WARM -> TubeAmpDsp.Parameters(
                enabled = true, drive = 1.18f, saturation = 0.20f, harmonics = 0.10f,
                compressionRatio = 1.12f, attackMs = 35f, releaseMs = 260f, warmth = 0.25f
            )
            TubeAmpPreset.SMOOTH -> TubeAmpDsp.Parameters(
                enabled = true, drive = 1.30f, saturation = 0.40f, harmonics = 0.20f,
                compressionRatio = 1.22f, attackMs = 28f, releaseMs = 240f, warmth = 0.50f
            )
            TubeAmpPreset.VINTAGE -> TubeAmpDsp.Parameters(
                enabled = true, drive = 1.48f, saturation = 0.60f, harmonics = 0.30f,
                compressionRatio = 1.38f, attackMs = 22f, releaseMs = 300f, warmth = 0.75f
            )
            TubeAmpPreset.DYNAMIC -> TubeAmpDsp.Parameters(
                enabled = true, drive = 1.36f, saturation = 0.40f, harmonics = 0.20f,
                compressionRatio = 1.16f, attackMs = 12f, releaseMs = 150f, warmth = 0.50f
            )
        }
        SPManager.setInt(SPManager.SP_TUBE_AMP_PRESET, preset.ordinal)
        SPManager.setBoolean(SPManager.SP_TUBE_AMP_ENABLED, preset != TubeAmpPreset.NONE)
        SPManager.setFloat(SPManager.SP_TUBE_AMP_WARMTH, dsp.tube.parameters.warmth)
        SPManager.setFloat(SPManager.SP_TUBE_AMP_SATURATION, dsp.tube.parameters.saturation)
        SPManager.setFloat(SPManager.SP_TUBE_AMP_HARMONICS, dsp.tube.parameters.harmonics)
        onDataChangeListener?.onTubeAmpChange()
    }

    fun getPresetList(): Array<String?> = EqualizerPreset.values().map { it.title }.toTypedArray()

    interface OnDataChangeListener {
        fun onPlaylistChange() {}
        fun onPlayModeChange() {}
        fun onRepeatModeChange() {}
        fun onEqualizerChange() {}
        fun onTomSteadyChange() {}
        fun onTubeAmpChange() {}
        fun onAudioManagerChange() {}
        fun onStopTimeChange() {}
    }

    var onDataChangeListener: OnDataChangeListener? = null

    interface OnMediaChangeListener<E> {
        fun onMediaMetadataChange(mediaPlayer: MediaPlayer<E>) {}
        fun onPlaybackStateChange(mediaPlayer: MediaPlayer<E>) {}
    }

    private val onMediaChangeListeners = HashSet<OnMediaChangeListener<E>>()

    @Synchronized
    fun addOnMediaChangeListener(listener: OnMediaChangeListener<E>) {
        onMediaChangeListeners.add(listener)
    }

    @Synchronized
    fun removeOnMediaChangeListener(listener: OnMediaChangeListener<E>) {
        onMediaChangeListeners.remove(listener)
    }

    private fun onMediaMetadataChange() = onMediaChangeListeners.toList().forEach {
        it.onMediaMetadataChange(this)
    }

    private fun onPlayStateChange() = onMediaChangeListeners.toList().forEach {
        it.onPlaybackStateChange(this)
    }

    private var stopTimer: Timer? = null
    var stopTime = 0
        set(value) {
            if (value == 0) {
                stopTimer?.cancel()
                stopTimer = null
            } else if (value != field - 1) {
                stopTimer?.cancel()
                stopTimer = Timer("PodClassicStopTimer", true).apply {
                    schedule(object : TimerTask() {
                        override fun run() {
                            if (stopTime <= 1) {
                                stop()
                                stopTime = 0
                            } else {
                                stopTime--
                            }
                        }
                    }, 60_000L, 60_000L)
                }
            }
            field = value
            onDataChangeListener?.onStopTimeChange()
        }

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        playState = if (player.playWhenReady) PlayState.STATE_PLAYING else PlayState.STATE_PAUSE
                        onPlayStateChange()
                    }
                    Player.STATE_ENDED -> {
                        if (playlist.onCompletion() != null) startMediaPlayer() else stop()
                    }
                    else -> Unit
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val nextState = when {
                    isPlaying -> PlayState.STATE_PLAYING
                    player.playbackState == Player.STATE_READY -> PlayState.STATE_PAUSE
                    else -> playState
                }
                if (nextState != playState) {
                    playState = nextState
                    onPlayStateChange()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (playlist.next() != null) startMediaPlayer() else stop()
            }

        })
    }

    fun release() = onPlayerThread {
        if (released) return@onPlayerThread
        stopTimer?.cancel()
        player.release()
        onMediaChangeListeners.clear()
        released = true
    }

    fun stop() = onPlayerThread {
        player.stop()
        player.clearMediaItems()
        playlist.clear()
        stopTime = 0
        playState = PlayState.STATE_STOP
        dsp.tube.reset()
        onPlayStateChange()
        onMediaMetadataChange()
    }

    private fun startMediaPlayer() = onPlayerThread {
        var attempts = 0
        var current = playlist.getCurrent()
        while (current != null && attempts < playlist.size()) {
            val uri = mediaAdapter.getMediaUri(current)
            if (uri != null) {
                playState = PlayState.STATE_STOP
                onMediaMetadataChange()
                player.setMediaItem(MediaItem.fromUri(uri))
                player.prepare()
                player.playWhenReady = true
                return@onPlayerThread
            }
            current = playlist.next()
            attempts++
        }
        stop()
    }

    fun next() = onPlayerThread {
        if (playlist.next() != null) startMediaPlayer()
    }

    fun prev() = onPlayerThread {
        if (getProgress() > 3_000) seekTo(0) else if (playlist.prev() != null) startMediaPlayer()
    }

    fun playPause() = onPlayerThread { if (player.isPlaying) pause() else play() }

    fun play() = onPlayerThread {
        if (playlist.isEmpty()) return@onPlayerThread
        if (player.playbackState == Player.STATE_READY) {
            player.play()
            playState = PlayState.STATE_PLAYING
            onPlayStateChange()
        } else {
            startMediaPlayer()
        }
    }

    fun pause() = onPlayerThread {
        if (playState == PlayState.STATE_PLAYING || player.playWhenReady) {
            player.pause()
            playState = PlayState.STATE_PAUSE
            onPlayStateChange()
        }
    }

    fun seekTo(position: Int) = onPlayerThread { player.seekTo(position.coerceAtLeast(0).toLong()) }

    var defaultSeekDuration = 12_000
    fun forward(): Boolean = onPlayerThread {
        if (!isPrepared) return@onPlayerThread false
        val duration = getDuration()
        val progress = getProgress()
        if (duration <= 0 || progress >= duration) false
        else {
            seekTo(min(progress + defaultSeekDuration, duration))
            true
        }
    }

    fun backward(): Boolean = onPlayerThread {
        if (!isPrepared) return@onPlayerThread false
        val progress = getProgress()
        if (progress <= 0) false else {
            seekTo(max(0, progress - defaultSeekDuration))
            true
        }
    }

    fun getDuration(): Int = onPlayerThread {
        player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0
    }

    fun getProgress(): Int = onPlayerThread {
        player.currentPosition.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    }

    fun getPlaylist(): ArrayList<E> = onPlayerThread { ArrayList(playlist.getPlayList()) }
    fun getCurrent(): E? = onPlayerThread { playlist.getCurrent() }
    fun getIndex(): Int = onPlayerThread { playlist.index }
    fun getPlayState(): PlayState = onPlayerThread { playState }

    fun setPlayMode(playMode: PlayMode) = onPlayerThread {
        playlist.playMode = playMode
        onDataChangeListener?.onPlayModeChange()
    }
    fun getPlayMode(): PlayMode = onPlayerThread { playlist.playMode }

    fun setRepeatMode(repeatMode: RepeatMode) = onPlayerThread {
        playlist.repeatMode = repeatMode
        onDataChangeListener?.onRepeatModeChange()
    }
    fun getRepeatMode(): RepeatMode = onPlayerThread { playlist.repeatMode }

    fun add(e: E) = onPlayerThread {
        playlist.add(e)
        onDataChangeListener?.onPlaylistChange()
    }

    fun set(e: E) = onPlayerThread {
        playlist.add(e)
        playlist.setCurrent(e)
        startMediaPlayer()
    }

    fun remove(e: E) = onPlayerThread {
        playlist.remove(e)
        onDataChangeListener?.onPlaylistChange()
    }

    fun removeAt(index: Int) = onPlayerThread {
        playlist.removeAt(index)
        onDataChangeListener?.onPlaylistChange()
    }

    fun setIndex(index: Int) = onPlayerThread {
        playlist.index = index
        startMediaPlayer()
    }

    fun setPlaylist(list: ArrayList<E>) = onPlayerThread {
        playlist.setPlaylist(list)
        if (list.isNotEmpty()) playlist.index = 0
        startMediaPlayer()
        onDataChangeListener?.onPlaylistChange()
    }

    fun setPlaylist(list: ArrayList<E>, index: Int) = onPlayerThread {
        playlist.setPlaylist(list, index)
        startMediaPlayer()
        onDataChangeListener?.onPlaylistChange()
    }

    fun shufflePlay(list: ArrayList<E>?) = onPlayerThread {
        val source = list ?: playlist.getPlayList()
        playlist.shufflePlay(ArrayList(source))
        startMediaPlayer()
        onDataChangeListener?.onPlaylistChange()
    }

    fun shufflePlay(list: ArrayList<E>, index: Int) = onPlayerThread {
        playlist.shufflePlay(list, index)
        startMediaPlayer()
        onDataChangeListener?.onPlaylistChange()
    }

    private fun <T> onPlayerThread(block: () -> T): T {
        if (Looper.myLooper() == player.applicationLooper) return block()
        val task = FutureTask(block)
        mainHandler.post(task)
        return task.get()
    }

    private fun updateAudioOffloadPreference(player: ExoPlayer, effectsEnabled: Boolean) {
        val offloadMode = if (effectsEnabled) {
            AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
        } else {
            AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
        }
        if (player.trackSelectionParameters.audioOffloadPreferences.audioOffloadMode == offloadMode) {
            return
        }
        val preferences = AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(offloadMode)
            .build()
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(preferences)
            .build()
    }

    interface MediaAdapter<E> {
        fun getMediaUri(e: E): Uri?
    }
}
