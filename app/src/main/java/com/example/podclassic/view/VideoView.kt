package com.example.podclassic.view

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import com.example.podclassic.base.ScreenView
import com.example.podclassic.util.AudioFocusManager
import com.example.podclassic.util.Colors
import java.io.File

@SuppressLint("ViewConstructor")
class VideoView(context: Context, val file : File) : FrameLayout(context), ScreenView, AudioFocusManager.OnAudioFocusChangeListener {

    private val audioFocusManager = AudioFocusManager(this)

    private val videoView = VideoView(context)

    init {
        this.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        layoutParams.gravity = Gravity.CENTER
        addView(videoView, layoutParams)

        setBackgroundColor(Colors.text)

        audioFocusManager.requestAudioFocus()
        videoView.setVideoPath(file.toString())
        videoView.setOnErrorListener { _, _, _ -> return@setOnErrorListener true }
        videoView.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        videoView.stopPlayback()
        videoView.suspend()
        videoView.setOnErrorListener(null)
        removeAllViews()
        audioFocusManager.abandonAudioFocus()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            audioFocusManager.requestAudioFocus()
        } else {
            videoView.pause()
            audioFocusManager.abandonAudioFocus()
        }
    }

    override fun enter() : Boolean {
        if (videoView.isPlaying) {
            videoView.pause()
            audioFocusManager.abandonAudioFocus()
        } else {
            videoView.start()
            audioFocusManager.requestAudioFocus()
        }
        return true
    }

    override fun enterLongClick() : Boolean { return false }

    override fun slide(slideVal: Int): Boolean {
        val sv = slideVal * 10 * 1000
        var progress = videoView.currentPosition
        val duration = videoView.duration
        if ((progress <= 0 && sv < 0) || (progress >= duration && sv > 0)) {
            return false
        }
        progress += sv
        if (progress < 0) {
            progress = 0
        }
        if (progress > duration) {
            progress = duration
        }
        videoView.seekTo(progress)
        return true
    }

    override fun getTitle(): String {
        return file.name
    }

    override fun onAudioFocusGain() {}

    override fun onAudioFocusLoss() {
        if (videoView.isPlaying) {
            videoView.pause()
        }
    }

    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_NORMAL
    }
}