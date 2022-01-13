package com.example.podclassic.service

import android.app.*
import android.app.Notification.MediaStyle
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import com.example.podclassic.`object`.Music
import com.example.podclassic.activity.MainActivity
import com.example.podclassic.storage.SaveMusics
import com.example.podclassic.util.AudioFocusManager
import com.example.podclassic.util.Colors
import com.example.podclassic.util.Icons
import com.example.podclassic.util.VolumeUtil


class MediaPlayerService : Service(), MediaPlayer.OnMediaChangeListener, AudioFocusManager.OnAudioFocusChangeListener {

    private val mediaPlayer = MediaPlayer.apply {
        addOnMediaChangeListener(this@MediaPlayerService)
    }
    private val audioFocusManager = AudioFocusManager(this)

    override fun onBind(intent: Intent?): IBinder? { return null }

    private lateinit var mediaSession: MediaSession

    private val playbackStateBuilder = PlaybackState.Builder().apply {
        setActions(PlaybackState.ACTION_FAST_FORWARD
                or PlaybackState.ACTION_REWIND
                or PlaybackState.ACTION_PLAY_PAUSE
                or PlaybackState.ACTION_SKIP_TO_NEXT
                or PlaybackState.ACTION_SKIP_TO_PREVIOUS
                or PlaybackState.ACTION_STOP
                or PlaybackState.ACTION_SEEK_TO
                or PlaybackState.ACTION_PAUSE
                or PlaybackState.ACTION_PLAY
        )
    }

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
        override fun onStop() { Core.exit() }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.removeOnMediaChangeListener(this)
        mediaPlayer.removeOnMediaChangeListener(this)
        audioFocusManager.onAudioFocusChangeListener = null
        stopForeground(true)
        //AppWidget.updateRemoteViews(null)

        mediaSession.apply {
            setCallback(null)
            isActive = false
            release()
        }
        unregisterBroadcastReceiver()
    }

    override fun onCreate() {
        super.onCreate()
        registerBroadcastReceiver()

        val componentName = ComponentName(applicationContext, MediaBroadcastReceiver::class.java)
        packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        mediaButtonIntent.component = componentName
        //val pendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, PendingIntent.FLAG_CANCEL_CURRENT)

        mediaSession = MediaSession(applicationContext, packageName).apply {
            isActive = true
            setCallback(mediaSessionCallBack)
            //setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS or MediaSession.FLAG_HANDLES_MEDIA_BUTTONS)
            //setMediaButtonReceiver(pendingIntent)
            setSessionActivity(pendingIntentActivity)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(packageName, "播放控制", NotificationManager.IMPORTANCE_LOW)
            channel.setSound(null, null)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onMediaChange() {}

    override fun onMediaChangeFinished() {
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
        const val ACTION_SHUFFLE = "action_shuffle"
        const val ACTION_FAVORITE = "action_favorite"
        const val ACTION_VOLUME_UP = "action_volume_up"
        const val ACTION_VOLUME_DOWN = "action_volume_donw"
    }

    private val contentIntent by lazy {
        PendingIntent.getActivity(applicationContext, 10, Intent(applicationContext, MainActivity::class.java).apply { action = ACTION_MAIN }, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private val pendingIntentShuffle by lazy {
        PendingIntent.getService(applicationContext, 100, Intent(applicationContext, MediaPlayerService::class.java).apply { action = ACTION_SHUFFLE }, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private val pendingIntentPause by lazy {
        PendingIntent.getService(applicationContext, 2, Intent(applicationContext, MediaPlayerService::class.java).apply { action = ACTION_PAUSE }, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private val pendingIntentActivity by lazy {
        PendingIntent.getActivity(applicationContext, 6, Intent(applicationContext, MainActivity::class.java).apply {  action = ACTION_MAIN }, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private val pendingIntentFavorite by lazy {
        PendingIntent.getService(applicationContext, 4, Intent(applicationContext, MediaPlayerService::class.java).apply { action = ACTION_FAVORITE }, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private val actionPrev by lazy {
        val intent = Intent(applicationContext, MediaPlayerService::class.java).apply { action = ACTION_PREV }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_skip_previous_grey_800_36dp), "prev", PendingIntent.getForegroundService(applicationContext, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)).build()
        else Notification.Action.Builder(R.drawable.ic_skip_previous_grey_800_36dp, "prev", PendingIntent.getService(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT  or PendingIntent.FLAG_IMMUTABLE)).build()
    }

    private val actionNext by lazy {
        val intent = Intent(applicationContext, MediaPlayerService::class.java).apply { action = ACTION_NEXT }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_skip_next_grey_800_36dp), "next", PendingIntent.getForegroundService(applicationContext, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT)).build()
        else Notification.Action.Builder(R.drawable.ic_skip_next_grey_800_36dp, "next", PendingIntent.getService(this, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT  or PendingIntent.FLAG_IMMUTABLE)).build()
    }

    private val actionStop by lazy {
        val intent = Intent(applicationContext, MediaPlayerService::class.java).apply { action = ACTION_STOP }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_close_grey_800_24dp), "stop", PendingIntent.getForegroundService(applicationContext, 5, intent, PendingIntent.FLAG_UPDATE_CURRENT)).build()
        else Notification.Action.Builder(R.drawable.ic_close_grey_800_24dp, "stop", PendingIntent.getService(this, 5, intent, PendingIntent.FLAG_UPDATE_CURRENT  or PendingIntent.FLAG_IMMUTABLE)).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val music = mediaPlayer.getCurrent()
        if (music == null) {
            if (intent?.action == ACTION_NEXT || intent?.action == ACTION_PAUSE || intent?.action == ACTION_PREV) {
                mediaPlayer.shufflePlay()
            }
        } else {
            when (intent?.action) {
                ACTION_STOP -> Core.exit()
                ACTION_NEXT -> mediaPlayer.next()
                ACTION_PAUSE -> mediaPlayer.pause()
                ACTION_PREV -> mediaPlayer.prev()
                ACTION_SHUFFLE -> mediaPlayer.shufflePlay()
                ACTION_FAVORITE -> {
                    if (SaveMusics.loveList.contains(music)) {
                        SaveMusics.loveList.remove(music)
                    } else {
                        SaveMusics.loveList.add(music)
                    }
                    sendNotification()
                }
                ACTION_VOLUME_UP -> VolumeUtil.volumeUp()
                ACTION_VOLUME_DOWN -> VolumeUtil.volumeDown()
                else -> sendNotification()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private val mediaStyle by lazy {
        MediaStyle().apply {
            setMediaSession(mediaSession.sessionToken)
            setShowActionsInCompactView(1,2,3)
        }
    }

    private fun sendNotification() {
        val music = MediaPlayer.getCurrent()
        //AppWidget.updateRemoteViews(music)
        if (music == null) {
            stopForeground(true)
            return
        }
        val image = MediaPlayer.getImage() ?: Icons.EMPTY

        val actionPause = Notification.Action.Builder(if (mediaPlayer.isPlaying) R.drawable.ic_pause_grey_800_36dp else R.drawable.ic_play_arrow_grey_800_36dp, "pause", pendingIntentPause).build()

        val actionFavorite = Notification.Action.Builder(if (SaveMusics.loveList.contains(music)) R.drawable.ic_favorite_grey_800_24dp else R.drawable.ic_favorite_border_grey_800_24dp, "favorite", pendingIntentFavorite).build()

        val builder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, packageName) else Notification.Builder(this)).apply {
            setPriority(Notification.PRIORITY_MAX)
            setVisibility(Notification.VISIBILITY_PUBLIC)
            setSmallIcon(R.drawable.ic_play_arrow_grey_800_36dp)
            setShowWhen(false)
            setOnlyAlertOnce(true)
            setDeleteIntent(actionStop.actionIntent)
            setContentIntent(contentIntent)
            setColor(Colors.color_primary)
            style = mediaStyle
            setLargeIcon(image)
            addAction(actionPrev)
            addAction(actionPause)
            addAction(actionNext)
            addAction(actionFavorite)
            addAction(actionStop)
            setContentTitle(music.name)
            setContentText(music.singer)
            setOngoing(mediaPlayer.isPlaying)

        }

        val notification = builder.build()
        notification.flags = Notification.FLAG_ONGOING_EVENT

        updateMediaMetadata(music, image)

        startForeground(1, notification)

        updatePlaybackState()
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

    override fun onSeek(progress: Int) {
        updatePlaybackState()
    }

    private fun updatePlaybackState() {
        val state = if (mediaPlayer.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        mediaSession.setPlaybackState(
            playbackStateBuilder.setState(state, mediaPlayer.getProgress().toLong(), 1f).build()
        )
    }

    private fun updateMediaMetadata(music : Music, image : Bitmap) {
        val mediaMetadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, music.name)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, music.album)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, music.singer)
            .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, image)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, mediaPlayer.getDuration().toLong())
            .build()
        mediaSession.setMetadata(mediaMetadata)
    }
}