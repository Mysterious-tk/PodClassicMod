package com.example.podclassic.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent

class MediaBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        intent ?: return
        context ?: return

        if (Intent.ACTION_MEDIA_BUTTON == intent.action) {
            val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
            if (keyEvent.action != KeyEvent.ACTION_UP) {
                return
            }
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                    MediaPresenter.playPause()
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    MediaPresenter.next()
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    MediaPresenter.prev()
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    MediaPresenter.forward()
                }
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    MediaPresenter.backward()
                }
                KeyEvent.KEYCODE_MEDIA_CLOSE -> {
                    MediaPresenter.stop()
                }
            }
        } else {
            MediaPresenter.pause()
        }
    }
}