package com.example.podclassic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import android.widget.Toast
import com.example.podclassic.R
import com.example.podclassic.`object`.MediaPlayer
import com.example.podclassic.activity.MainActivity
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.service.MediaPlayerService
import com.example.podclassic.storage.SPManager
import com.example.podclassic.storage.SaveMusics


class AppWidget : AppWidgetProvider(), MediaPlayer.OnMediaChangeListener {

    init {
        if (SPManager.getBoolean(SPManager.SP_HAS_WIDGET)) {
            MediaPlayer.addOnMediaChangeListener(this)
        }
    }

    companion object {
        private val pendingIntentPrev by lazy {
            val context = BaseApplication.getContext()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) PendingIntent.getForegroundService(context, 1, Intent(context, MediaPlayerService::class.java).apply { action = MediaPlayerService.ACTION_PREV
            }, PendingIntent.FLAG_UPDATE_CURRENT)
            else PendingIntent.getService(context, 1, Intent(context, MediaPlayerService::class.java).apply { action = MediaPlayerService.ACTION_PREV }, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private val pendingIntentPause by lazy {
            val context = BaseApplication.getContext()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) PendingIntent.getForegroundService(context, 2, Intent(context, MediaPlayerService::class.java).apply { action = MediaPlayerService.ACTION_PAUSE
            }, PendingIntent.FLAG_UPDATE_CURRENT)
            else PendingIntent.getService(context, 2, Intent(context, MediaPlayerService::class.java).apply { action = MediaPlayerService.ACTION_PAUSE }, PendingIntent.FLAG_UPDATE_CURRENT)
        }


        private val pendingIntentNext by lazy {
            val context = BaseApplication.getContext()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) PendingIntent.getForegroundService(context, 3, Intent(context, MediaPlayerService::class.java).apply { action = MediaPlayerService.ACTION_NEXT
            }, PendingIntent.FLAG_UPDATE_CURRENT)
            else PendingIntent.getService(context, 3, Intent(context, MediaPlayerService::class.java).apply { action = MediaPlayerService.ACTION_NEXT }, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private val pendingIntentFavorite by lazy {
            val context = BaseApplication.getContext()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) PendingIntent.getForegroundService(context, 4, Intent(context, MediaPlayerService::class.java).apply { action = MediaPlayerService.ACTION_FAVORITE }, PendingIntent.FLAG_UPDATE_CURRENT)
            else PendingIntent.getService(context, 4, Intent(context, MediaPlayerService::class.java).apply { action = MediaPlayerService.ACTION_FAVORITE }, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private val pendingIntentActivity by lazy {
            val context = BaseApplication.getContext()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) PendingIntent.getForegroundService(context, 5, Intent(context, MainActivity::class.java).apply {  action = MediaPlayerService.ACTION_MAIN }, PendingIntent.FLAG_UPDATE_CURRENT)
            else PendingIntent.getService(context, 5, Intent(context, MainActivity::class.java).apply {  action = MediaPlayerService.ACTION_MAIN }, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        fun updateRemoteViews() {
            val context = BaseApplication.getContext()
            Toast.makeText(context, "update",0).show()
            val appWidgetManager = AppWidgetManager.getInstance(BaseApplication.getContext())

            val music = MediaPlayer.getCurrent() ?: return
            val remoteViews = RemoteViews(context.packageName, R.layout.app_widget)
            remoteViews.setTextViewText(R.id.tv_name, music.name)
            remoteViews.setTextViewText(R.id.tv_singer, music.singer)
            remoteViews.setOnClickPendingIntent(R.id.btn_pause, pendingIntentPause)
            remoteViews.setOnClickPendingIntent(R.id.btn_prev, pendingIntentPrev)
            remoteViews.setOnClickPendingIntent(R.id.btn_next, pendingIntentNext)
            remoteViews.setOnClickPendingIntent(R.id.btn_favorite, pendingIntentFavorite)
            remoteViews.setOnClickPendingIntent(R.id.image, pendingIntentActivity)
            MediaPlayer.image.apply {
                if (this == null) {
                    remoteViews.setImageViewResource(R.id.image, R.drawable.ic_default)
                } else {
                    remoteViews.setImageViewBitmap(R.id.image, this)
                }
            }
            remoteViews.setImageViewResource(R.id.btn_pause, if (MediaPlayer.isPlaying) R.drawable.ic_pause_grey_800_36dp else R.drawable.ic_play_arrow_grey_800_36dp)
            remoteViews.setImageViewResource(R.id.btn_favorite, if (SaveMusics.loveList.contains(music)) R.drawable.ic_favorite_grey_800_24dp else R.drawable.ic_favorite_border_grey_800_24dp)
            val componentName = ComponentName(context, AppWidget::class.java)
            appWidgetManager?.updateAppWidget(componentName, remoteViews)
        }
    }

    override fun onUpdate(ctx: Context?, appWidgetManager: AppWidgetManager?, appWidgetIds: IntArray?) {
        super.onUpdate(ctx, appWidgetManager, appWidgetIds)
        updateRemoteViews()
    }

    override fun onMediaChange() {}

    override fun onMediaChangeFinished() {
        updateRemoteViews()
    }

    override fun onPlayStateChange() {
        updateRemoteViews()
    }

    override fun onEnabled(context: Context?) {
        super.onEnabled(context)
        SPManager.setBoolean(SPManager.SP_HAS_WIDGET, true)
        MediaPlayer.addOnMediaChangeListener(this)
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        SPManager.setBoolean(SPManager.SP_HAS_WIDGET, false)
        MediaPlayer.removeOnMediaChangeListener(this)
    }
}