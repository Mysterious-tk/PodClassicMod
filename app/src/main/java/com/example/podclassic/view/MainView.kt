package com.example.podclassic.view

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Environment
import com.example.podclassic.`object`.Core
import com.example.podclassic.`object`.MediaPlayer
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.base.ScreenView
import com.example.podclassic.game.Brick
import com.example.podclassic.util.FileUtil
import com.example.podclassic.util.MediaUtil
import com.example.podclassic.util.Values
import com.example.podclassic.widget.ListView
import java.io.File


class MainView(context: Context) : ListView(context), ScreenView {


    companion object {
        const val TITLE = Values.POD


        class App(val name: String, val intent: Intent)

        lateinit var appList : ArrayList<App>

        fun loadAppList() {
            val context = BaseApplication.context
            val packageManager = context.packageManager
            val packages: List<PackageInfo> = packageManager.getInstalledPackages(0)
            appList = ArrayList(packages.size)
            for (packageInfo in packages) {
                if (packageInfo.packageName == context.packageName) {
                    continue
                }

                val appName = packageInfo.applicationInfo.loadLabel(packageManager).toString()
                val packageName = packageInfo.packageName

                val intent = Intent(Intent.ACTION_MAIN)
                intent.addCategory(Intent.CATEGORY_LAUNCHER)
                intent.flags = Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_NEW_TASK

                var mainActivity : String? = null
                @SuppressLint("WrongConstant") val list: List<ResolveInfo> =
                    packageManager.queryIntentActivities(intent, PackageManager.GET_ACTIVITIES)
                for (info in list) {
                    if (info.activityInfo.packageName == packageName) {
                        mainActivity = info.activityInfo.name
                        break
                    }
                }
                if (mainActivity!= null && mainActivity.isNotEmpty()) {
                    intent.component = ComponentName(packageName, mainActivity)
                    appList.add(App(appName, intent))
                }
            }
        }


    }

    override fun getTitle(): String { return TITLE }

    val item = Item("正在播放", object : OnItemClickListener {
        override fun onItemClick(index: Int, listView: ListView): Boolean {
            Core.addView(MusicPlayerView(context))
            return true
        }
    }, true)

    init {
        itemList = arrayListOf(
            Item("音乐", object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(MusicView(context))
                    return true
                }

            }, true),
            Item("照片", object : OnItemClickListener {
                val list by lazy { MediaUtil.photoList }
                private fun getItemList(): ArrayList<Item> {
                    val itemList = ArrayList<Item>()
                    for (file in list) {
                        itemList.add(Item(file.name, null, false))
                    }
                    return itemList
                }

                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(
                        ItemListView(context, getItemList(), "照片", object : OnItemClickListener {
                                override fun onItemClick(index: Int, listView: ListView): Boolean {
                                    Core.addView(ImageView(context, list, index))
                                    return true
                                }
                            })
                    )
                    return true
                }

            }, true),
            Item("视频", object : OnItemClickListener {
                val list by lazy { MediaUtil.videoList }

                private fun getItemList(): ArrayList<Item> {
                    val itemList = ArrayList<Item>()
                    for (file in list) {
                        itemList.add(Item(file.name, null, false))
                    }
                    return itemList
                }

                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(
                        ItemListView(context, getItemList(), "视频", object : OnItemClickListener {
                                override fun onItemClick(index: Int, listView: ListView): Boolean {
                                    val file = list[index]
                                    return if (file.exists()) {
                                        Core.addView(VideoView(context, list[index]))
                                        true
                                    } else {
                                        false
                                    }
                                }
                            })
                    )
                    return true
                }

            }, true),
            Item("文件", object : OnItemClickListener {
                private fun getItemList(): ArrayList<Item>? {
                    val sdCardPath = FileUtil.getSDCardPath() ?: return null
                    val itemList = ArrayList<Item>()
                    itemList.add(Item("内部存储", object : OnItemClickListener {
                        override fun onItemClick(index: Int, listView: ListView): Boolean {
                            Core.addView(FileView(context, Environment.getExternalStorageDirectory()))
                            return true
                        }
                    }, true))

                    itemList.add(Item("SD卡", object : OnItemClickListener {
                        override fun onItemClick(index: Int, listView: ListView): Boolean {
                            Core.addView(FileView(context, File(sdCardPath)))
                            return true
                        }
                    }, true))
                    return itemList
                }

                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    val itemList = getItemList()
                    if (itemList == null) {
                        Core.addView(FileView(context, Environment.getExternalStorageDirectory()))
                    } else {
                        Core.addView(ItemListView(context, itemList, "文件", null))
                    }
                    return true
                }

            }, true),

            Item("附加程序", object : OnItemClickListener {
                val itemList = arrayListOf(
                    Item("游戏", object : OnItemClickListener {
                        val itemList = arrayListOf(
                            Item("Brick", object : OnItemClickListener {
                                override fun onItemClick(
                                    index: Int,
                                    listView: ListView
                                ): Boolean {
                                    Core.addView(Brick(context)); return true
                                }
                            }, true),
                            Item("Music Quiz", object : OnItemClickListener {
                                override fun onItemClick(
                                    index: Int,
                                    listView: ListView
                                ): Boolean {
                                    Core.addView(MusicQuizView(context)); return true
                                }
                            }, true)
                        )

                        override fun onItemClick(index: Int, listView: ListView): Boolean {
                            Core.addView(
                                ItemListView(context, itemList, "游戏", null)
                            ); return true
                        }

                    }, true) ,


                    Item("所有程序", object : OnItemClickListener {

                        override fun onItemClick(index: Int, listView: ListView): Boolean {
                            val itemList = ArrayList<Item>()
                            for (app in appList) {
                                itemList.add(Item(app.name, null, true))
                            }
                            Core.addView(ItemListView(context, itemList, "所有程序",
                                    object : OnItemClickListener {
                                        override fun onItemClick(index: Int, listView: ListView): Boolean {
                                            context.startActivity(appList[index].intent)
                                            return true
                                        }
                                    })
                            )
                            return true
                        }
                    }, true)


                )

                override fun onItemClick(index: Int, listView: ListView): Boolean { Core.addView(ItemListView(context, itemList, "附加程序", null)); return true }

            }, true),

            Item("设置", object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    Core.addView(
                        SettingsView(
                            context
                        )
                    ); return true
                }

            }, true),
            Item("随机播放歌曲", object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: ListView): Boolean {
                    if (MediaPlayer.shufflePlay()) {
                        Core.addView(MusicPlayerView(context))
                        return true
                    }
                    return false
                }
            }, false)
        )
    }


    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            if (MediaPlayer.getCurrent() == null) {
                remove(item)
            } else {
                addIfNotExist(item)
            }
        }
    }

    override fun enter() : Boolean { return onItemClick() }

    override fun enterLongClick() : Boolean { return false }

    override fun slide(slideVal: Int): Boolean { return onSlide(slideVal) }

    override fun getLaunchMode(): Int {
        return ScreenView.LAUNCH_MODE_SINGLE
    }
}