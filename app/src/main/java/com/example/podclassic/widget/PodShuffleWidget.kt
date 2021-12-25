package com.example.podclassic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.example.podclassic.R
import com.example.podclassic.activity.MainActivity
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.service.MediaPlayerService
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.Colors

class PodShuffleWidget : AppWidgetProvider() {

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

        private val pendingIntentVolumeUp by lazy {
            val context = BaseApplication.context
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) PendingIntent.getForegroundService(context, 4, Intent(context, MediaPlayerService::class.java).apply { action = MediaPlayerService.ACTION_VOLUME_UP }, PendingIntent.FLAG_UPDATE_CURRENT)
            else PendingIntent.getService(context, 4, Intent(context, MediaPlayerService::class.java).apply { action = MediaPlayerService.ACTION_FAVORITE }, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private val pendingIntentVolumeDown by lazy {
            val context = BaseApplication.context
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) PendingIntent.getForegroundService(context, 5, Intent(context, MediaPlayerService::class.java).apply { action = MediaPlayerService.ACTION_VOLUME_DOWN }, PendingIntent.FLAG_UPDATE_CURRENT)
            else PendingIntent.getService(context, 4, Intent(context, MediaPlayerService::class.java).apply { action = MediaPlayerService.ACTION_FAVORITE }, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private val pendingIntentActivity by lazy {
            val context = BaseApplication.context
            PendingIntent.getActivity(context, 6, Intent(context, MainActivity::class.java).apply {  action = MediaPlayerService.ACTION_MAIN }, PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    override fun onUpdate(context: Context?, appWidgetManager: AppWidgetManager?, appWidgetIds: IntArray?) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        //updateRemoteViews()
        //return
        if (appWidgetIds == null) {
            return
        }
        if (context == null) {
            return
        }
        for (appWidgetId in appWidgetIds) {
            val remoteViews = RemoteViews(context.packageName, R.layout.view_ipod_shuffle)

            remoteViews.setOnClickPendingIntent(R.id.btn_pause, pendingIntentPause)
            remoteViews.setOnClickPendingIntent(R.id.btn_prev, pendingIntentPrev)
            remoteViews.setOnClickPendingIntent(R.id.btn_next, pendingIntentNext)
            remoteViews.setOnClickPendingIntent(R.id.btn_volume_down, pendingIntentVolumeDown)
            remoteViews.setOnClickPendingIntent(R.id.btn_volume_up, pendingIntentVolumeUp)
            remoteViews.setOnClickPendingIntent(R.id.root, pendingIntentActivity)

            appWidgetManager?.updateAppWidget(appWidgetId, remoteViews)
        }
    }


}