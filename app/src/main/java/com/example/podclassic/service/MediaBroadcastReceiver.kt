package com.example.podclassic.service

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent

/**
 * 媒体广播接收器
 * 
 * 处理以下事件：
 * 1. 媒体按钮事件（耳机线控、蓝牙设备控制）
 * 2. 耳机拔出事件
 * 3. 蓝牙设备连接/断开事件
 * 4. 系统媒体控制事件
 */
class MediaBroadcastReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "MediaBroadcastReceiver"
        
        // 双击/三击检测的时间阈值（毫秒）
        private const val MULTI_CLICK_INTERVAL = 500L
    }
    
    // 用于检测双击/三击的变量
    private var lastClickTime: Long = 0
    private var clickCount: Int = 0

    override fun onReceive(context: Context?, intent: Intent?) {
        intent ?: return
        context ?: return

        when (intent.action) {
            Intent.ACTION_MEDIA_BUTTON -> handleMediaButton(intent)
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> handleAudioBecomingNoisy()
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> handleBluetoothDisconnected()
            BluetoothDevice.ACTION_ACL_CONNECTED -> handleBluetoothConnected()
        }
    }
    
    /**
     * 处理媒体按钮事件
     * 支持：播放/暂停、上一曲、下一曲、快进、快退
     */
    private fun handleMediaButton(intent: Intent) {
        val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
        
        // 只处理按键抬起事件，避免重复触发
        if (keyEvent.action != KeyEvent.ACTION_UP) {
            return
        }
        
        Log.d(TAG, "Media button pressed: keyCode=${keyEvent.keyCode}")
        
        when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                // 检测双击/三击
                handlePlayPauseClick()
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                MediaPresenter.play()
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                MediaPresenter.pause()
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                MediaPresenter.stop()
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
    
    /**
     * 处理播放/暂停按钮点击
     * 支持双击（下一曲）和三击（上一曲）
     */
    private fun handlePlayPauseClick() {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastClickTime <= MULTI_CLICK_INTERVAL) {
            clickCount++
        } else {
            clickCount = 1
        }
        lastClickTime = currentTime
        
        // 延迟处理，以检测是否为双击或三击
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            when (clickCount) {
                1 -> MediaPresenter.playPause()          // 单击：播放/暂停
                2 -> MediaPresenter.next()               // 双击：下一曲
                3 -> MediaPresenter.prev()               // 三击：上一曲
            }
            clickCount = 0
        }, MULTI_CLICK_INTERVAL)
    }
    
    /**
     * 处理耳机拔出事件
     * 自动暂停播放
     */
    private fun handleAudioBecomingNoisy() {
        Log.d(TAG, "Audio becoming noisy, pausing playback")
        // 耳机拔出时暂停播放
        if (MediaPresenter.isPlaying()) {
            MediaPresenter.pause()
        }
    }
    
    /**
     * 处理蓝牙设备断开事件
     * 自动暂停播放
     */
    private fun handleBluetoothDisconnected() {
        Log.d(TAG, "Bluetooth device disconnected, pausing playback")
        // 蓝牙设备断开时暂停播放
        if (MediaPresenter.isPlaying()) {
            MediaPresenter.pause()
        }
    }
    
    /**
     * 处理蓝牙设备连接事件
     * 可以继续播放或保持当前状态
     */
    private fun handleBluetoothConnected() {
        Log.d(TAG, "Bluetooth device connected")
        // 蓝牙设备连接时不自动播放，保持当前状态
        // 如果需要自动播放，可以取消下面的注释
        // if (!MediaPresenter.isPlaying()) {
        //     MediaPresenter.play()
        // }
    }
}