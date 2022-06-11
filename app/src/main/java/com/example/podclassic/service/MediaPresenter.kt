package com.example.podclassic.service

import android.content.ComponentName
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.bean.Music
import com.example.podclassic.media.PlayMode
import com.example.podclassic.media.PlayState
import com.example.podclassic.media.RepeatMode
import com.example.podclassic.util.LiveData


object MediaPresenter {
    val music = LiveData<Music?>(null)
    val playState = LiveData<PlayState>(null)
    val playMode = LiveData(PlayMode.ORDER)
    val repeatMode = LiveData(RepeatMode.ALL)
    val stopTime = LiveData(0)
    val playList = LiveData<ArrayList<Music>>(null)


    private const val STATE_UNCONNECTED = 0
    private const val STATE_CONNECTING = 1
    private const val STATE_CONNECTED = 2

    private var connectionState = STATE_UNCONNECTED

    private var mediaController: MediaService.MediaController? = null
        get() {
            if (field == null && connectionState == STATE_UNCONNECTED) {
                connect()
            }
            return field
        }

    fun init() {

        //connectService()
    }

    private fun connect() {
        if (connectionState != STATE_UNCONNECTED) {
            return
        }
        connectionState = STATE_CONNECTING

        val context = BaseApplication.context
        val intent = Intent(context, MediaService::class.java)

        context.bindService(intent, object : ServiceConnection {
            override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
                (p1 as MediaService.ServiceBinder)
                mediaController = p1.mediaController
                connectionState = STATE_CONNECTED
            }

            override fun onServiceDisconnected(p0: ComponentName?) {
                mediaController = null
                if (connectionState == STATE_CONNECTED) {
                    connectionState = STATE_UNCONNECTED
                }
            }

        }, BIND_AUTO_CREATE)
    }


    init {
        connect()
    }

    private fun sendMessage(action: String, arg1: Any? = null, arg2: Any? = null) {
        mediaController?.sendMessage(action, arg1, arg2)
    }

    fun next() {
        sendMessage(MediaService.ACTION_NEXT)
    }

    fun prev() {
        sendMessage(MediaService.ACTION_PREV)
    }

    fun playPause() {
        sendMessage(MediaService.ACTION_PLAY_PAUSE)
    }

    fun play() {
        sendMessage(MediaService.ACTION_PLAY)
    }

    fun pause() {
        sendMessage(MediaService.ACTION_PAUSE)
    }

    fun seekTo(position: Int) {
        sendMessage(MediaService.ACTION_SEEK, position)
    }

    fun setPlayMode(playMode: PlayMode) {
        sendMessage(MediaService.ACTION_SET_PLAY_MODE, playMode)
    }

    fun nextPlayMode() {
        sendMessage(MediaService.ACTION_NEXT_PLAY_MODE)
    }

    fun setRepeatMode(repeatMode: RepeatMode) {
        sendMessage(MediaService.ACTION_SET_REPEAT_MODE, repeatMode)
    }

    fun shufflePlay(list: ArrayList<Music>) {
        sendMessage(MediaService.ACTION_SHUFFLE, list)
    }

    fun shufflePlay(list: ArrayList<Music>, index: Int) {
        sendMessage(MediaService.ACTION_SHUFFLE, list, index)
    }

    fun shufflePlay() {
        sendMessage(MediaService.ACTION_SHUFFLE)
    }

    fun setEqualizer(index: Int) {
        sendMessage(MediaService.ACTION_SET_EQUALIZER, index)
    }

    fun stop() {
        sendMessage(MediaService.ACTION_STOP)
    }

    fun add(e: Music) {
        sendMessage(MediaService.ACTION_ADD_MUSIC, e)
    }

    fun set(e: Music) {
        sendMessage(MediaService.ACTION_SET_MUSIC, e)
    }

    fun remove(e: Music) {
        sendMessage(MediaService.ACTION_REMOVE_MUSIC, e)
    }

    fun removeAt(index: Int) {
        sendMessage(MediaService.ACTION_REMOVE_MUSIC_AT, index)
    }

    fun setIndex(index: Int) {
        sendMessage(MediaService.ACTION_SET_INDEX, index)
    }

    fun setPlaylist(list: ArrayList<Music>) {
        sendMessage(MediaService.ACTION_SET_PLAY_LIST, list)
    }

    fun setPlaylist(list: ArrayList<Music>, index: Int) {
        sendMessage(MediaService.ACTION_SET_PLAY_LIST, list, index)
    }

    fun setStopTime(time: Int) {
        sendMessage(MediaService.ACTION_SET_STOP_TIME, time)
    }


    fun getIndex(): Int {
        return mediaController?.getIndex() ?: -1
    }

    fun getCurrent(): Music? {
        return mediaController?.getCurrent()
    }

    fun forward(): Boolean {
        return mediaController?.forward() ?: false
    }

    fun backward(): Boolean {
        return mediaController?.backward() ?: false
    }

    fun getDuration(): Int {
        mediaController?.getDuration().apply {
            return if (this == null || this == 0) {
                1
            } else {
                this
            }
        }
    }

    fun getProgress(): Int {
        return mediaController?.getProgress() ?: 0
    }

    fun getPlayMode(): PlayMode {
        return mediaController?.getPlayMode() ?: PlayMode.ORDER
    }

    fun getRepeatMode(): RepeatMode {
        return mediaController?.getRepeatMode() ?: RepeatMode.ALL
    }

    fun nextRepeatMode() {
        sendMessage(MediaService.ACTION_NEXT_REPEAT_MODE)

    }

    fun getStopTime(): Int {
        return mediaController?.getStopTime() ?: 0
    }

    fun isPlaying(): Boolean {
        return mediaController?.isPlaying() ?: false
    }

    fun isPrepared(): Boolean {
        return mediaController?.isPrepared() ?: false
    }

    fun getPresetList(): Array<String?> {
        return mediaController?.getPresetList() ?: arrayOf()
    }

    fun getPlaylist(): ArrayList<Music> {
        return mediaController?.getPlaylist() ?: arrayListOf()
    }

    fun setAudioFocus() {
        sendMessage(MediaService.ACTION_UPDATE_AUDIO_FOCUS)
    }
}