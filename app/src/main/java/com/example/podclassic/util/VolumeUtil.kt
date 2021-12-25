package com.example.podclassic.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.view.HapticFeedbackConstants
import android.view.View
import com.example.podclassic.R
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.storage.SPManager
import kotlin.math.max

object VolumeUtil {
    private val audioManager by lazy {
        BaseApplication.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    val maxVolume by lazy { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    private var streamId = 0
    private val soundPool = SoundPool.Builder().apply {
        setMaxStreams(4)
        /*
        setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build())
        */
        build()
    }.build().apply {
        streamId = load(BaseApplication.context, R.raw.click, 0)
    }

    fun getCurrentVolume(): Int {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    fun setCurrentVolume(currentVolume: Int) {
        var curVolume = currentVolume
        if (curVolume < 0) {
            curVolume = 0
        }
        if (curVolume > maxVolume) {
            curVolume = maxVolume
        }

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, curVolume, 0)
    }

    fun volumeUp() {
        setCurrentVolume(getCurrentVolume() + 1)
    }

    fun volumeDown() {
        setCurrentVolume(getCurrentVolume() - 1)
    }

    fun vibrate(view : View) {
        val sound = SPManager.getInt(SPManager.Sound.SP_NAME)
        if ((sound and SPManager.Sound.SOUND_ID) != 0) {
            soundPool.play(streamId, 1f, 1f, 0, 0, 1f)
        }
        if (sound and SPManager.Sound.VIBRATE_ID != 0) {
            ThreadUtil.newThread {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
            }
        }
    }

    fun releaseSoundPool() {
        soundPool.release()
    }
}