package com.example.podclassic.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

class AudioFocusManager(
    context: Context,
    private val onAudioFocusChangeListener: OnAudioFocusChangeListener?
) : AudioManager.OnAudioFocusChangeListener {
    val audioAttributes: AudioAttributes = AudioAttributes
        .Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()


    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val audioFocusRequest =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setOnAudioFocusChangeListener(this)
            .setAudioAttributes(audioAttributes)
            .build()
        else null

    fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
        } else {
            audioManager.abandonAudioFocus(this)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        onAudioFocusChangeListener ?: return
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                onAudioFocusChangeListener.onAudioFocusGain()
            }
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                onAudioFocusChangeListener.onAudioFocusLoss()
            }
        }
    }

    interface OnAudioFocusChangeListener {
        fun onAudioFocusGain()
        fun onAudioFocusLoss()
    }
}