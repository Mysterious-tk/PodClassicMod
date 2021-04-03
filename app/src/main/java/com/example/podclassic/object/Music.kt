package com.example.podclassic.`object`

import android.text.TextUtils
import com.example.podclassic.util.Values


class Music(name: String, album: String?, singer: String?, var path: String, var id: Long) {

    var name = name
    set(value) {
        if (value.isNotBlank()) {
            field = value
        }
    }

    var album = album
        set(value) {
            if (value?.isNotBlank() == true) {
                field = value
            }
        }
        get() = field ?: Values.NULL
    var singer = singer
        set(value) {
            if (value?.isNotBlank() == true) {
                field = value
            }
        }
        get() = field ?: Values.NULL


    override fun hashCode(): Int {
        return path.hashCode()
    }

    override fun toString(): String {
        return "name = $name , album = $album , singer = $singer , path = $path"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Music) {
            return false
        }
        if (other.path == path && !TextUtils.isEmpty(path)) {
            return true
        }
        return false
    }
}