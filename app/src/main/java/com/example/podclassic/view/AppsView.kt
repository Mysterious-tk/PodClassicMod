package com.example.podclassic.view

import android.content.Context
import com.example.podclassic.base.Core
import com.example.podclassic.bean.App
import com.example.game.Brick
import com.example.podclassic.storage.AppTable
import com.example.podclassic.values.Strings
import com.example.podclassic.values.Values
import com.example.podclassic.widget.RecyclerListView

class AppsView(context: Context) : ItemListView(
    context,
    //这是构造方法的一个参数...轻喷
    arrayListOf(
        Item(Strings.GAME, object : OnItemClickListener {
            val itemList = arrayListOf(
                Item(Strings.BRICK, object : OnItemClickListener {
                    override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                        Core.addView(Brick(context)); return true
                    }
                }, true),
                Item(Strings.MUSIC_QUIZ, object : OnItemClickListener {
                    override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                        Core.addView(MusicQuizView(context)); return true
                    }
                }, true)
            )

            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                Core.addView(ItemListView(context, itemList, Strings.GAME, null)); return true
            }

        }, true)
    ).apply {
        if (Values.LAUNCHER) {
            this.add(Item(Strings.APPLICATION, object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                    val itemList = ArrayList<Item>()
                    val appList = AppTable.getAppList()
                    for (app in appList) {
                        itemList.add(Item(app.name, null, true))
                    }
                    Core.addView(
                        ItemListView(context, itemList, Strings.APPLICATION,
                            object : OnItemClickListener {
                                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                                    context.startActivity(appList[index].intent)
                                    return true
                                }

                                override fun onItemLongClick(
                                    index: Int,
                                    listView: RecyclerListView
                                ): Boolean {
                                    listView.shake()
                                    AppTable.favourite.add(appList[index])
                                    return true
                                }
                            })
                    )
                    return true
                }
            }, true))
        }
    }, Strings.EXTRA_APPLICATION, null
) {

    init {
        this.defaultOnItemClickListener = object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                if (Values.LAUNCHER && index >= 2) {
                    context.startActivity((listView.getItem(index).extra as App).intent)
                    return true
                }
                return false
            }

            override fun onItemLongClick(index: Int, listView: RecyclerListView): Boolean {
                if (Values.LAUNCHER && index >= 2) {
                    AppTable.favourite.remove((listView.getItem(index).extra as App))
                    listView.removeCurrentItem()
                    return true
                }
                return false
            }
        }
    }

    override fun onViewAdd() {
        if (Values.LAUNCHER) {
            val appList = AppTable.favourite.list

            for (app in appList) {
                val item = Item(app.name, null, true).apply {
                    extra = app
                }
                addIfNotExist(item)
            }
            refreshList()
        }
    }

    override fun getTitle(): String {
        return Strings.EXTRA_APPLICATION
    }
}
