package com.example.podclassic.view

import android.content.Context
import com.example.podclassic.`object`.Core
import com.example.podclassic.game.Brick
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.Values
import com.example.podclassic.widget.ListView

class AppsView(context: Context) : ItemListView(context,
    //这是构造方法的一个参数...
    arrayListOf(
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

        }, true)).apply {
            if (Values.LAUNCHER) {
                this.add(Item("所有程序", object : OnItemClickListener {
                    override fun onItemClick(index: Int, listView: ListView): Boolean {
                        val itemList = ArrayList<Item>()
                        val appList = SPManager.AppList.getAppList()
                        for (app in appList) {
                            itemList.add(Item(app.name, null, true))
                        }
                        Core.addView(
                            ItemListView(context, itemList, "所有程序",
                                object : OnItemClickListener {
                                    override fun onItemClick(index: Int, listView: ListView): Boolean {
                                        context.startActivity(appList[index].intent)
                                        return true
                                    }

                                    override fun onItemLongClick(index: Int, listView: ListView): Boolean {
                                        listView.shake()
                                        SPManager.AppList.addApp(appList[index])
                                        return true
                                    }
                                })
                        )
                        return true
                    }
                }, true))
            }
        }
    , "附加程序", null) {

    init {
        this.defaultOnItemClickListener = object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: ListView): Boolean {
                if (Values.LAUNCHER && index >= 2) {
                    context.startActivity((listView.getItem(index).extra as SPManager.App).intent)
                    return true
                }
                return false
            }
            override fun onItemLongClick(index: Int, listView: ListView): Boolean {
                if (Values.LAUNCHER && index >= 2) {
                    SPManager.AppList.removeApp((listView.getItem(index).extra as SPManager.App))
                    listView.removeCurrentItem()
                    return true
                }
                return false
            }
        }
        val appList = SPManager.AppList.getSavedAppList()

        for (app in appList) {
            itemList.add(Item(app.name, null, true).apply {
                extra = app
            })
        }

    }
    override fun onStart() {
        if (Values.LAUNCHER) {
            val appList = SPManager.AppList.getSavedAppList()

            for (app in appList) {
                val item = Item(app.name, null, true).apply {
                    extra = app
                }
                if (!itemList.contains(item)) {
                    itemList.add(item)
                }
            }
            refreshList()
        }
    }
}