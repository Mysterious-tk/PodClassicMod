package com.example.podclassic.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.example.podclassic.`object`.Core
import com.example.podclassic.`object`.MediaPlayer
import com.example.podclassic.util.MediaUtil

class MediaBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) {
            return
        }
        if (Intent.ACTION_MEDIA_BUTTON == intent.action) {
            val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
            if (keyEvent.action != KeyEvent.ACTION_UP) {
                return
            }
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> MediaPlayer.pause()
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    if (MediaPlayer.getPlayListSize() == 0) {
                        MediaPlayer.shufflePlay(MediaUtil.musics)
                    } else {
                        MediaPlayer.next()
                    }
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    if (MediaPlayer.getPlayListSize() == 0) {
                        MediaPlayer.shufflePlay(MediaUtil.musics)
                    } else {
                        MediaPlayer.prev()
                    }
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> MediaPlayer.forward()
                KeyEvent.KEYCODE_MEDIA_REWIND -> MediaPlayer.backward()
                KeyEvent.KEYCODE_MEDIA_CLOSE -> Core.exit()
            }
        } else {
            if (MediaPlayer.isPlaying) {
                MediaPlayer.pause()
            }
        }
    }
}