package com.example.podclassic.service

import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.*
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationManagerCompat
import com.example.podclassic.bean.Music
import com.example.podclassic.media.MediaPlayer
import com.example.podclassic.media.PlayMode
import com.example.podclassic.media.RepeatMode
import com.example.podclassic.storage.MusicTable
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.MediaStoreUtil
import com.example.podclassic.util.VolumeUtil
import kotlin.system.exitProcess


/**
 * 媒体播放服务
 * 
 * 提供完整的后台媒体播放功能，包括：
 * 1. 前台服务保证后台播放稳定性
 * 2. MediaSession 管理媒体会话状态
 * 3. 通知栏媒体控制界面
 * 4. 音频焦点管理
 * 5. 播放进度实时更新
 */
class MediaService : Service() {
    companion object {
        const val ACTION_SET_EQUALIZER = "action_set_equalizer"
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
        const val ACTION_SET_AGC_ENABLED = "action_set_agc_enabled"
        const val ACTION_SET_TOM_STEADY_ENABLED = "action_set_tom_steady_enabled"
        const val ACTION_SET_TOM_STEADY_PARAMETERS = "action_set_tom_steady_parameters"
        const val ACTION_INIT_TOM_STEADY = "action_init_tom_steady"
        
        // 进度更新间隔（毫秒）
        const val PROGRESS_UPDATE_INTERVAL = 1000L
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
            mediaPlayer.stop()
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
        val playbackSpeed = 1.0f
        
        mediaSessionCompat.setPlaybackState(
            playbackStateCompatBuilder
                .setState(state, position, playbackSpeed)
                .setBufferedPosition(mediaPlayer.getDuration().toLong())
                .build()
        )
    }

    /**
     * 媒体变更监听器
     * 处理歌曲切换和播放状态变化
     */
    private val onMediaChangeListener = object : MediaPlayer.OnMediaChangeListener<Music> {
        override fun onMediaMetadataChange(mediaPlayer: MediaPlayer<Music>) {
            val current = mediaPlayer.getCurrent()

            sendNotification()

            MediaPresenter.music.set(current)
            current ?: return

            // 构建完整的媒体元数据
            val metadataBuilder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, current.id?.toString() ?: "")
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, current.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, current.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, current.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, current.duration)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, current.albumId?.toString() ?: "")
            
            // 添加专辑封面（如果有）
            current.image?.let {
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
            }
            
            mediaSessionCompat.setMetadata(metadataBuilder.build())
            
            android.util.Log.d("MediaService", "Media metadata updated: ${current.title} - ${current.artist}")
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
    }

    private val mediaAdapter = object : MediaPlayer.MediaAdapter<Music> {
        override fun onLoadMedia(e: Music, mediaPlayer: android.media.MediaPlayer): Boolean {
            if (e.data != null && e.data.isNotBlank()) {
                try {
                    mediaPlayer.setDataSource(e.data)
                    return true
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    return false
                }
            } else if (e.uri != null) {
                try {
                    mediaPlayer.setDataSource(this@MediaService, e.uri)
                    return true
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    return false
                }
            }
            return false
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

    private var notificationBuilding = false
    
    // 进度更新相关
    private var progressUpdateRunnable: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 发送/更新通知
     * 包含播放进度信息
     */
    private fun sendNotification() {
        val music = mediaPlayer.getCurrent()
        if (music == null) {
            stopForeground(true)
            isForeground = false
            stopProgressUpdate()
            return
        }
        if (notificationBuilding) {
            return
        }
        notificationBuilding = true
        
        val position = mediaPlayer.getProgress()
        val duration = mediaPlayer.getDuration()
        
        val notification = notificationManager.buildNotification(
            music, 
            mediaPlayer.isPlaying,
            position,
            duration
        )
        startForeground(NotificationManager.NOTIFICATION_ID, notification)
        isForeground = true
        notificationBuilding = false
        
        // 如果正在播放，启动进度更新
        if (mediaPlayer.isPlaying) {
            startProgressUpdate()
        } else {
            stopProgressUpdate()
        }
    }
    
    /**
     * 启动进度更新定时器
     */
    private fun startProgressUpdate() {
        stopProgressUpdate()
        progressUpdateRunnable = object : Runnable {
            override fun run() {
                if (mediaPlayer.isPlaying) {
                    updateNotificationProgress()
                    mainHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL)
                }
            }
        }
        mainHandler.postDelayed(progressUpdateRunnable!!, PROGRESS_UPDATE_INTERVAL)
    }
    
    /**
     * 停止进度更新定时器
     */
    private fun stopProgressUpdate() {
        progressUpdateRunnable?.let {
            mainHandler.removeCallbacks(it)
        }
        progressUpdateRunnable = null
    }
    
    /**
     * 仅更新通知进度，不重建整个通知
     */
    private fun updateNotificationProgress() {
        val music = mediaPlayer.getCurrent() ?: return
        val position = mediaPlayer.getProgress()
        val duration = mediaPlayer.getDuration()
        
        val notification = notificationManager.updateNotificationProgress(
            music,
            mediaPlayer.isPlaying,
            position,
            duration
        )
        
        // 使用 NotificationManagerCompat 更新通知
        NotificationManagerCompat.from(this).notify(
            NotificationManager.NOTIFICATION_ID, 
            notification
        )
    }

    // 标记服务是否在前台运行
    private var isForeground = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("MediaService", "onStartCommand() called with action: ${intent?.action}, isForeground=$isForeground")

        // 确保服务在前台运行（Android 8.0+ 要求）
        if (!isForeground) {
            android.util.Log.d("MediaService", "Service not in foreground, starting foreground")
            startForegroundWithNotification()
        }

        val action = intent?.action
        if (action != null) {
            android.util.Log.d("MediaService", "Handling action: $action")
            handleAction(action, null, null)
        } else {
            android.util.Log.d("MediaService", "No action, just updating notification")
        }

        // 更新通知
        updateNotification()
        
        // 返回 START_STICKY 确保服务被杀死后能自动重启
        return START_STICKY
    }
    
    /**
     * 启动前台服务并显示通知
     */
    private fun startForegroundWithNotification() {
        val music = mediaPlayer.getCurrent()
        if (music != null) {
            val notification = notificationManager.buildNotification(
                music,
                mediaPlayer.isPlaying,
                mediaPlayer.getProgress(),
                mediaPlayer.getDuration()
            )
            startForeground(NotificationManager.NOTIFICATION_ID, notification)
            isForeground = true
        }
    }
    
    /**
     * 更新通知（不重建整个通知）
     */
    private fun updateNotification() {
        val music = mediaPlayer.getCurrent()
        if (music != null) {
            sendNotification()
        }
    }

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
        
        android.util.Log.d("MediaService", "Service initialized successfully")
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
        updateAgcEnabled()
        updateTomSteadyEnabled()
        
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
        super.onDestroy()
        isForeground = false
        stopProgressUpdate()
        mediaSessionCompat.release()
        unregisterBroadcastReceiver()
        handlerThread.quit()
        mediaPlayer.removeOnMediaChangeListener(onMediaChangeListener)
        mediaPlayer.release()
        exitProcess(0)
        //Core.exit()
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
                }
                ACTION_CANCEL_FAVORITE -> {
                    MusicTable.favourite.remove(arg1 as Music)
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
                ACTION_SEEK -> {
                    mediaPlayer.seekTo(arg1 as Int)
                }
                ACTION_REMOVE_MUSIC_AT -> {
                    mediaPlayer.removeAt(arg1 as Int)
                }
                ACTION_UPDATE_AUDIO_FOCUS -> {
                    updateAudioFocus()
                }
                ACTION_SET_AGC_ENABLED -> {
                    updateAgcEnabled()
                }
                ACTION_SET_TOM_STEADY_ENABLED -> {
                    updateTomSteadyEnabled()
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
                ACTION_INIT_TOM_STEADY -> {
                    // 初始化TomSteady处理器
                    mediaPlayer.initTomSteadyProcessor()
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

    private fun updateAgcEnabled() {
        mediaPlayer.agcEnabled = SPManager.getBoolean(SPManager.SP_AGC_ENABLED)
    }

    private fun updateTomSteadyEnabled() {
        mediaPlayer.tomSteadyEnabled = SPManager.getBoolean(SPManager.SP_TOM_STEADY_ENABLED)
    }

    data class Action(val action: String, val arg1: Any?, val arg2: Any?)

    private fun handleAction(message: Message) {
        val action = message.obj as Action
        handleAction(action.action, action.arg1, action.arg2)
    }

    private fun stop() {
        mediaPlayer.stop()
        stopForeground(true)
        isForeground = false
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
