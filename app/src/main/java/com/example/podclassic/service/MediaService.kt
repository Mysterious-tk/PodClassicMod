package com.example.podclassic.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.*
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.example.podclassic.bean.Music
import com.example.podclassic.media.MediaPlayer
import com.example.podclassic.media.PlayMode
import com.example.podclassic.media.RepeatMode
import com.example.podclassic.storage.MusicTable
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.MediaStoreUtil
import com.example.podclassic.util.VolumeUtil


/**
 * 媒体播放服务
 * 
 * 提供完整的后台媒体播放功能，包括：
 * 1. 前台服务保证后台播放稳定性
 * 2. MediaSession 管理媒体会话状态
 * 3. 通知栏媒体控制界面
 * 4. 音频焦点管理
 * 5. 通过 MediaSession 向系统提供播放进度
 */
class MediaService : Service() {
    companion object {
        const val ACTION_SET_EQUALIZER = "action_set_equalizer"
        const val ACTION_RELOAD_AUDIO_EFFECTS = "action_reload_audio_effects"
        const val ACTION_PLAY_PAUSE = "action_play_pause"
        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"

        const val ACTION_NEXT = "action_next"
        const val ACTION_PREV = "action_prev"
        const val ACTION_STOP = "action_stop"

        const val ACTION_SEEK = "action_seek"
        const val ACTION_FORWARD = "action_forward"
        const val ACTION_BACKWARD = "action_backward"

        const val ACTION_ADD_MUSIC = "action_add_music"
        const val ACTION_REMOVE_MUSIC = "action_remove_music"
        const val ACTION_REMOVE_MUSIC_AT = "action_remove_music_at"
        const val ACTION_SET_INDEX = "action_set_index"

        const val ACTION_SET_MUSIC = "action_set_music"

        const val ACTION_NEXT_PLAY_MODE = "action_next_play_mode"
        const val ACTION_SET_PLAY_MODE = "action_set_play_mode"

        const val ACTION_NEXT_REPEAT_MODE = "action_next_repeat_mode"
        const val ACTION_SET_REPEAT_MODE = "action_set_repeat_mode"

        const val ACTION_SET_PLAY_LIST = "action_set_playlist"

        const val ACTION_SHUFFLE = "action_shuffle"

        const val ACTION_SET_STOP_TIME = "action_set_stop_time"

        const val ACTION_SET_FAVORITE = "action_set_favorite"
        const val ACTION_CANCEL_FAVORITE = "action_cancel_favorite"
        const val ACTION_FAVORITE_CHANGE = "action_favorite_change"

        const val ACTION_VOLUME_UP = "action_volume_up"
        const val ACTION_VOLUME_DOWN = "action_volume_down"

        const val ACTION_MAIN = "action_main"

        const val ACTION_UPDATE_AUDIO_FOCUS = "action_set_audio_focus"
        const val ACTION_SET_TOM_STEADY_ENABLED = "action_set_tom_steady_enabled"
        const val ACTION_SET_TOM_STEADY_PARAMETERS = "action_set_tom_steady_parameters"

        // 胆机音效相关
        const val ACTION_SET_TUBE_AMP_ENABLED = "action_set_tube_amp_enabled"
        const val ACTION_SET_TUBE_AMP_PRESET = "action_set_tube_amp_preset"
        const val ACTION_SET_TUBE_AMP_PARAMETERS = "action_set_tube_amp_parameters"

    }

    private lateinit var mediaSessionCompat: MediaSessionCompat

    /**
     * PlaybackState 构建器
     * 配置所有支持的媒体控制操作
     */
    private val playbackStateCompatBuilder = PlaybackStateCompat.Builder()
        .setActions(
            PlaybackStateCompat.ACTION_PLAY
                    or PlaybackStateCompat.ACTION_PAUSE
                    or PlaybackStateCompat.ACTION_PLAY_PAUSE
                    or PlaybackStateCompat.ACTION_STOP
                    or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    or PlaybackStateCompat.ACTION_SEEK_TO
                    or PlaybackStateCompat.ACTION_FAST_FORWARD
                    or PlaybackStateCompat.ACTION_REWIND
        )
        .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)

    private lateinit var notificationManager: NotificationManager

    /**
     * MediaSession 回调处理
     * 处理来自系统和其他应用的媒体控制请求
     */
    private val mediaSessionCallBack = object : MediaSessionCompat.Callback() {
        override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
            mediaBroadcastReceiver.onReceive(this@MediaService, mediaButtonIntent)
            return true
        }

        override fun onRewind() {
            android.util.Log.d("MediaService", "MediaSession onRewind() called")
            mediaPlayer.backward()
        }

        override fun onFastForward() {
            android.util.Log.d("MediaService", "MediaSession onFastForward() called")
            mediaPlayer.forward()
        }

        override fun onSkipToPrevious() {
            android.util.Log.d("MediaService", "MediaSession onSkipToPrevious() called")
            mediaPlayer.prev()
        }

        override fun onSkipToNext() {
            android.util.Log.d("MediaService", "MediaSession onSkipToNext() called")
            mediaPlayer.next()
        }

        override fun onPause() {
            android.util.Log.d("MediaService", "MediaSession onPause() called, isPlaying=${mediaPlayer.isPlaying}")
            if (mediaPlayer.isPlaying) {
                android.util.Log.d("MediaService", "Calling mediaPlayer.pause()")
                mediaPlayer.pause()
            }
        }

        override fun onPlay() {
            android.util.Log.d("MediaService", "MediaSession onPlay() called")
            if (mediaPlayer.getPlaylist().isEmpty()) {
                shufflePlay()
            }
            if (!mediaPlayer.isPlaying) mediaPlayer.play()
        }

        override fun onSeekTo(pos: Long) {
            android.util.Log.d("MediaService", "MediaSession onSeekTo() called, pos=$pos")
            mediaPlayer.seekTo(pos.toInt())
            // 更新 MediaSession 的播放状态以反映新的位置
            updatePlaybackState()
        }

        override fun onStop() {
            android.util.Log.d("MediaService", "MediaSession onStop() called")
            stop()
        }
    }
    
    /**
     * 更新 MediaSession 的播放状态
     */
    private fun updatePlaybackState() {
        val state = when {
            mediaPlayer.isPlaying -> PlaybackStateCompat.STATE_PLAYING
            mediaPlayer.isPrepared -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_STOPPED
        }

        val position = mediaPlayer.getProgress().toLong()
        val playbackSpeed = if (mediaPlayer.isPlaying) 1.0f else 0.0f
        val duration = mediaPlayer.getDuration().toLong()

        mediaSessionCompat.setPlaybackState(
            playbackStateCompatBuilder
                .setState(state, position, playbackSpeed, SystemClock.elapsedRealtime())
                .setBufferedPosition(duration)
                .build()
        )

        android.util.Log.d("MediaService", "PlaybackState updated: state=$state, position=$position, duration=$duration, isActive=${mediaSessionCompat.isActive}")
    }

    /**
     * 媒体变更监听器
     * 处理歌曲切换和播放状态变化
     */
    private val onMediaChangeListener = object : MediaPlayer.OnMediaChangeListener<Music> {
        override fun onMediaMetadataChange(mediaPlayer: MediaPlayer<Music>) {
            val current = mediaPlayer.getCurrent()

            MediaPresenter.music.set(current)
            if (current == null) {
                sendNotification()
                return
            }

            // 构建完整的媒体元数据
            val metadataBuilder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, current.id?.toString() ?: "")
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, current.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, current.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, current.album)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, current.artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, current.duration)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, current.albumId?.toString() ?: "")
                .putString(MediaMetadataCompat.METADATA_KEY_AUTHOR, current.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_WRITER, current.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_COMPOSER, current.artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, mediaPlayer.getPlaylist().size.toLong())
                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, (mediaPlayer.getIndex() + 1).toLong())
                // 添加应用包名，帮助系统识别
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, current.title)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, current.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, current.album)

            // 添加专辑封面（如果有）
            current.image?.let {
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, it)
            }

            mediaSessionCompat.setMetadata(metadataBuilder.build())
            updatePlaybackState()
            sendNotification()

            android.util.Log.d("MediaService", "Metadata set - Title: ${current.title}, Artist: ${current.artist}, Album: ${current.album}, Duration: ${current.duration}")
            android.util.Log.d("MediaService", "MediaSession isActive: ${mediaSessionCompat.isActive}")
        }

        override fun onPlaybackStateChange(mediaPlayer: MediaPlayer<Music>) {
            updatePlaybackState()
            sendNotification()
            MediaPresenter.playState.set(mediaPlayer.getPlayState())
            
            android.util.Log.d("MediaService", "Playback state changed: isPlaying=${mediaPlayer.isPlaying}")
        }
    }

    private val onDataChangeListener = object : MediaPlayer.OnDataChangeListener {
        override fun onEqualizerChange() {
            SPManager.setInt(SPManager.SP_EQUALIZER, mediaPlayer.equalizerId)
        }

        override fun onPlayModeChange() {
            MediaPresenter.playMode.set(mediaPlayer.getPlayMode())
            SPManager.setInt(SPManager.SP_PLAY_MODE, mediaPlayer.getPlayMode().id)
        }

        override fun onPlaylistChange() {
            MediaPresenter.playList.set(mediaPlayer.getPlaylist())
        }

        override fun onRepeatModeChange() {
            MediaPresenter.repeatMode.set(mediaPlayer.getRepeatMode())
            SPManager.setInt(SPManager.SP_REPEAT_MODE, mediaPlayer.getRepeatMode().id)
        }

        override fun onStopTimeChange() {
            MediaPresenter.stopTime.set(mediaPlayer.stopTime)
        }

        override fun onTubeAmpChange() {
            // 胆机音效变化时的回调（可扩展）
        }

    }

    private val mediaAdapter = object : MediaPlayer.MediaAdapter<Music> {
        override fun getMediaUri(e: Music): android.net.Uri? = when {
            !e.data.isNullOrBlank() -> android.net.Uri.parse(e.data)
            e.uri != null -> e.uri
            else -> null
        }
    }

    private lateinit var mediaPlayer: MediaPlayer<Music>

    private var broadcastReceiverRegistered = false
    private val mediaBroadcastReceiver = MediaBroadcastReceiver()

    private fun registerBroadcastReceiver() {
        if (broadcastReceiverRegistered) {
            return
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        intentFilter.priority = 1000
        registerReceiver(mediaBroadcastReceiver, intentFilter)
        broadcastReceiverRegistered = true
    }

    private fun unregisterBroadcastReceiver() {
        if (broadcastReceiverRegistered) {
            unregisterReceiver(mediaBroadcastReceiver)
            broadcastReceiverRegistered = false
        }
    }

    private data class NotificationState(
        val musicId: Long?,
        val title: String?,
        val artist: String?,
        val album: String?,
        val duration: Int,
        val artworkIdentity: Int,
        val isPlaying: Boolean,
        val isFavorite: Boolean
    )

    private var lastNotificationState: NotificationState? = null
    @Volatile
    private var serviceStopping = false

    /**
     * 发送/更新通知
     * 只在歌曲、播放状态、封面或收藏状态真正变化时重建。
     * 播放位置由 MediaSession 的 position/speed/updateTime 交给系统推算。
     */
    @SuppressLint("MissingPermission")
    @Synchronized
    private fun sendNotification() {
        if (serviceStopping) return

        val music = mediaPlayer.getCurrent()
        android.util.Log.d("MediaService", "sendNotification: music=$music, isForeground=$isForeground")
        if (music == null) {
            android.util.Log.w("MediaService", "No music to show, stopping foreground")
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
            lastNotificationState = null
            return
        }

        val duration = mediaPlayer.getDuration()
        val state = NotificationState(
            musicId = music.id,
            title = music.title,
            artist = music.artist,
            album = music.album,
            duration = duration,
            artworkIdentity = System.identityHashCode(music.image),
            isPlaying = mediaPlayer.isPlaying,
            isFavorite = MusicTable.favourite.contains(music)
        )
        if (isForeground && state == lastNotificationState) {
            return
        }

        android.util.Log.d("MediaService", "Creating notification for: ${music.title}, isPlaying=${state.isPlaying}")
        val notification = notificationManager.buildNotification(
            music,
            state.isPlaying,
            duration
        )
        if (isForeground) {
            if (canPostNotifications()) {
                androidx.core.app.NotificationManagerCompat.from(this).notify(
                    NotificationManager.NOTIFICATION_ID,
                    notification
                )
            }
        } else {
            startForeground(NotificationManager.NOTIFICATION_ID, notification)
            isForeground = true
        }
        lastNotificationState = state
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    // 标记服务是否在前台运行
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("MediaService", "onCreate() called")

        // 初始化 MediaSession
        initMediaSession()

        // 初始化通知管理器
        notificationManager = NotificationManager(this, mediaSessionCompat.sessionToken)

        // 初始化媒体播放器
        initMediaPlayer()

        // 注册广播接收器
        registerBroadcastReceiver()

        // 初始化后台处理线程
        initHandlerThread()

        // 确保MediaSession初始状态正确
        updatePlaybackState()

        android.util.Log.d("MediaService", "Service initialized successfully")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("MediaService", "onStartCommand() called with action: ${intent?.action}, isForeground=$isForeground")

        val action = intent?.action
        if (action == ACTION_STOP) {
            stop()
            return START_NOT_STICKY
        }

        serviceStopping = false

        // 确保MediaSession始终处于激活状态
        if (!mediaSessionCompat.isActive) {
            mediaSessionCompat.isActive = true
            android.util.Log.d("MediaService", "Reactivated MediaSession")
        }

        if (action != null) {
            android.util.Log.d("MediaService", "Handling action: $action")
            handleAction(action, null, null)
        } else {
            android.util.Log.d("MediaService", "No action, just updating notification")
        }

        // Most playback actions synchronously publish a real media event, which
        // already starts/updates the foreground notification. This fallback
        // covers commands without a media callback and avoids a pre-action rebuild.
        if (!isForeground && mediaPlayer.getCurrent() != null) {
            sendNotification()
        }

        // 前台媒体服务在正常播放期间仍由系统保留；被系统回收后不应以
        // 空 Intent 重建一个没有播放内容的常驻服务。
        return START_NOT_STICKY
    }
    
    /**
     * 初始化 MediaSession
     */
    private fun initMediaSession() {
        val receiverComponent = ComponentName(packageName, "com.example.podclassic.service.MediaBroadcastReceiver")

        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            component = receiverComponent
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            this, 
            0, 
            mediaButtonIntent, 
            PendingIntent.FLAG_IMMUTABLE
        )
        
        mediaSessionCompat = MediaSessionCompat(this, "PodClassicMediaService", receiverComponent, pendingIntent)
        
        // 设置回调
        mediaSessionCompat.setCallback(mediaSessionCallBack)
        
        // 设置会话活动（点击通知时打开应用）
        val sessionIntent = packageManager?.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionPendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            sessionIntent, 
            PendingIntent.FLAG_IMMUTABLE
        )
        mediaSessionCompat.setSessionActivity(sessionPendingIntent)

        // 激活 MediaSession
        mediaSessionCompat.isActive = true
        
        // 设置标志：处理媒体按钮和传输控制
        mediaSessionCompat.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or 
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        
        // 设置初始播放状态
        mediaSessionCompat.setPlaybackState(playbackStateCompatBuilder.build())

        android.util.Log.d("MediaService", "MediaSession token: ${mediaSessionCompat.sessionToken}")
        android.util.Log.d("MediaService", "MediaSession isActive: ${mediaSessionCompat.isActive}")
        android.util.Log.d("MediaService", "MediaSession initialized")
    }
    
    /**
     * 初始化媒体播放器
     */
    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer(this, mediaAdapter)
        mediaPlayer.addOnMediaChangeListener(onMediaChangeListener)
        mediaPlayer.onDataChangeListener = onDataChangeListener

        // 恢复保存的播放设置
        mediaPlayer.setPlayMode(
            PlayMode.getPlayMode(SPManager.getInt(SPManager.SP_PLAY_MODE))
        )
        mediaPlayer.setRepeatMode(
            RepeatMode.getRepeatMode(SPManager.getInt(SPManager.SP_REPEAT_MODE))
        )

        updateAudioFocus()
        updateTomSteadyEnabled()
        updateTubeAmpEnabled()
        updateAudioEffects()
        mediaPlayer.refreshAudioPipeline()

        android.util.Log.d("MediaService", "MediaPlayer initialized")
    }
    
    /**
     * 初始化后台处理线程
     */
    private fun initHandlerThread() {
        if (!this::handlerThread.isInitialized) {
            handlerThread = HandlerThread("MediaActionMQ").apply { start() }
            handler = object : Handler(handlerThread.looper) {
                override fun handleMessage(msg: Message) {
                    handleAction(msg)
                }
            }
        }
    }

    override fun onDestroy() {
        serviceStopping = true
        isForeground = false
        lastNotificationState = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (this::handler.isInitialized) {
            handler.removeCallbacksAndMessages(null)
        }
        mediaSessionCompat.setCallback(null)
        mediaSessionCompat.isActive = false
        mediaSessionCompat.release()
        unregisterBroadcastReceiver()
        if (this::handlerThread.isInitialized) {
            handlerThread.quitSafely()
        }
        mediaPlayer.removeOnMediaChangeListener(onMediaChangeListener)
        mediaPlayer.release()
        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder {
        return ServiceBinder()
    }

    private fun handleAction(action: String, arg1: Any?, arg2: Any?) {
        try {
            when (action) {
                ACTION_PLAY_PAUSE -> {
                    if (mediaPlayer.getPlaylist().isEmpty()) {
                        shufflePlay()
                    }
                    mediaPlayer.playPause()
                }
                ACTION_PLAY -> {
                    mediaPlayer.play()
                }
                ACTION_PAUSE -> {
                    mediaPlayer.pause()
                }
                ACTION_NEXT -> {
                    if (mediaPlayer.getPlaylist().isEmpty()) {
                        shufflePlay()
                    }
                    mediaPlayer.next()
                }
                ACTION_PREV -> {
                    if (mediaPlayer.getPlaylist().isEmpty()) {
                        shufflePlay()
                    }
                    mediaPlayer.prev()
                }
                ACTION_STOP -> {
                    stop()
                }
                ACTION_NEXT_PLAY_MODE -> {
                    mediaPlayer.setPlayMode(PlayMode.getPlayMode((mediaPlayer.getPlayMode().id + 1) % PlayMode.count))
                }
                ACTION_SET_PLAY_MODE -> {
                    if (arg1 is Int) {
                        mediaPlayer.setPlayMode(PlayMode.getPlayMode(arg1))
                    } else if (arg1 is PlayMode) {
                        mediaPlayer.setPlayMode(arg1)
                    }
                }
                ACTION_SET_MUSIC -> {
                    mediaPlayer.set(arg1 as Music)
                }
                ACTION_NEXT_REPEAT_MODE -> {
                    mediaPlayer.setRepeatMode(RepeatMode.getRepeatMode((mediaPlayer.getRepeatMode().id + 1) % RepeatMode.count))
                }
                ACTION_SET_REPEAT_MODE -> {
                    mediaPlayer.setRepeatMode(RepeatMode.getRepeatMode(arg1 as Int))
                }
                ACTION_SHUFFLE -> {
                    if (arg1 == null) {
                        shufflePlay()
                        return
                    }
                    if (arg2 == null) {
                        mediaPlayer.shufflePlay(arg1 as ArrayList<Music>)
                    } else {
                        mediaPlayer.shufflePlay(arg1 as ArrayList<Music>, arg2 as Int)
                    }
                }
                ACTION_SET_STOP_TIME -> {
                    mediaPlayer.stopTime = arg1 as Int
                }
                ACTION_SET_INDEX -> {
                    mediaPlayer.setIndex(arg1 as Int)
                }
                ACTION_ADD_MUSIC -> {
                    mediaPlayer.add(arg1 as Music)
                }
                ACTION_REMOVE_MUSIC -> {
                    mediaPlayer.remove(arg1 as Music)
                }
                ACTION_SET_PLAY_LIST -> {
                    if (arg2 == null) {
                        mediaPlayer.setPlaylist(arg1 as ArrayList<Music>)
                    } else {
                        mediaPlayer.setPlaylist(arg1 as ArrayList<Music>, arg2 as Int)
                    }
                }
                ACTION_SET_FAVORITE -> {
                    MusicTable.favourite.add(arg1 as Music)
                    sendNotification()
                }
                ACTION_CANCEL_FAVORITE -> {
                    MusicTable.favourite.remove(arg1 as Music)
                    sendNotification()
                }
                ACTION_FAVORITE_CHANGE -> {
                    val music = if (arg1 == null) {
                        mediaPlayer.getCurrent()
                    } else {
                        arg1 as Music
                    }
                    music ?: return
                    if (MusicTable.favourite.contains(music)) {
                        MusicTable.favourite.remove(music)
                    } else {
                        MusicTable.favourite.add(music)
                    }
                    sendNotification()
                }
                ACTION_VOLUME_UP -> {
                    VolumeUtil.volumeUp()
                }
                ACTION_VOLUME_DOWN -> {
                    VolumeUtil.volumeDown()
                }
                ACTION_FORWARD -> {
                    mediaPlayer.forward()
                }
                ACTION_BACKWARD -> {
                    mediaPlayer.backward()
                }
                ACTION_SET_EQUALIZER -> {
                    mediaPlayer.equalizerId = arg1 as Int
                }
                ACTION_RELOAD_AUDIO_EFFECTS -> {
                    updateTomSteadyEnabled()
                    updateTubeAmpEnabled()
                    updateAudioEffects()
                    mediaPlayer.refreshAudioPipeline()
                }
                ACTION_SEEK -> {
                    mediaPlayer.seekTo(arg1 as Int)
                }
                ACTION_REMOVE_MUSIC_AT -> {
                    mediaPlayer.removeAt(arg1 as Int)
                }
                ACTION_UPDATE_AUDIO_FOCUS -> {
                    updateAudioFocus()
                }
                ACTION_SET_TOM_STEADY_ENABLED -> {
                    updateTomSteadyEnabled()
                    mediaPlayer.refreshAudioPipeline()
                }
                ACTION_SET_TOM_STEADY_PARAMETERS -> {
                    // 处理TomSteady参数设置
                    if (arg1 is Map<*, *>) {
                        val params = arg1
                        val targetLevel = params["targetLevel"] as? Float
                        val maxGain = params["maxGain"] as? Float
                        val minGain = params["minGain"] as? Float
                        val attackTime = params["attackTime"] as? Float
                        val releaseTime = params["releaseTime"] as? Float
                        
                        mediaPlayer.setTomSteadyParameters(
                            targetLevel = targetLevel,
                            maxGain = maxGain,
                            minGain = minGain,
                            attackTime = attackTime,
                            releaseTime = releaseTime
                        )
                    }
                }
                ACTION_SET_TUBE_AMP_ENABLED -> {
                    updateTubeAmpEnabled()
                    mediaPlayer.refreshAudioPipeline()
                }
                ACTION_SET_TUBE_AMP_PRESET -> {
                    // 处理胆机预设
                    if (arg1 is com.example.podclassic.media.TubeAmpPreset) {
                        mediaPlayer.applyTubeAmpPreset(arg1)
                        mediaPlayer.refreshAudioPipeline()
                    }
                }
                ACTION_SET_TUBE_AMP_PARAMETERS -> {
                    // 处理胆机参数设置
                    if (arg1 is Map<*, *>) {
                        val params = arg1
                        val gain = params["gain"] as? Float
                        val saturation = params["saturation"] as? Float
                        val harmonics = params["harmonics"] as? Float
                        val ratio = params["ratio"] as? Float
                        val attack = params["attack"] as? Float
                        val release = params["release"] as? Float
                        val warmth = params["warmth"] as? Float

                        mediaPlayer.setTubeAmpParameters(
                            gain = gain,
                            saturation = saturation,
                            harmonics = harmonics,
                            ratio = ratio,
                            attack = attack,
                            release = release,
                            warmth = warmth
                        )
                    }
                }
                else -> {
                    throw IllegalArgumentException("unknown action")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun shufflePlay() {
        val playlist = if (SPManager.getBoolean(SPManager.SP_PLAY_ALL)) {
            MediaStoreUtil.getMusicList()
        } else {
            MusicTable.favourite.getList()
        }
        mediaPlayer.shufflePlay(playlist)
    }

    private fun updateAudioFocus() {
        mediaPlayer.enableAudioFocus = SPManager.getBoolean(SPManager.SP_AUDIO_FOCUS, true)
    }

    private fun updateTomSteadyEnabled() {
        val storedTarget = SPManager.getFloat(SPManager.SP_TOM_STEADY_TARGET_LEVEL, 0.16f)
        val storedMaxGain = SPManager.getFloat(SPManager.SP_TOM_STEADY_MAX_GAIN, 6f)
        val target = if (storedTarget in 0.08f..0.20f) storedTarget else 0.16f
        val maxGain = if (storedMaxGain in 0f..9f) storedMaxGain else 6f
        SPManager.setFloat(SPManager.SP_TOM_STEADY_TARGET_LEVEL, target)
        SPManager.setFloat(SPManager.SP_TOM_STEADY_MAX_GAIN, maxGain)
        mediaPlayer.setTomSteadyParameters(
            // Migrate values from the old placeholder implementation, whose 0.7 target clipped.
            targetLevel = target,
            maxGain = maxGain,
            attackTime = SPManager.getFloat(SPManager.SP_TOM_STEADY_ATTACK_TIME, 80f),
            releaseTime = SPManager.getFloat(SPManager.SP_TOM_STEADY_RELEASE_TIME, 900f)
        )
        mediaPlayer.tomSteadyEnabled = SPManager.getBoolean(SPManager.SP_TOM_STEADY_ENABLED)
    }

    private fun updateTubeAmpEnabled() {
        val wasEnabled = SPManager.getBoolean(SPManager.SP_TUBE_AMP_ENABLED)
        val warmth = SPManager.getFloat(SPManager.SP_TUBE_AMP_WARMTH, 0.5f)
        val saturation = SPManager.getFloat(SPManager.SP_TUBE_AMP_SATURATION, 0.4f)
        val harmonics = SPManager.getFloat(SPManager.SP_TUBE_AMP_HARMONICS, 0.2f)
        var preset = com.example.podclassic.media.TubeAmpPreset.values()[
            SPManager.getInt(SPManager.SP_TUBE_AMP_PRESET).coerceIn(
                0,
                com.example.podclassic.media.TubeAmpPreset.values().lastIndex
            )
        ]
        if (wasEnabled && preset == com.example.podclassic.media.TubeAmpPreset.NONE) {
            preset = com.example.podclassic.media.TubeAmpPreset.WARM
            SPManager.setInt(SPManager.SP_TUBE_AMP_PRESET, preset.ordinal)
        }
        mediaPlayer.applyTubeAmpPreset(preset)
        mediaPlayer.setTubeAmpParameters(
            warmth = warmth,
            saturation = saturation,
            harmonics = harmonics
        )
        mediaPlayer.tubeAmpEnabled = wasEnabled
    }

    private fun updateAudioEffects() {
        mediaPlayer.equalizerEnabled = SPManager.getBoolean(SPManager.SP_EQUALIZER_ENABLED)
        mediaPlayer.equalizerId = SPManager.getInt(SPManager.SP_EQUALIZER)
        mediaPlayer.setEqualizerStrength(
            SPManager.getFloat(SPManager.SP_EQUALIZER_STRENGTH, 1f)
        )
        mediaPlayer.setVolumeNormalization(
            enabled = SPManager.getBoolean(SPManager.SP_VOLUME_NORMALIZATION_ENABLED),
            targetDb = SPManager.getFloat(SPManager.SP_VOLUME_NORMALIZATION_TARGET_DB, -16f),
            maxBoostDb = SPManager.getFloat(SPManager.SP_VOLUME_NORMALIZATION_MAX_BOOST_DB, 6f)
        )
        mediaPlayer.setClearBass(
            enabled = SPManager.getBoolean(SPManager.SP_CLEAR_BASS_ENABLED),
            level = SPManager.getInt(SPManager.SP_CLEAR_BASS_LEVEL).let { if (it in 1..5) it else 2 }
        )
        val crossfeedIndex = SPManager.getInt(SPManager.SP_CROSSFEED_LEVEL)
            .coerceIn(0, com.example.podclassic.media.CrossfeedLevel.values().lastIndex)
        mediaPlayer.setCrossfeed(
            enabled = SPManager.getBoolean(SPManager.SP_CROSSFEED_ENABLED),
            level = com.example.podclassic.media.CrossfeedLevel.values()[crossfeedIndex]
        )
    }

    data class Action(val action: String, val arg1: Any?, val arg2: Any?)

    private fun handleAction(message: Message) {
        val action = message.obj as Action
        handleAction(action.action, action.arg1, action.arg2)
    }

    @Synchronized
    private fun stop() {
        if (serviceStopping) return
        serviceStopping = true
        if (this::handler.isInitialized) {
            handler.removeCallbacksAndMessages(null)
        }
        mediaPlayer.stop()
        updatePlaybackState()
        mediaSessionCompat.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForeground = false
        lastNotificationState = null
        MediaPresenter.disconnect()
        stopSelf()
    }

    inner class ServiceBinder : Binder() {
        val mediaController = MediaController()
    }

    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    inner class MediaController {
        fun sendMessage(action: String, arg1: Any? = null, arg2: Any? = null) {
            val message = Message.obtain().apply { obj = Action(action, arg1, arg2) }
            handler.sendMessage(message)
        }

        fun getIndex(): Int = mediaPlayer.getIndex()
        fun getCurrent(): Music? = mediaPlayer.getCurrent()
        fun forward(): Boolean = mediaPlayer.forward()
        fun backward(): Boolean = mediaPlayer.backward()
        fun getDuration(): Int = mediaPlayer.getDuration()
        fun getProgress(): Int = mediaPlayer.getProgress()
        fun getPlayMode(): PlayMode = mediaPlayer.getPlayMode()
        fun getRepeatMode(): RepeatMode = mediaPlayer.getRepeatMode()
        fun getStopTime(): Int = mediaPlayer.stopTime
        fun getPlaylist(): ArrayList<Music> = mediaPlayer.getPlaylist()
        fun isPlaying(): Boolean = mediaPlayer.isPlaying
        fun isPrepared(): Boolean = mediaPlayer.isPrepared
        fun getPresetList(): Array<String?> = mediaPlayer.getPresetList()
    }
}
