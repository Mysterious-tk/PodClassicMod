package com.example.podclassic.view

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Base64
import com.example.podclassic.base.Core
import com.example.podclassic.base.ScreenView
import com.example.podclassic.media.PlayMode
import com.example.podclassic.media.RepeatMode
import com.example.podclassic.service.MediaPresenter
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.MediaStoreUtil
import com.example.podclassic.values.Strings
import com.example.podclassic.values.Values
import com.example.podclassic.widget.ListView
import com.example.podclassic.widget.OnSwitchListener
import com.example.podclassic.widget.SwitchBar
import java.util.*

class SettingsView(context: Context) : ListView(context), ScreenView {
    companion object {
        const val about = "感谢您的使用!\n这里本应有个彩蛋, 但是没啥想法, 就这样吧.\n"
    }

    override fun enter(): Boolean {
        return onItemClick()
    }

    override fun enterLongClick(): Boolean {
        return false
    }

    override fun slide(slideVal: Int): Boolean {
        return onSlide(slideVal)
    }

    override fun getTitle(): String {
        return Strings.SETTINGS
    }

    init {
        itemList = arrayListOf(
            Item(Strings.ABOUT, object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    val statFs = StatFs(Environment.getDataDirectory().path)
                    val blockSizeLong = statFs.blockSizeLong
                    Core.addView(
                        ItemListView(
                            context, arrayListOf(
                                Item(Strings.iPod, null, false),
                                Item(Strings.SONG, null, MediaStoreUtil.getMusicSize().toString()),
                                Item(Strings.VIDEO, null, MediaStoreUtil.getVideoSize().toString()),
                                Item(Strings.PHOTO, null, MediaStoreUtil.getPhotoSize().toString()),
                                Item(
                                    Strings.CAPACITY,
                                    null,
                                    (blockSizeLong * statFs.blockCountLong / 1024 / 1024 / 1024).toString() + " GB"
                                ),
                                Item(
                                    Strings.CAPACITY_AVAILABLE,
                                    null,
                                    (blockSizeLong * statFs.availableBlocksLong / 1024 / 1024 / 1024).toString() + " GB"
                                ),
                                Item(Strings.VERSION, object : OnItemClickListener {
                                    var count = 0
                                    override fun onItemClick(
                                        index: Int,
                                        listView: ListView
                                    ): Boolean {
                                        count++
                                        if (count == 7) {
                                            count = 0
                                            Core.addView(TxtView(context, Strings.ABOUT, about))
                                        }
                                        return true
                                    }
                                }, Values.getVersionName()),
                                Item(
                                    Strings.SN, null,
                                    Base64.encodeToString(
                                        Build.ID.encodeToByteArray(),
                                        Base64.NO_PADDING
                                    ).trim()
                                        .uppercase(Locale.ROOT)
                                ),//android.os.Build.SERIAL),
                                Item(Strings.MODEL, null, "MA446ZP"),
                                Item(Strings.FORMAT, null, "Windows")
                            ), Strings.ABOUT, null
                        )
                    )
                    return true
                }
            }, true),

            Item(Strings.LANGUAGE, object : OnItemClickListener {
                val itemList = arrayListOf(
                    //id和这的顺序要对应
                    Item(
                        Strings.AUTO,
                        null,
                        if (SPManager.getInt(SPManager.Language.SP_NAME) == SPManager.Language.AUTO.id) Strings.CURRENT else Strings.NULL
                    ),
                    Item(
                        Strings.LANGUAGE_CN,
                        null,
                        if (SPManager.getInt(SPManager.Language.SP_NAME) == SPManager.Language.CN.id) Strings.CURRENT else Strings.NULL
                    ),
                    Item(
                        Strings.LANGUAGE_TW,
                        null,
                        if (SPManager.getInt(SPManager.Language.SP_NAME) == SPManager.Language.TW.id) Strings.CURRENT else Strings.NULL
                    ),
                    Item(
                        Strings.LANGUAGE_EN,
                        null,
                        if (SPManager.getInt(SPManager.Language.SP_NAME) == SPManager.Language.EN.id) Strings.CURRENT else Strings.NULL
                    ),
                )

                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(
                        ItemListView(
                            context,
                            itemList,
                            Strings.LANGUAGE,
                            object : OnItemClickListener {
                                override fun onItemClick(index: Int, listView: ListView): Boolean {
                                    SPManager.setInt(SPManager.Language.SP_NAME, index)
                                    //Toast.makeText(context, "set language", Toast.LENGTH_SHORT).show()
                                    Core.reboot()
                                    return true
                                }
                            })
                    ); return true
                }
            }, true),

            Item(Strings.SLEEP_TIME, object : OnItemClickListener {
                fun scheduleToStop(min: Int) {
                    MediaPresenter.setStopTime(min)
                    Core.removeView()
                }

                val itemList = arrayListOf(
                    Item(Strings.DISABLE, object : OnItemClickListener {
                        override fun onItemClick(index: Int, listView: ListView): Boolean {
                            scheduleToStop(0); return true
                        }
                    }, false),
                    //Item("1 分钟", object : OnItemClickListener { override fun onItemClick(index: Int, listView: ListView) : Boolean {scheduleToStop(1); return true } }, false),
                    Item("15 ${Strings.MINUTE}", object : OnItemClickListener {
                        override fun onItemClick(index: Int, listView: ListView): Boolean {
                            scheduleToStop(15); return true
                        }
                    }, false),
                    Item("30 ${Strings.MINUTE}", object : OnItemClickListener {
                        override fun onItemClick(index: Int, listView: ListView): Boolean {
                            scheduleToStop(30); return true
                        }
                    }, false),
                    Item("60 ${Strings.MINUTE}", object : OnItemClickListener {
                        override fun onItemClick(index: Int, listView: ListView): Boolean {
                            scheduleToStop(60); return true
                        }
                    }, false),
                    Item("90 ${Strings.MINUTE}", object : OnItemClickListener {
                        override fun onItemClick(index: Int, listView: ListView): Boolean {
                            scheduleToStop(90); return true
                        }
                    }, false),
                    Item("120 ${Strings.MINUTE}", object : OnItemClickListener {
                        override fun onItemClick(index: Int, listView: ListView): Boolean {
                            scheduleToStop(120); return true
                        }
                    }, false),
                    Item("240 ${Strings.MINUTE}", object : OnItemClickListener {
                        override fun onItemClick(index: Int, listView: ListView): Boolean {
                            scheduleToStop(240); return true
                        }
                    }, false)
                )

                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(ItemListView(context, itemList, "睡眠定时", null)); return true
                }
            }, true),

            Item(Strings.EQUALIZER, object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    val nameList = MediaPresenter.getPresetList()
                    val itemList = ArrayList<Item>()
                    val equalizerIndex = SPManager.getInt(
                        SPManager.SP_EQUALIZER
                    )
                    for (i in nameList.indices) {
                        itemList.add(
                            Item(
                                nameList[i]!!,
                                null,
                                if (i == equalizerIndex) Strings.CURRENT else Strings.NULL
                            )
                        )
                    }
                    Core.addView(
                        ItemListView(
                            context,
                            itemList,
                            Strings.EQUALIZER,
                            object : OnItemClickListener {
                                override fun onItemClick(index: Int, listView: ListView): Boolean {
                                    MediaPresenter.setEqualizer(index)
                                    Core.removeView()
                                    return true
                                }
                            })
                    )
                    return true
                }
            }, true),

            Item(Strings.PLAY_MODE, object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    val playMode = MediaPresenter.getPlayMode()
                    val next = PlayMode.getPlayMode((playMode.id + 1) % PlayMode.count)
                    MediaPresenter.nextPlayMode()
                    listView.getCurrentItem().rightText = next.title
                    return true
                }
            }, MediaPresenter.getPlayMode().title),

            Item(
                Strings.SHUFFLE_PLAY, object : OnItemClickListener {
                    override fun onItemClick(index: Int, listView: ListView): Boolean {
                        SPManager.getBoolean(
                            SPManager.SP_PLAY_ALL
                        ).let {
                            SPManager.setBoolean(
                                SPManager.SP_PLAY_ALL, !it
                            )
                            listView.getCurrentItem().rightText =
                                if (it) Strings.SAVE else Strings.ALL
                        }
                        return true
                    }
                }, if (SPManager.getBoolean(
                        SPManager.SP_PLAY_ALL
                    )
                ) Strings.ALL else Strings.SAVE
            ),

            Item(Strings.REPEAT_MODE, object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    val repeatMode = MediaPresenter.getRepeatMode()
                    val next = RepeatMode.getRepeatMode((repeatMode.id + 1) % RepeatMode.count)
                    MediaPresenter.nextRepeatMode()
                    listView.getCurrentItem().rightText = next.title
                    return true
                }
            }, MediaPresenter.getRepeatMode().title),

            Item(
                Strings.NIGHT_MODE, object : OnItemClickListener {
                    override fun onItemClick(index: Int, listView: ListView): Boolean {
                        var nightMode = SPManager.getInt(SPManager.NightMode.SP_NAME)
                        nightMode++
                        nightMode %= SPManager.NightMode.values
                        SPManager.setInt(
                            SPManager.NightMode.SP_NAME, nightMode
                        )
                        Core.setNightMode()
                        listView.getCurrentItem().rightText =
                            SPManager.NightMode.getTitle(nightMode)
                        return true
                    }

                }, SPManager.NightMode.getTitle(
                    SPManager.getInt(
                        SPManager.NightMode.SP_NAME
                    )
                )
            ),

            SwitchBar(
                Strings.PLAY_WITH_OTHER,
                SPManager.SP_AUDIO_FOCUS,
                true,
                object : OnSwitchListener {
                    override fun onSwitch() {
                        MediaPresenter.setAudioFocus()
                    }
                }),

            Item(
                Strings.TOUCH_FEEDBACK, object : OnItemClickListener {
                    override fun onItemClick(index: Int, listView: ListView): Boolean {
                        var sound = SPManager.getInt(
                            SPManager.Sound.SP_NAME
                        )
                        sound++
                        sound %= SPManager.Sound.values
                        SPManager.setInt(
                            SPManager.Sound.SP_NAME, sound
                        )
                        listView.getCurrentItem().rightText = SPManager.Sound.getTitle(sound)
                        return true
                    }
                }, SPManager.Sound.getTitle(
                    SPManager.getInt(
                        SPManager.Sound.SP_NAME
                    )
                )
            ),

            Item(
                Strings.THEME, object : OnItemClickListener {
                    override fun onItemClick(index: Int, listView: ListView): Boolean {
                        val themeId = SPManager.getInt(SPManager.Theme.SP_NAME)
                        SPManager.setInt(
                            SPManager.Theme.SP_NAME,
                            (themeId + 1) % SPManager.Theme.values
                        )
                        Core.refresh()
                        listView.getCurrentItem().rightText =
                            when (SPManager.getInt(SPManager.Theme.SP_NAME)) {
                                SPManager.Theme.RED.id -> SPManager.Theme.RED.title
                                SPManager.Theme.BLACK.id -> SPManager.Theme.BLACK.title
                                SPManager.Theme.WHITE.id -> SPManager.Theme.WHITE.title
                                else -> ""
                            }
                        return true
                    }
                }, when (SPManager.getInt(SPManager.Theme.SP_NAME)) {
                    SPManager.Theme.RED.id -> SPManager.Theme.RED.title
                    SPManager.Theme.BLACK.id -> SPManager.Theme.BLACK.title
                    SPManager.Theme.WHITE.id -> SPManager.Theme.WHITE.title
                    else -> ""
                }
            ),

            SwitchBar(Strings.SHOW_LYRIC, SPManager.SP_SHOW_LYRIC),

            SwitchBar(Strings.SHOW_INFO, SPManager.SP_SHOW_INFO),

            SwitchBar(Strings.SHOW_TIME, SPManager.SP_SHOW_TIME),

            Item(
                Strings.AUTO_PLAY, object : OnItemClickListener {
                    override fun onItemClick(index: Int, listView: ListView): Boolean {
                        var autoStop = SPManager.getInt(
                            SPManager.AutoStop.SP_NAME
                        )
                        autoStop++
                        autoStop %= SPManager.AutoStop.values
                        SPManager.setInt(
                            SPManager.AutoStop.SP_NAME, autoStop
                        )
                        listView.getCurrentItem().rightText = SPManager.AutoStop.getString(autoStop)
                        return true
                    }
                }, SPManager.AutoStop.getString(
                    SPManager.getInt(
                        SPManager.AutoStop.SP_NAME
                    )
                )
            ),

            SwitchBar(Strings.COVER_FLOW, SPManager.SP_COVER_FLOW),

            Item(Strings.RESET_ALL_SETTINGS, object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(
                        ItemListView(
                            context, arrayListOf(
                                Item(Strings.CANCEL, object : OnItemClickListener {
                                    override fun onItemClick(
                                        index: Int,
                                        listView: ListView
                                    ): Boolean {
                                        Core.removeView(); return true
                                    }
                                }, false),
                                Item(Strings.RESET, object : OnItemClickListener {
                                    override fun onItemClick(
                                        index: Int,
                                        listView: ListView
                                    ): Boolean {
                                        Core.reset();Core.removeView(); return true
                                    }
                                }, false)
                            ), Strings.RESET_ALL, null
                        )
                    )
                    return true
                }
            }, true)
        )
    }
}