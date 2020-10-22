package com.example.podclassic.util

import android.app.Service
import android.content.Context
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.example.podclassic.R
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.storage.SPManager

object VolumeUtil {
    private val audioManager by lazy {
        BaseApplication.getContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    val maxVolume by lazy { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    private val vibrator by lazy {
        BaseApplication.getContext().getSystemService(Service.VIBRATOR_SERVICE) as Vibrator
    }

    private var lastVibrateTime = 0L

    private var streamId = 0
    private val soundPool by lazy {
        val pool = SoundPool.Builder()
            .setMaxStreams(1)
            .build()
        streamId = pool.load(BaseApplication.getContext(), R.raw.click, 0)
        pool
    }

    private const val VIBRATE_TIME = 40L

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

    @Suppress("DEPRECATION")
    fun vibrate() {
        val sound = SPManager.getInt(SPManager.Sound.SP_NAME)
        if (sound and SPManager.Sound.SOUND_ID != 0) {
            soundPool.play(streamId, 1f, 1f, 0, 0, 1f)
        }
        if (sound and SPManager.Sound.VIBRATE_ID != 0) {
            val currentMillis = System.currentTimeMillis()
            if ((currentMillis - lastVibrateTime) < VIBRATE_TIME) {
                return
            } else {
                lastVibrateTime = currentMillis
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createOneShot(VIBRATE_TIME, 15)
                vibrator.vibrate(vibrationEffect)
            } else {
                vibrator.vibrate(VIBRATE_TIME)
            }
        }
    }

    fun releaseSoundPool() {
        soundPool.release()
    }
}