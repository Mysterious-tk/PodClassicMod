package com.example.podclassic.view

import android.content.Context
import android.media.MediaPlayer
import android.text.TextUtils
import android.view.View
import android.widget.RelativeLayout
import com.example.podclassic.R
import com.example.podclassic.`object`.Music
import com.example.podclassic.base.ScreenView
import com.example.podclassic.util.AudioFocusManager
import com.example.podclassic.util.MediaUtil
import com.example.podclassic.util.ThreadUtil
import com.example.podclassic.util.Values.DEFAULT_PADDING
import com.example.podclassic.widget.ListView
import com.example.podclassic.widget.SeekBar
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min
import kotlin.random.Random

class MusicQuizView(context: Context) : RelativeLayout(context), ScreenView, MediaPlayer.OnPreparedListener, AudioFocusManager.OnAudioFocusChangeListener {
    private val itemList = ItemListView(context, ArrayList(), "", null, MAX_SIZE)
    private val timerView = SeekBar(context)

    companion object {
        const val MAX_TIME = 10
        const val MAX_SIZE = 5
    }

    private val musics = MediaUtil.musics.clone() as ArrayList<Music>

    private val audioFocusManager = AudioFocusManager(this)


    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            audioFocusManager.requestAudioFocus()
        } else {
            pause()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        audioFocusManager.requestAudioFocus()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pause()
        mediaPlayer.release()
    }

    private var timer : Timer? = null

    private var timerCount = 0
    private var total = 0
    private var count = 0
    private var pack : Package? = null
    private val mediaPlayer = MediaPlayer()
    private var started = false

    init {
        timerView.apply {
            textVisibility = View.INVISIBLE
            setMax(MAX_TIME)
            id = R.id.timer_view
        }
        addView(itemList, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply { addRule(ABOVE, timerView.id) })
        addView(timerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            .apply {
                setMargins(DEFAULT_PADDING,DEFAULT_PADDING,DEFAULT_PADDING,DEFAULT_PADDING)
                addRule(ALIGN_PARENT_BOTTOM)
            })
        mediaPlayer.setOnPreparedListener(this)
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
            itemList.getItem(i).name = selectedMusic[i].name
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
            timerView.setLeftText("分数 : " + count * 1000 + " (" + count + "/" + total + ")")
        }

        try {
            mediaPlayer.apply {
                reset()
                setDataSource(pack?.selectedMusic?.path)
                prepareAsync()
            }
        } catch (ignore : Exception) {
            mediaPlayer.apply {
                reset()
                setDataSource(pack?.selectedMusic?.path)
                prepareAsync()
            }
        }

        timer?.schedule(object : TimerTask() {
            override fun run() {
                timerCount ++
                ThreadUtil.runOnUiThread(Runnable {
                    timerView.setCurrent(timerCount)
                })
                if (timerCount == MAX_TIME) {
                    timer?.cancel()
                    total ++
                    mediaPlayer.stop()
                    ThreadUtil.runOnUiThread(Runnable {
                        timerView.setLeftText("不正确")
                    })
                    Thread.sleep(1000)
                    ThreadUtil.runOnUiThread(Runnable {
                        startGame()
                    })
                } else if (timerCount == 1) {
                    ThreadUtil.runOnUiThread(Runnable {
                        timerView.setLeftText("分数 : " + count * 1000 + " (" + count + "/" + total + ")")
                    })
                }
            }

        }, 1000, 1000L)
        started = true
    }

    inner class Package(val selectedMusic : Music) {

        fun onSelect(music : Music) {
            total ++
            if (music == selectedMusic) {
                this@MusicQuizView.timerView.setLeftText("正确！")
                count ++
            } else {
                this@MusicQuizView.timerView.setLeftText("不正确")
            }
            timer?.cancel()
            mediaPlayer.stop()
            startGame()
        }

    }


    private fun selectMusics() : ArrayList<Music> {
        musics.shuffle()
        val list = ArrayList<Music>(MAX_SIZE)
        for (i in 0 until min(MAX_SIZE, musics.size)) {
            list.add(musics[i])
        }
        return list
    }

    override fun enter() : Boolean {
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

    override fun enterLongClick() : Boolean { return false }

    override fun slide(slideVal: Int): Boolean {
        return itemList.slide(slideVal)
    }

    override fun getTitle(): String {
        return "Music Quiz"
    }

    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_NORMAL
    }

    override fun onPrepared(mp: MediaPlayer?) {
        mediaPlayer.seekTo(mediaPlayer.duration / 5)
        mediaPlayer.start()
    }

    override fun onAudioFocusGain() {}

    override fun onAudioFocusLoss() {
        pause()
    }
}