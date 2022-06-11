package com.example.podclassic.view

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaPlayer
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import com.example.podclassic.base.ScreenView
import com.example.podclassic.bean.Music
import com.example.podclassic.media.AudioFocusManager
import com.example.podclassic.service.MediaPresenter
import com.example.podclassic.util.MediaStoreUtil
import com.example.podclassic.util.ThreadUtil
import com.example.podclassic.values.Strings
import com.example.podclassic.values.Values.DEFAULT_PADDING
import com.example.podclassic.widget.ListView
import com.example.podclassic.widget.SeekBar
import com.example.podclassic.widget.TextView
import java.util.*
import kotlin.math.min
import kotlin.random.Random

class MusicQuizView(context: Context) : LinearLayout(context), ScreenView,
    MediaPlayer.OnPreparedListener {
    private val itemList = ItemListView(context, ArrayList(), "", null, MAX_SIZE)
    private val timerView = SeekBar(context)
    private val textView = TextView(context)

    companion object {
        const val MAX_TIME = 10
        const val MAX_SIZE = 5
    }

    private val musics = MediaStoreUtil.getMusicList()

    private val audioFocusManager =
        AudioFocusManager(context, object : AudioFocusManager.OnAudioFocusChangeListener {
            override fun onAudioFocusGain() {}

            override fun onAudioFocusLoss() {
                pause()
            }
        })


    override fun onViewRemove() {
        pause()
    }

    private var timer: Timer? = null

    private var timerCount = 0
    private var total = 0
    private var count = 0
    private var pack: Package? = null
    private val mediaPlayer = MediaPlayer()
    private var started = false

    init {
        timerView.apply {
            textVisibility = View.INVISIBLE
            setMax(MAX_TIME)
        }
        orientation = VERTICAL

        addView(itemList, LayoutParams(LayoutParams.MATCH_PARENT, 0, 7f))
        addView(timerView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
            setMargins(DEFAULT_PADDING, DEFAULT_PADDING, DEFAULT_PADDING, 0)
        })
        addView(textView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.5f))
        mediaPlayer.setOnPreparedListener(this)
    }

    @SuppressLint("SetTextI18n")
    private fun setScore() {
        textView.text = "${Strings.SCORE} : ${count * 1000} ($count/$total)"
    }

    private fun setIncorrect() {
        textView.text = Strings.INCORRECT
    }

    private fun setCorrect() {
        textView.text = Strings.CORRECT
    }

    fun startGame() {
        if (musics.isEmpty()) {
            return
        }
        timerCount = 0
        timer?.cancel()
        timer = Timer()
        val selectedMusic = selectMusics()
        while (selectedMusic.size > itemList.size()) {
            itemList.add(ListView.Item("", null, false))
        }
        for (i in selectedMusic.indices) {
            itemList.getItem(i).name = selectedMusic[i].title
        }
        itemList.defaultOnItemClickListener = object : ListView.OnItemClickListener {
            override fun onItemClick(index: Int, listView: ListView): Boolean {
                return if (pack == null || index >= selectedMusic.size) {
                    false
                } else {
                    pack?.onSelect(selectedMusic[index])
                    true
                }
            }
        }
        itemList.refreshList()

        pack = Package(selectedMusic[Random.nextInt(selectedMusic.size)])
        if (TextUtils.isEmpty(timerView.getLeftText())) {
            setScore()
        }

        try {
            mediaPlayer.apply {
                reset()
                setDataSource(pack?.selectedMusic?.data)
                prepareAsync()
            }
        } catch (ignore: Exception) {
            mediaPlayer.apply {
                reset()
                setDataSource(pack?.selectedMusic?.data)
                prepareAsync()
            }
        }

        timer?.schedule(object : TimerTask() {
            override fun run() {
                timerCount++
                ThreadUtil.runOnUiThread {
                    timerView.setCurrent(timerCount)
                }
                if (timerCount == MAX_TIME) {
                    timer?.cancel()
                    total++
                    mediaPlayer.stop()
                    ThreadUtil.runOnUiThread {
                        setIncorrect()
                    }
                    Thread.sleep(1000)
                    ThreadUtil.runOnUiThread {
                        startGame()
                    }
                } else if (timerCount == 1) {
                    ThreadUtil.runOnUiThread {
                        setScore()
                    }
                }
            }

        }, 1000, 1000L)
        started = true
    }

    inner class Package(val selectedMusic: Music) {

        fun onSelect(music: Music) {
            total++
            if (music == selectedMusic) {
                ThreadUtil.runOnUiThread {
                    setCorrect()
                }
                count++
            } else {
                ThreadUtil.runOnUiThread {
                    setIncorrect()
                }
            }
            timer?.cancel()
            mediaPlayer.stop()
            startGame()
        }

    }


    private fun selectMusics(): ArrayList<Music> {
        musics.shuffle()
        val list = ArrayList<Music>(MAX_SIZE)
        for (i in 0 until min(MAX_SIZE, musics.size)) {
            list.add(musics[i])
        }
        return list
    }

    override fun enter(): Boolean {
        if (started) {
            return itemList.onItemClick()
        } else {
            startGame()
        }
        return true
    }

    private fun pause() {
        audioFocusManager.abandonAudioFocus()
        timerView.setCurrent(0)
        timer?.cancel()
        mediaPlayer.stop()
        started = false
    }

    override fun enterLongClick(): Boolean {
        return false
    }

    override fun slide(slideVal: Int): Boolean {
        return itemList.slide(slideVal)
    }

    override fun getTitle(): String {
        return Strings.MUSIC_QUIZ
    }


    override fun onViewCreate() {
        audioFocusManager.requestAudioFocus()
    }

    override fun onViewDelete() {
        pause()
        mediaPlayer.release()
    }

    private fun requestAudioFocus() {
        MediaPresenter.pause()
        audioFocusManager.requestAudioFocus()
    }

    override fun onPrepared(mp: MediaPlayer?) {
        mediaPlayer.seekTo(mediaPlayer.duration / 5)
        mediaPlayer.start()
        requestAudioFocus()
    }
}