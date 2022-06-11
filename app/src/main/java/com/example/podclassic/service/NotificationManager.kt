package com.example.podclassic.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

class NotificationManager(context: Context, sessionToken: MediaSessionCompat.Token) {

    init {
        val systemNotificationManagerCompat = NotificationManagerCompat.from(context)

        val channel = NotificationChannelCompat.Builder(context.packageName, 0)
            .setName("media notification")
            .build()
        systemNotificationManagerCompat.createNotificationChannel(channel)
    }

    private val contentIntent = PendingIntent.getActivity(
        context,
        10,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private val pendingIntentPause = PendingIntent.getService(
        context,
        2,
        Intent(context, MediaService::class.java).apply { action = MediaService.ACTION_PLAY_PAUSE },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private val pendingIntentFavorite = PendingIntent.getService(
        context,
        4,
        Intent(context, MediaService::class.java).apply {
            action = MediaService.ACTION_FAVORITE_CHANGE
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )


    private val actionPrev = NotificationCompat.Action.Builder(
        IconCompat.createWithResource(
            context,
            R.drawable.ic_skip_previous_grey_800_36dp
        ),
        "prev",
        PendingIntent.getService(
            context,
            1,
            Intent(context, MediaService::class.java).apply { action = MediaService.ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    ).build()
    private val actionNext = NotificationCompat.Action.Builder(
        IconCompat.createWithResource(
            context,
            R.drawable.ic_skip_next_grey_800_36dp
        ),
        "next",
        PendingIntent.getService(
            context,
            3,
            Intent(context, MediaService::class.java).apply { action = MediaService.ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    ).build()
    private val actionStop = NotificationCompat.Action.Builder(
        IconCompat.createWithResource(
            context,
            R.drawable.ic_close_grey_800_24dp
        ),
        "stop",
        PendingIntent.getService(
            context,
            5,
            Intent(context, MediaService::class.java).apply { action = MediaService.ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    ).build()

    private val notificationBuilder = NotificationCompat.Builder(context, context.packageName)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setSmallIcon(R.drawable.ic_play_arrow_grey_800_36dp)
        .setShowWhen(false)
        .setOnlyAlertOnce(true)
        .setDeleteIntent(actionStop.actionIntent)
        .setContentIntent(contentIntent)
        .setColor(Colors.color_primary)
        .setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(sessionToken)
                .setShowActionsInCompactView(1, 2, 3)
                .setShowCancelButton(true)
        )

    fun buildNotification(music: Music, isPlaying: Boolean): Notification {
        val image = music.image?: Icons.EMPTY

        val actionPause = NotificationCompat.Action.Builder(
            if (isPlaying) R.drawable.ic_pause_grey_800_36dp else R.drawable.ic_play_arrow_grey_800_36dp,
            "pause",
            pendingIntentPause
        ).build()

        val actionFavorite = NotificationCompat.Action.Builder(
            if (MusicTable.favourite.contains(music)) R.drawable.ic_favorite_grey_800_24dp else R.drawable.ic_favorite_border_grey_800_24dp,
            "favorite",
            pendingIntentFavorite
        ).build()

        val notification = notificationBuilder
            .clearActions()
            .setLargeIcon(image)
            .addAction(actionPrev)
            .addAction(actionPause)
            .addAction(actionNext)
            .addAction(actionFavorite)
            .addAction(actionStop)
            .setContentTitle(music.title)
            .setContentText(music.artist)
            .setOngoing(isPlaying)
            .build()
        notification.flags = Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE

        return notification
    }
}