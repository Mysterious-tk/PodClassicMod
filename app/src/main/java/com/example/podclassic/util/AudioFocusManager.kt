package com.example.podclassic.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.storage.SPManager

class AudioFocusManager(var onAudioFocusChangeListener : OnAudioFocusChangeListener?) : AudioManager.OnAudioFocusChangeListener {
    private val audioManager by lazy { BaseApplication.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val attribute = AudioAttributes
        .Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()!!

    private val audioFocusRequest : AudioFocusRequest? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setOnAudioFocusChangeListener(this)
            .setAudioAttributes(attribute)
            .build() else null

    @Suppress("DEPRECATION")
    fun requestAudioFocus() {
        if (!SPManager.getBoolean(SPManager.SP_AUDIO_FOCUS)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    @Suppress("DEPRECATION")
    fun abandonAudioFocus() {
        if (!SPManager.getBoolean(SPManager.SP_AUDIO_FOCUS)) { return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
        } else {
            audioManager.abandonAudioFocus(this)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                onAudioFocusChangeListener?.onAudioFocusGain()
            }
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                onAudioFocusChangeListener?.onAudioFocusLoss()
            }
        }
    }

    interface OnAudioFocusChangeListener {
        fun onAudioFocusGain()
        fun onAudioFocusLoss()
    }
}