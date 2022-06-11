package com.example.podclassic.view

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import com.example.podclassic.base.ScreenView
import com.example.podclassic.media.AudioFocusManager
import com.example.podclassic.service.MediaPresenter
import com.example.podclassic.values.Colors
import java.io.File

@SuppressLint("ViewConstructor")
class VideoView(context: Context, val file: File) : FrameLayout(context), ScreenView {

    private val audioFocusManager =
        AudioFocusManager(context, object : AudioFocusManager.OnAudioFocusChangeListener {
            override fun onAudioFocusGain() {}

            override fun onAudioFocusLoss() {
                if (videoView.isPlaying) {
                    videoView.pause()
                }
            }
        })

    private val videoView = VideoView(context)

    init {
        //this.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        addView(
            videoView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER })

        setBackgroundColor(Colors.text)

        requestAudioFocus()
        videoView.apply {
            setVideoPath(file.toString())
            setOnErrorListener { _, _, _ -> return@setOnErrorListener true }
            start()
        }
    }

    private fun requestAudioFocus() {
        MediaPresenter.pause()
        audioFocusManager.requestAudioFocus()
    }

    override fun onViewAdd() {
        requestAudioFocus()
    }

    override fun onViewRemove() {
        audioFocusManager.abandonAudioFocus()
        videoView.pause()
    }

    override fun enter(): Boolean {
        if (videoView.isPlaying) {
            videoView.pause()
            audioFocusManager.abandonAudioFocus()
        } else {
            requestAudioFocus()
            videoView.start()
        }
        return true
    }

    override fun enterLongClick(): Boolean {
        return false
    }

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

    override fun onViewDelete() {
        videoView.apply {
            stopPlayback()
            suspend()
            setOnErrorListener(null)
        }
        removeAllViews()
        audioFocusManager.abandonAudioFocus()
    }
}