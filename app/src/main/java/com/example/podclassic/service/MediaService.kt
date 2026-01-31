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
import com.example.podclassic.bean.Music
import com.example.podclassic.media.MediaPlayer
import com.example.podclassic.media.PlayMode
import com.example.podclassic.media.RepeatMode
import com.example.podclassic.storage.MusicTable
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.MediaStoreUtil
import com.example.podclassic.util.VolumeUtil
import kotlin.system.exitProcess


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
    }

    private lateinit var mediaSessionCompat: MediaSessionCompat

    private val playbackStateCompatBuilder = PlaybackStateCompat.Builder()
        .setActions(
            PlaybackStateCompat.ACTION_FAST_FORWARD
                    or PlaybackStateCompat.ACTION_REWIND
                    or PlaybackStateCompat.ACTION_PLAY_PAUSE
                    or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    or PlaybackStateCompat.ACTION_STOP
                    or PlaybackStateCompat.ACTION_SEEK_TO
                    or PlaybackStateCompat.ACTION_PAUSE
                    or PlaybackStateCompat.ACTION_PLAY
        )
        .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)

    private lateinit var notificationManager: NotificationManager

    private val mediaSessionCallBack = object : MediaSessionCompat.Callback() {
        override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
            mediaBroadcastReceiver.onReceive(this@MediaService, mediaButtonIntent)
            return true
        }

        override fun onRewind() {
            mediaPlayer.backward()
        }

        override fun onFastForward() {
            mediaPlayer.forward()
        }

        override fun onSkipToPrevious() {
            mediaPlayer.prev()
        }

        override fun onSkipToNext() {
            mediaPlayer.next()
        }

        override fun onPause() {
            if (mediaPlayer.isPlaying) mediaPlayer.pause()
        }

        override fun onPlay() {
            if (!mediaPlayer.isPlaying) mediaPlayer.play()
        }

        override fun onSeekTo(pos: Long) {
            mediaPlayer.seekTo(pos.toInt())
        }

        override fun onStop() {
            mediaPlayer.stop()
        }
    }

    private val onMediaChangeListener = object : MediaPlayer.OnMediaChangeListener<Music> {
        override fun onMediaMetadataChange(mediaPlayer: MediaPlayer<Music>) {
            val current = mediaPlayer.getCurrent()

            sendNotification()

            MediaPresenter.music.set(current)
            current ?: return

            mediaSessionCompat.setMetadata(
                MediaMetadataCompat.Builder()
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, current.image)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, current.album)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, current.artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, current.title)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, current.duration)
                    .build()
            )
        }

        override fun onPlaybackStateChange(mediaPlayer: MediaPlayer<Music>) {
            val state = if (mediaPlayer.isPlaying) {
                PlaybackStateCompat.STATE_PLAYING
            } else {
                PlaybackStateCompat.STATE_PAUSED
            }
            mediaSessionCompat.setPlaybackState(
                playbackStateCompatBuilder.setState(
                    state,
                    mediaPlayer.getProgress().toLong(),
                    1f
                ).build()
            )
            sendNotification()
            MediaPresenter.playState.set(mediaPlayer.getPlayState())
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

    private fun sendNotification() {
        val music = mediaPlayer.getCurrent()
        if (music == null) {
            stopForeground(true)
            return
        }
        if (notificationBuilding) {
            return
        }
        notificationBuilding = true
        val notification = notificationManager.buildNotification(music, mediaPlayer.isPlaying)
        startForeground(1, notification)
        notificationBuilding = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val action = intent?.action
        if (action != null) {
            handleAction(action, null, null)
        }

        sendNotification()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()

        val component =
            ComponentName(packageName, "com.example.podclassic.media.MediaBroadcastReceiver")

        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        mediaButtonIntent.component = component
        val pendingIntent =
            PendingIntent.getBroadcast(this, 0, mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE)
        mediaSessionCompat = MediaSessionCompat(this, "MusicService", component, pendingIntent)
        mediaSessionCompat.setCallback(mediaSessionCallBack)
        mediaSessionCompat.setSessionActivity(
            packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
                PendingIntent.getActivity(this, 0, sessionIntent, PendingIntent.FLAG_IMMUTABLE)
            })

        mediaSessionCompat.isActive = true
        mediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        mediaSessionCompat.setPlaybackState(playbackStateCompatBuilder.build())

        //sessionToken = mediaSessionCompat.sessionToken

        notificationManager = NotificationManager(this, mediaSessionCompat.sessionToken)

        mediaPlayer = MediaPlayer(this, mediaAdapter)
        mediaPlayer.addOnMediaChangeListener(onMediaChangeListener)

        mediaPlayer.onDataChangeListener = onDataChangeListener

        mediaPlayer.setPlayMode(
            PlayMode.getPlayMode(
                SPManager.getInt(
                    SPManager.SP_PLAY_MODE
                )
            )
        )
        mediaPlayer.setRepeatMode(
            RepeatMode.getRepeatMode(
                SPManager.getInt(
                    SPManager.SP_REPEAT_MODE
                )
            )
        )

        updateAudioFocus()


        registerBroadcastReceiver()


        if (!this::handlerThread.isInitialized) {
            handlerThread = HandlerThread("ActionMQ")
            handlerThread.start()
            handler = object : Handler(handlerThread.looper) {
                override fun handleMessage(msg: Message) {
                    handleAction(msg)
                }
            }

        }
    }

    override fun onDestroy() {
        super.onDestroy()
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
        mediaPlayer.enableAudioFocus = SPManager.getBoolean(SPManager.SP_AUDIO_FOCUS)
    }

    data class Action(val action: String, val arg1: Any?, val arg2: Any?)

    private fun handleAction(message: Message) {
        val action = message.obj as Action
        handleAction(action.action, action.arg1, action.arg2)
    }

    private fun stop() {
        mediaPlayer.stop()
        stopForeground(true)
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
