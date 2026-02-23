package com.example.podclassic.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

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
            .setWillPauseWhenDucked(false)
            .build()
        else null

    fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
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
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        onAudioFocusChangeListener ?: return
        Log.d("AudioFocusManager", "onAudioFocusChange: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                onAudioFocusChangeListener.onAudioFocusGain()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                onAudioFocusChangeListener.onAudioFocusLoss(permanent = true, pausedByDuck = false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                onAudioFocusChangeListener.onAudioFocusLoss(permanent = false, pausedByDuck = false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                onAudioFocusChangeListener.onAudioFocusLoss(permanent = false, pausedByDuck = true)
            }
        }
    }

    interface OnAudioFocusChangeListener {
        fun onAudioFocusGain()
        fun onAudioFocusLoss(permanent: Boolean, pausedByDuck: Boolean = false)
    }
}