package com.example.podclassic.view

import android.content.Context
import com.example.podclassic.base.Core
import com.example.podclassic.base.ScreenView
import com.example.podclassic.bean.MusicList
import com.example.podclassic.service.MediaPresenter
import com.example.podclassic.storage.MusicListTable
import com.example.podclassic.storage.MusicTable
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.MediaStoreUtil
import com.example.podclassic.values.Strings
import com.example.podclassic.widget.RecyclerListView
import java.io.File

class MusicView(context: Context) : RecyclerListView(context), ScreenView {
    override fun getTitle(): String {
        return Strings.SONG
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

    private val currentList = Item(Strings.CURRENT_PLAYLIST, object : OnItemClickListener {
        override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
            Core.addView(
                MusicListView(
                    context,
                    MediaPresenter.getPlaylist(),
                    Strings.CURRENT_PLAYLIST,
                    MusicListView.TYPE_CURRENT
                )
            )
            return true
        }
    }, true)

    private val coverFlow = Item(Strings.COVER_FLOW, object : OnItemClickListener {
        override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
            if (MediaStoreUtil.getAlbumList().isEmpty()) {
                return false
            }
            Core.addView(CoverFlowView(context))
            return true
        }
    }, true)

    init {
        itemList = arrayListOf(
            Item(Strings.MUSIC, object : OnItemClickListener {
                val musicList = MediaStoreUtil.getMusicList()
                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                    Core.addView(
                        MusicListView(
                            context,
                            musicList,
                            Strings.MUSIC,
                            MusicListView.TYPE_NORMAL
                        )
                    )
                    return true
                }
            }, true),

            Item(Strings.ALBUM, object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                    Core.addView(
                        AlbumListView(
                            context,
                            MediaStoreUtil.getAlbumList(),
                            Strings.ALBUM,
                            AlbumListView.LONG_CLICK_ADD
                        )
                    )
                    return true
                }
            }, true),

            Item(Strings.ARTIST, object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                    val singerList = MediaStoreUtil.getArtistList()
                    val itemList1 = ArrayList<Item>(singerList.size)
                    for (musicList in singerList) {
                        itemList1.add(Item(musicList.title, null, true))
                    }
                    val itemListView = ItemListView(
                        context,
                        itemList1,
                        Strings.ARTIST,
                        object : OnItemClickListener {
                            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                                val albumList =
                                    MediaStoreUtil.getArtistAlbum(singerList[index].title)
                                val musicList = MusicList.Builder().apply {
                                    title = singerList[index].title
                                    type = MusicList.TYPE_ARTIST
                                }.build()
                                albumList.add(0, musicList)
                                Core.addView(
                                    AlbumListView(
                                        context,
                                        albumList,
                                        singerList[index].title,
                                        AlbumListView.LONG_CLICK_ADD
                                    )
                                )
                                return true
                            }

                            override fun onItemLongClick(index: Int, listView: RecyclerListView): Boolean {
                                MusicListTable.artist.add(singerList[index])
                                listView.shake()
                                return true
                            }
                        })
                    itemListView.sorted = true
                    Core.addView(itemListView)
                    return true
                }

            }, true),

            Item(Strings.SAVE_FOLDER, object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                    val itemList = ArrayList<Item>()
                    val files = MusicListTable.folder.getList()

                    for (file in files) {
                        itemList.add(Item(file.title, null, true))
                    }
                    Core.addView(
                        ItemListView(
                            context,
                            itemList,
                            Strings.FOLDER,
                            object : OnItemClickListener {
                                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                                    Core.addView(FileView(context, File(files[index].data)))
                                    return true
                                }

                                override fun onItemLongClick(
                                    index: Int,
                                    listView: RecyclerListView
                                ): Boolean {
                                    listView.removeCurrentItem()
                                    MusicListTable.folder.delete(files[index])
                                    return true
                                }
                            })
                    )
                    return true
                }
            }, true),


            Item(Strings.SAVE_MUSIC, object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                    Core.addView(
                        MusicListView(
                            context,
                            MusicTable.favourite.getList(),
                            Strings.SAVE_MUSIC,
                            MusicListView.TYPE_FAVOURITE
                        )
                    )
                    return true
                }
            }, true),
            Item(Strings.SAVE_ALBUM, object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                    Core.addView(
                        AlbumListView(
                            context,
                            MusicListTable.album.getList(),
                            Strings.SAVE_ALBUM,
                            AlbumListView.LONG_CLICK_REMOVE
                        )
                    )
                    return true
                }
            }, true),
            Item(Strings.SAVE_ARTIST, object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                    Core.addView(
                        AlbumListView(
                            context,
                            MusicListTable.artist.getList(),
                            Strings.SAVE_ARTIST,
                            AlbumListView.LONG_CLICK_REMOVE
                        )
                    )
                    return true
                }
            }, true)
        )
        if (SPManager.getBoolean(
                SPManager.SP_COVER_FLOW
            )
        ) {
            addIfNotExist(coverFlow)
        }

        if (MediaPresenter.getCurrent() != null) {
            addIfNotExist(currentList, 0)
        }
    }

    override fun onViewAdd() {
        if (MediaPresenter.getCurrent() == null) {
            remove(currentList)
        } else {
            addIfNotExist(currentList, 0)
        }
    }
}
