package com.example.podclassic.bean

import com.example.podclassic.util.MediaStoreUtil
import com.example.podclassic.util.PinyinUtil

data class MusicList(
    val title: String,
    val subtitle: String,
    val data: String,
    val id: Long? = 0L,
    val type: Int = TYPE_NONE,
    var list: ArrayList<Music>?
) : Comparable<MusicList> {

    companion object {
        const val TYPE_NONE = 0
        const val TYPE_ALBUM = 1
        const val TYPE_ARTIST = 2
        const val TYPE_FOLDER = 3
    }

    private var pinyin: String? = null
        get() {
            if (field == null) {
                field = PinyinUtil.getPinyin(title)
            }
            return field
        }

    class Builder {
        var title: String = ""
        var data: String = ""
        var subtitle : String = ""
        var id: Long? = null
        var type: Int = 0
        var list: ArrayList<Music>? = null
        fun build(): MusicList {
            return MusicList(title, subtitle, data, id, type, list)
        }
    }

    fun getMusicList(): ArrayList<Music> {
        if (list == null) {
            list = if (type == TYPE_ALBUM) {
                MediaStoreUtil.getAlbumMusic(title)
            } else {
                MediaStoreUtil.getArtistMusic(title)
            }
        }
        return list!!
    }

    override fun hashCode(): Int {
        return data.hashCode() * type
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (other !is MusicList) {
            return false
        }
        return title == other.title && type == other.type && data == other.data
    }

    override fun compareTo(other: MusicList): Int {
        return pinyin!!.compareTo(other.pinyin!!)
    }
}