package com.example.podclassic.service

import android.app.*
import android.app.Notification.MediaStyle
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import com.example.podclassic.R
import com.example.podclassic.`object`.Core
import com.example.podclassic.`object`.MediaPlayer
import com.example.podclassic.activity.MainActivity
import com.example.podclassic.storage.SaveMusics
import com.example.podclassic.util.AudioFocusManager
import com.example.podclassic.util.Colors


class MediaPlayerService : Service(), MediaPlayer.OnMediaChangeListener, AudioFocusManager.OnAudioFocusChangeListener {

    private val mediaPlayer = MediaPlayer
    private val audioFocusManager by lazy { AudioFocusManager(this) }

    init {
        mediaPlayer.addOnMediaChangeListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? { return null }

    private val mediaSession by lazy { MediaSession(this, packageName) }
    private val playbackStateBuilder = PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP)

    private val mediaSessionCallBack = object : MediaSession.Callback() {
        override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
            mediaBroadcastReceiver.onReceive(this@MediaPlayerService, mediaButtonIntent)
            return true
        }

        override fun onRewind() { MediaPlayer.backward() }
        override fun onFastForward() { MediaPlayer.forward() }
        override fun onSkipToPrevious() { MediaPlayer.prev() }
        override fun onSkipToNext() { MediaPlayer.next() }
        override fun onPause() { if (MediaPlayer.isPlaying) mediaPlayer.pause() }
        override fun onPlay() { if (!MediaPlayer.isPlaying) mediaPlayer.pause() }
        override fun onSeekTo(pos: Long) { MediaPlayer.seekTo(pos.toInt()) }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.removeOnMediaChangeListener(this)
        audioFocusManager.onAudioFocusChangeListener = null
        stopForeground(true)

        mediaSession.setCallback(null)
        mediaSession.isActive = false
        mediaSession.release()
        unregisterBroadcastReceiver()
    }

    override fun onCreate() {
        super.onCreate()
        registerBroadcastReceiver()
        mediaSession.isActive = true
        mediaSession.setCallback(mediaSessionCallBack)

        val componentName = ComponentName(this, MediaBroadcastReceiver::class.java)
        packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        mediaButtonIntent.component = componentName
        val pendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, PendingIntent.FLAG_CANCEL_CURRENT)
        mediaSession.setMediaButtonReceiver(pendingIntent)
        mediaSession.setPlaybackState(playbackStateBuilder.build())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(packageName, "播放控制", NotificationManager.IMPORTANCE_LOW)
            channel.setSound(null, null)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onMediaChange() {
        sendNotification()
    }

    override fun onPlayStateChange() {
        sendNotification()
    }

    companion object {
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_NEXT = "action_next"
        const val ACTION_STOP = "action_stop"
        const val ACTION_MAIN = "action_main"
        const val ACTION_PREV = "action_prev"
        const val ACTION_FAVORITE = "action_favorite"
    }

    private val pendingIntentPause by lazy {
        val intent = Intent(this, MediaPlayerService::class.java)
        intent.action = ACTION_PAUSE
        val pendingIntent: PendingIntent?
        pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
        pendingIntent
    }

    private val pendingIntentFavorite by lazy {
        val intent = Intent(this, MediaPlayerService::class.java)
        intent.action = ACTION_FAVORITE
        val pendingIntent: PendingIntent?
        pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
        pendingIntent
    }

    private val actionPrev by lazy {
        val intent = Intent(this, MediaPlayerService::class.java)
        intent.action = ACTION_PREV
        val pendingIntent: PendingIntent?
        val action: Notification.Action?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pendingIntent = PendingIntent.getForegroundService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            action = Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_skip_previous_grey_800_36dp), "prev", pendingIntent).build()
        } else {
            pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            action = Notification.Action.Builder(R.drawable.ic_skip_previous_grey_800_36dp, "prev", pendingIntent).build()
        }
        action
    }

    private val actionNext by lazy {
        val intent = Intent(this, MediaPlayerService::class.java)
        intent.action = ACTION_NEXT
        val pendingIntent: PendingIntent?
        val action: Notification.Action?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pendingIntent = PendingIntent.getForegroundService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            action = Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_skip_next_grey_800_36dp), "next", pendingIntent).build()
        } else {
            pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            action = Notification.Action.Builder(R.drawable.ic_skip_next_grey_800_36dp, "next", pendingIntent).build()
        }
        action
    }

    private val actionStop by lazy {
        val intent = Intent(this, MediaPlayerService::class.java)
        intent.action = ACTION_STOP
        val pendingIntent: PendingIntent?
        val action: Notification.Action?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pendingIntent = PendingIntent.getForegroundService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            action = Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_close_grey_800_24dp), "stop", pendingIntent).build()
        } else {
            pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            action = Notification.Action.Builder(R.drawable.ic_close_grey_800_24dp, "stop", pendingIntent).build()
        }
        action
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> Core.exit()
            ACTION_NEXT -> mediaPlayer.next()
            ACTION_PAUSE -> mediaPlayer.pause()
            ACTION_PREV -> mediaPlayer.prev()
            ACTION_FAVORITE -> {
                val music = mediaPlayer.getCurrent()
                if (music != null) {
                    if (SaveMusics.loveList.contains(music)) {
                        SaveMusics.loveList.remove(music)
                    } else {
                        SaveMusics.loveList.add(music)
                    }
                    sendNotification()
                }
            }
            else -> sendNotification()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private val mediaStyle by lazy {
        val style = MediaStyle()
        style.setMediaSession(mediaSession.sessionToken)
        style
    }

    @Suppress("DEPRECATION")
    private fun sendNotification() {
        val music = mediaPlayer.getCurrent()
        if (music == null) {
            stopForeground(true)
            return
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, packageName) else Notification.Builder(this)

        builder.style = mediaStyle
        builder.setSmallIcon(R.drawable.ic_play_arrow_grey_800_36dp)
        builder.setShowWhen(false)
        builder.setOnlyAlertOnce(true)
        builder.setLargeIcon(music.getImage())
        val contentIntent = Intent(this, MainActivity::class.java)
        contentIntent.action = ACTION_MAIN
        builder.setContentIntent(PendingIntent.getActivity(this, 3, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT))

        val actionPause = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Action.Builder(Icon.createWithResource(this, if (mediaPlayer.isPlaying) R.drawable.ic_pause_grey_800_36dp else R.drawable.ic_play_arrow_grey_800_36dp), "pause", pendingIntentPause).build()
        } else {
            Notification.Action.Builder(if (mediaPlayer.isPlaying) R.drawable.ic_pause_grey_800_36dp else R.drawable.ic_play_arrow_grey_800_36dp, "pause", pendingIntentPause).build()
        }

        val actionFavorite = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Action.Builder(Icon.createWithResource(this, if (SaveMusics.loveList.contains(music)) R.drawable.ic_favorite_grey_800_24dp else R.drawable.ic_favorite_border_grey_800_24dp), "favorite", pendingIntentFavorite).build()
        } else {
            Notification.Action.Builder(if (SaveMusics.loveList.contains(music)) R.drawable.ic_favorite_grey_800_24dp else R.drawable.ic_favorite_border_grey_800_24dp, "favorite", pendingIntentFavorite).build()
        }

        builder.addAction(actionPrev)
        builder.addAction(actionPause)
        builder.addAction(actionNext)
        builder.addAction(actionFavorite)
        builder.addAction(actionStop)

        builder.setContentTitle(music.name)
        builder.setContentText(music.singer)

        builder.setColor(Colors.color_primary)

        builder.setVisibility(Notification.VISIBILITY_PUBLIC)

        val notification = builder.build()
        notification.flags = Notification.FLAG_ONGOING_EVENT
        startForeground(1, notification)

        val state = if (mediaPlayer.isPlaying)  PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val mediaMetadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, music.name)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, music.album)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, music.singer)
            .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, music.getImage())
            .build()
        mediaSession.setMetadata(mediaMetadata)
        mediaSession.setPlaybackState(playbackStateBuilder.setState(state, mediaPlayer.getProgress().toLong(), 1f).build())
    }

    private val mediaBroadcastReceiver = MediaBroadcastReceiver()

    private var broadcastReceiverRegistered = false

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

    override fun onAudioFocusGain() {
        mediaSession.isActive = true
    }

    override fun onAudioFocusLoss() {}
}