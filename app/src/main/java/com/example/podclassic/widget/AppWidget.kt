package com.example.podclassic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.example.podclassic.R
import com.example.podclassic.`object`.MediaPlayer
import com.example.podclassic.`object`.Music
import com.example.podclassic.activity.MainActivity
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.service.MediaPlayerService
import com.example.podclassic.storage.SaveMusics


class AppWidget : AppWidgetProvider() {

    companion object {
        private val pendingIntentPrev by lazy {
            val context = BaseApplication.context
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) PendingIntent.getForegroundService(context, 1, Intent(context, MediaPlayerService::class.java).apply { action = MediaPlayerService.ACTION_PREV
            }, PendingIntent.FLAG_UPDATE_CURRENT)
            else PendingIntent.getService(context, 1, Intent(context, MediaPlayerService::class.java).apply { action = MediaPlayerService.ACTION_PREV }, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private val pendingIntentPause by lazy {
            val context = BaseApplication.context
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) PendingIntent.getForegroundService(context, 2, Intent(context, MediaPlayerService::class.java).apply { action = MediaPlayerService.ACTION_PAUSE
            }, PendingIntent.FLAG_UPDATE_CURRENT)
            else PendingIntent.getService(context, 2, Intent(context, MediaPlayerService::class.java).apply { action = MediaPlayerService.ACTION_PAUSE }, PendingIntent.FLAG_UPDATE_CURRENT)
        }


        private val pendingIntentNext by lazy {
            val context = BaseApplication.context
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) PendingIntent.getForegroundService(context, 3, Intent(context, MediaPlayerService::class.java).apply { action = MediaPlayerService.ACTION_NEXT
            }, PendingIntent.FLAG_UPDATE_CURRENT)
            else PendingIntent.getService(context, 3, Intent(context, MediaPlayerService::class.java).apply { action = MediaPlayerService.ACTION_NEXT }, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private val pendingIntentFavorite by lazy {
            val context = BaseApplication.context
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) PendingIntent.getForegroundService(context, 4, Intent(context, MediaPlayerService::class.java).apply { action = MediaPlayerService.ACTION_FAVORITE }, PendingIntent.FLAG_UPDATE_CURRENT)
            else PendingIntent.getService(context, 4, Intent(context, MediaPlayerService::class.java).apply { action = MediaPlayerService.ACTION_FAVORITE }, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private val pendingIntentActivity by lazy {
            val context = BaseApplication.context
            PendingIntent.getActivity(context, 5, Intent(context, MainActivity::class.java).apply {  action = MediaPlayerService.ACTION_MAIN }, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        fun updateRemoteViews(music : Music?) {
            val context = BaseApplication.context
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, AppWidget::class.java)

            appWidgetManager.getAppWidgetIds(componentName).apply {
                if (this == null || this.isEmpty()) {
                    return
                }
            }
            val remoteViews = RemoteViews(context.packageName, R.layout.app_widget)

            remoteViews.setTextViewText(R.id.tv_name, music?.name)
            remoteViews.setTextViewText(R.id.tv_singer, music?.singer)
            remoteViews.setOnClickPendingIntent(R.id.btn_pause, pendingIntentPause)
            remoteViews.setOnClickPendingIntent(R.id.btn_prev, pendingIntentPrev)
            remoteViews.setOnClickPendingIntent(R.id.btn_next, pendingIntentNext)
            remoteViews.setOnClickPendingIntent(R.id.btn_favorite, pendingIntentFavorite)
            remoteViews.setOnClickPendingIntent(R.id.root, pendingIntentActivity)
            MediaPlayer.image.apply {
                if (music == null || this == null) {
                    remoteViews.setImageViewResource(R.id.image, R.drawable.ic_default)
                } else {
                    remoteViews.setImageViewBitmap(R.id.image, this)
                }
            }
            remoteViews.setImageViewResource(R.id.btn_pause, if (MediaPlayer.isPlaying) R.drawable.ic_pause_grey_800_36dp else R.drawable.ic_play_arrow_grey_800_36dp)
            remoteViews.setImageViewResource(R.id.btn_favorite, if (music != null && SaveMusics.loveList.contains(music)) R.drawable.ic_favorite_grey_800_24dp else R.drawable.ic_favorite_border_grey_800_24dp)
            appWidgetManager?.updateAppWidget(componentName, remoteViews)
        }
    }

    override fun onEnabled(context: Context?) {
        super.onEnabled(context)
        updateRemoteViews(MediaPlayer.getCurrent())
    }
}