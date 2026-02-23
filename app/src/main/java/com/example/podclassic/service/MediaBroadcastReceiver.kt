package com.example.podclassic.service

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent

class MediaBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        intent ?: return
        context ?: return

        when (intent.action) {
            Intent.ACTION_MEDIA_BUTTON -> {
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
            }
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                // 耳机拔出时暂停播放
                MediaPresenter.pause()
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                // 蓝牙设备断开时暂停播放
                MediaPresenter.pause()
            }
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                // 蓝牙设备连接时不做处理，继续播放
            }
        }
    }
}