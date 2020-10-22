package com.example.podclassic.view

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.example.podclassic.`object`.Core
import com.example.podclassic.`object`.MediaPlayer
import com.example.podclassic.base.ScreenView
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.MediaUtil
import com.example.podclassic.util.Values

class SettingsView(context: Context) : ListView(context), ScreenView {

    companion object { const val TITLE = "设置" }
    override fun enter() : Boolean { return onItemClick() }
    override fun enterLongClick() : Boolean { return false }
    override fun slide(slideVal: Int): Boolean { return onSlide(slideVal) }
    override fun getTitle(): String { return TITLE }

    init {
        itemList = arrayListOf(
            Item("关于本机", object : OnItemClickListener {
                @SuppressLint("HardwareIds")
                override fun onItemClick(index : Int, listView : ListView) : Boolean {
                    val statFs = StatFs(Environment.getDataDirectory().path)
                    val blockSizeLong = statFs.blockSizeLong
                    Core.addView(ItemListView(context, arrayListOf(
                        Item(Values.IPOD, null, false),
                        Item("歌曲", null, MediaUtil.musics.size.toString()),
                        Item("视频", null, MediaUtil.getVideoSize().toString()),
                        Item("照片", null, MediaUtil.getPhotoSize().toString()),
                        Item("容量", null, (blockSizeLong * statFs.blockCountLong / 1024 / 1024 / 1024).toString() + " GB"),
                        Item("可用容量", null, (blockSizeLong * statFs.availableBlocksLong / 1024 / 1024 / 1024).toString() + " GB"),
                        Item("版本", null, Values.getVersionName()),
                        Item("S/N", null, android.os.Build.SERIAL),
                        Item("型号", null, "MA446ZP"),
                        Item("格式", null, "Windows")
                    ), "关于本机", null))
                    return true
                }
            }, true),
            Item("睡眠定时", object : OnItemClickListener {
                fun scheduleToStop(min : Int) {
                    MediaPlayer.scheduleToStop(min)
                    Core.removeView()
                }
                val itemList = arrayListOf(
                    Item("关闭", object : OnItemClickListener { override fun onItemClick(index: Int, listView: ListView) : Boolean {scheduleToStop(0); return true } }, false),
                    //Item("1 分钟", object : OnItemClickListener { override fun onItemClick(index: Int, listView: ListView) : Boolean {scheduleToStop(1); return true } }, false),
                    Item("15 分钟", object : OnItemClickListener { override fun onItemClick(index: Int, listView: ListView) : Boolean {scheduleToStop(15); return true } }, false),
                    Item("30 分钟", object : OnItemClickListener { override fun onItemClick(index: Int, listView: ListView) : Boolean {scheduleToStop(30); return true } }, false),
                    Item("60 分钟", object : OnItemClickListener { override fun onItemClick(index: Int, listView: ListView) : Boolean {scheduleToStop(60); return true } }, false),
                    Item("90 分钟", object : OnItemClickListener { override fun onItemClick(index: Int, listView: ListView) : Boolean {scheduleToStop(90); return true } }, false),
                    Item("120 分钟", object : OnItemClickListener { override fun onItemClick(index: Int, listView: ListView) : Boolean {scheduleToStop(120); return true } }, false)
                )
                override fun onItemClick(index : Int, listView : ListView) : Boolean { Core.addView(ItemListView(context, itemList, "睡眠", null)); return true }
            }, true),

            Item("均衡器", object : OnItemClickListener {
                override fun onItemClick(index : Int, listView: ListView) : Boolean {
                    val nameList = MediaPlayer.equalizerList
                    val itemList = ArrayList<Item>()
                    val equalizerIndex = SPManager.getInt(SPManager.SP_EQUALIZER)
                    for (i in nameList.indices) {
                        itemList.add(if (i == equalizerIndex) Item(nameList[i], null, "当前") else Item(nameList[i], null, false))
                    }
                    Core.addView(ItemListView(context, itemList, "均衡器", object : OnItemClickListener {
                        override fun onItemClick(index: Int, listView: ListView): Boolean {
                            val result = MediaPlayer.setEqualizer(index)
                            Core.removeView()
                            return result
                        }
                    }))
                    return true
                }

            }, true),

            Item("播放顺序", object : OnItemClickListener {
                override fun onItemClick(index : Int, listView : ListView) : Boolean {
                    MediaPlayer.setPlayMode()
                    listView.getCurrentItem().rightText = MediaPlayer.getPlayModeString()
                    return true
                }
            }, MediaPlayer.getPlayModeString()),

            Item("随机播放", object : OnItemClickListener {
                override fun onItemClick(index : Int, listView : ListView) : Boolean {
                    val playAll = SPManager.getBoolean(SPManager.SP_PLAY_ALL)
                    SPManager.setBoolean(SPManager.SP_PLAY_ALL, !playAll)
                    listView.getCurrentItem().rightText = if (playAll) "收藏歌曲" else "全部歌曲"
                    return true
                }
            }, if (SPManager.getBoolean(SPManager.SP_PLAY_ALL))  "全部歌曲" else "收藏歌曲" ),

            Item("夜间模式", object : OnItemClickListener {
                override fun onItemClick(index : Int, listView : ListView) : Boolean {
                    var nightMode = SPManager.getInt(SPManager.NightMode.SP_NAME)
                    nightMode ++
                    nightMode %= SPManager.NightMode.values
                    Core.setNightMode(SPManager.NightMode.nightMode(nightMode))
                    SPManager.setInt(SPManager.NightMode.SP_NAME, nightMode)
                    listView.getCurrentItem().rightText = SPManager.NightMode.getString(nightMode)
                    return true
                }

            }, SPManager.NightMode.getString(SPManager.getInt(SPManager.NightMode.SP_NAME))),

            Item("显示歌词", object : OnItemClickListener {
                override fun onItemClick(index : Int, listView : ListView) : Boolean {
                    val showLyric = SPManager.getBoolean(SPManager.SP_SHOW_LYRIC)
                    SPManager.setBoolean(SPManager.SP_SHOW_LYRIC, !showLyric)
                    listView.getCurrentItem().rightText = if (showLyric) "关闭" else "打开"
                    return true
                }

            }, if (SPManager.getBoolean(SPManager.SP_SHOW_LYRIC))  "打开" else "关闭" ),

            Item("与其它应用同时播放", object : OnItemClickListener {
                override fun onItemClick(index : Int, listView : ListView) : Boolean {
                    val audioFocus = SPManager.getBoolean(SPManager.SP_AUDIO_FOCUS)
                    SPManager.setBoolean(SPManager.SP_AUDIO_FOCUS, !audioFocus)
                    listView.getCurrentItem().rightText = if (audioFocus) "打开" else "关闭"
                    return true
                }
            }, if (SPManager.getBoolean(SPManager.SP_AUDIO_FOCUS))  "关闭" else "打开" ),


            Item("按键音", object : OnItemClickListener {
                override fun onItemClick(index : Int, listView: ListView) : Boolean {
                    var sound = SPManager.getInt(SPManager.Sound.SP_NAME)
                    sound ++
                    sound %= SPManager.Sound.values
                    SPManager.setInt(SPManager.Sound.SP_NAME, sound)
                    listView.getCurrentItem().rightText = SPManager.Sound.getString(sound)
                    return true
                }
            }, SPManager.Sound.getString(SPManager.getInt(SPManager.Sound.SP_NAME))),

            Item("主题", object : OnItemClickListener {
                override fun onItemClick(index : Int, listView: ListView) : Boolean {
                    val theme = SPManager.getBoolean(SPManager.SP_THEME)
                    SPManager.setBoolean(SPManager.SP_THEME, !theme)
                    Core.refresh()
                    listView.getCurrentItem().rightText = if (theme) "黑色" else "红色"
                    return true
                }
            }, if (SPManager.getBoolean(SPManager.SP_THEME))  "红色" else "黑色" ),

            Item("显示时间", object : OnItemClickListener {
                override fun onItemClick(index : Int, listView: ListView) : Boolean {
                    val showTime = SPManager.getBoolean(SPManager.SP_SHOW_TIME)
                    SPManager.setBoolean(SPManager.SP_SHOW_TIME, !showTime)
                    listView.getCurrentItem().rightText = if (showTime) "关闭" else "打开"
                    return true
                }
            }, if (SPManager.getBoolean(SPManager.SP_SHOW_TIME))  "打开" else "关闭" ),

            Item("启动后自动停止播放", object : OnItemClickListener {
                override fun onItemClick(index : Int, listView: ListView) : Boolean {
                    var autoStop = SPManager.getInt(SPManager.AutoStop.SP_NAME)
                    autoStop ++
                    autoStop %= SPManager.AutoStop.values
                    SPManager.setInt(SPManager.AutoStop.SP_NAME, autoStop)
                    listView.getCurrentItem().rightText = SPManager.AutoStop.getString(autoStop)
                    return true
                }
            }, SPManager.AutoStop.getString(SPManager.getInt(SPManager.AutoStop.SP_NAME))),

            Item("Reset All Settings", object : OnItemClickListener {
                override fun onItemClick(index : Int, listView: ListView) : Boolean {
                    Core.addView(ItemListView(context, arrayListOf(
                        Item("Cancel", object : OnItemClickListener { override fun onItemClick(index: Int, listView: ListView) : Boolean { Core.removeView(); return true } }, false),
                        Item("Reset", object : OnItemClickListener { override fun onItemClick(index: Int, listView: ListView) : Boolean { SPManager.reset();Core.removeView(); return true } }, false)
                    ), "Reset All", null))
                    return true
                }
            }, true)
            )
    }

    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_NORMAL
    }

}