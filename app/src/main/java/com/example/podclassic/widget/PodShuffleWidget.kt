package com.example.podclassic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.podclassic.R
import com.example.podclassic.activity.MainActivity
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.service.MediaService

class PodShuffleWidget : AppWidgetProvider() {

    companion object {
        private val pendingIntentPrev = PendingIntent.getService(
            BaseApplication.context,
            1,
            Intent(BaseApplication.context, MediaService::class.java).apply {
                action = MediaService.ACTION_PREV
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        private val pendingIntentNext = PendingIntent.getService(
            BaseApplication.context,
            2,
            Intent(BaseApplication.context, MediaService::class.java).apply {
                action = MediaService.ACTION_NEXT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        private val pendingIntentPause = PendingIntent.getService(
            BaseApplication.context,
            3,
            Intent(BaseApplication.context, MediaService::class.java).apply {
                action = MediaService.ACTION_PLAY_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        private val pendingIntentVolumeUp = PendingIntent.getService(
            BaseApplication.context,
            4,
            Intent(BaseApplication.context, MediaService::class.java).apply {
                action = MediaService.ACTION_VOLUME_UP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        private val pendingIntentVolumeDown = PendingIntent.getService(
            BaseApplication.context,
            5,
            Intent(BaseApplication.context, MediaService::class.java).apply {
                action = MediaService.ACTION_VOLUME_DOWN
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        private val pendingIntentActivity = PendingIntent.getService(
            BaseApplication.context,
            6,
            Intent(BaseApplication.context, MainActivity::class.java).apply {
                action = MediaService.ACTION_MAIN
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onUpdate(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetIds: IntArray?
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        //updateRemoteViews()
        //return
        if (appWidgetIds == null || context == null) {
            return
        }
        for (appWidgetId in appWidgetIds) {
            val remoteViews = RemoteViews(context.packageName, R.layout.pod_shuffle)

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