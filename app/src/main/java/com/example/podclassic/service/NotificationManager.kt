package com.example.podclassic.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.podclassic.R
import com.example.podclassic.activity.MainActivity
import com.example.podclassic.bean.Music
import com.example.podclassic.storage.MusicTable
import com.example.podclassic.values.Colors
import com.example.podclassic.values.Icons

/**
 * 增强型通知管理器
 * 提供完整的媒体控制通知功能，包括播放控制、进度显示和歌曲信息展示
 */
class NotificationManager(context: Context, sessionToken: MediaSessionCompat.Token) {

    companion object {
        const val CHANNEL_ID = "media_playback_channel"
        const val CHANNEL_NAME = "媒体播放"
        const val NOTIFICATION_ID = 1
        
        // 通知操作请求码
        private const val REQUEST_CODE_PREV = 1
        private const val REQUEST_CODE_PLAY_PAUSE = 2
        private const val REQUEST_CODE_NEXT = 3
        private const val REQUEST_CODE_FAVORITE = 4
        private const val REQUEST_CODE_STOP = 5
        private const val REQUEST_CODE_CONTENT = 10
    }

    private val appContext = context.applicationContext
    private val systemNotificationManagerCompat = NotificationManagerCompat.from(appContext)

    init {
        createNotificationChannel()
    }

    /**
     * 创建通知渠道（Android 8.0+ 必需）
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(CHANNEL_NAME)
            .setDescription("音乐播放控制通知")
            .setShowBadge(false)
            .build()
        systemNotificationManagerCompat.createNotificationChannel(channel)
    }

    /**
     * 点击通知内容时打开主界面
     */
    private val contentIntent = PendingIntent.getActivity(
        appContext,
        REQUEST_CODE_CONTENT,
        Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /**
     * 创建用于启动服务的 PendingIntent
     */
    private fun createServicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(appContext, MediaService::class.java).apply {
            this.action = action
            // 添加 FLAG_ACTIVITY_CLEAR_TOP 防止创建多个实例
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getService(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 播放/暂停按钮的 PendingIntent
     */
    private val pendingIntentPlayPause by lazy {
        createServicePendingIntent(MediaService.ACTION_PLAY_PAUSE, REQUEST_CODE_PLAY_PAUSE)
    }

    /**
     * 收藏按钮的 PendingIntent
     */
    private val pendingIntentFavorite by lazy {
        createServicePendingIntent(MediaService.ACTION_FAVORITE_CHANGE, REQUEST_CODE_FAVORITE)
    }

    /**
     * 上一曲按钮操作
     */
    private val actionPrev by lazy {
        NotificationCompat.Action.Builder(
            IconCompat.createWithResource(appContext, R.drawable.ic_skip_previous_grey_800_36dp),
            "上一曲",
            createServicePendingIntent(MediaService.ACTION_PREV, REQUEST_CODE_PREV)
        ).build()
    }

    /**
     * 下一曲按钮操作
     */
    private val actionNext by lazy {
        NotificationCompat.Action.Builder(
            IconCompat.createWithResource(appContext, R.drawable.ic_skip_next_grey_800_36dp),
            "下一曲",
            createServicePendingIntent(MediaService.ACTION_NEXT, REQUEST_CODE_NEXT)
        ).build()
    }

    /**
     * 停止按钮操作
     */
    private val actionStop by lazy {
        NotificationCompat.Action.Builder(
            IconCompat.createWithResource(appContext, R.drawable.ic_close_grey_800_24dp),
            "停止",
            createServicePendingIntent(MediaService.ACTION_STOP, REQUEST_CODE_STOP)
        ).build()
    }

    /**
     * 基础通知构建器配置
     */
    private val notificationBuilder = NotificationCompat.Builder(appContext, CHANNEL_ID)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setSmallIcon(R.drawable.ic_play_arrow_grey_800_36dp)
        .setShowWhen(false)
        .setOnlyAlertOnce(true)
        .setDeleteIntent(actionStop.actionIntent)
        .setContentIntent(contentIntent)
        .setColor(Colors.color_primary)
        .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        .setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(sessionToken)
                .setShowActionsInCompactView(0, 1, 2) // 紧凑视图显示：上一曲、播放/暂停、下一曲
                .setShowCancelButton(true)
                .setCancelButtonIntent(actionStop.actionIntent)
        )

    /**
     * 构建媒体播放通知
     * 
     * @param music 当前播放的音乐
     * @param isPlaying 是否正在播放
     * @param position 当前播放位置（毫秒）
     * @param duration 总时长（毫秒）
     * @return 构建好的通知对象
     */
    fun buildNotification(
        music: Music, 
        isPlaying: Boolean,
        position: Int = 0,
        duration: Int = 0
    ): Notification {
        // 获取专辑封面，如果没有则使用默认图标
        val largeIcon = music.image ?: Icons.EMPTY

        // 播放/暂停按钮
        val actionPlayPause = NotificationCompat.Action.Builder(
            if (isPlaying) R.drawable.ic_pause_grey_800_36dp else R.drawable.ic_play_arrow_grey_800_36dp,
            if (isPlaying) "暂停" else "播放",
            pendingIntentPlayPause
        ).build()

        // 收藏按钮
        val isFavorite = MusicTable.favourite.contains(music)
        val actionFavorite = NotificationCompat.Action.Builder(
            if (isFavorite) R.drawable.ic_favorite_grey_800_24dp else R.drawable.ic_favorite_border_grey_800_24dp,
            if (isFavorite) "取消收藏" else "收藏",
            pendingIntentFavorite
        ).build()

        // 构建通知
        val notification = notificationBuilder
            .clearActions()
            .setLargeIcon(largeIcon)
            .addAction(actionPrev)
            .addAction(actionPlayPause)
            .addAction(actionNext)
            .addAction(actionFavorite)
            .addAction(actionStop)
            .setContentTitle(music.title)
            .setContentText("${music.artist} - ${music.album}")
            .setSubText(formatDuration(duration))
            .setOngoing(isPlaying)
            .setProgress(duration, position, false) // 添加进度条
            .build()

        // 设置通知标志
        notification.flags = if (isPlaying) {
            Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE
        } else {
            Notification.FLAG_FOREGROUND_SERVICE
        }

        return notification
    }

    /**
     * 格式化时长显示
     */
    private fun formatDuration(durationMs: Int): String {
        if (durationMs <= 0) return "00:00"
        val minutes = durationMs / 1000 / 60
        val seconds = durationMs / 1000 % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * 更新通知进度
     * 用于实时更新播放进度
     */
    fun updateNotificationProgress(
        music: Music,
        isPlaying: Boolean,
        position: Int,
        duration: Int
    ): Notification {
        return buildNotification(music, isPlaying, position, duration)
    }
}