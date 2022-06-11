package com.example.podclassic.media

import com.example.podclassic.R
import com.example.podclassic.values.Strings

enum class PlayMode(val id: Int, private val stringId: Int) {
    ORDER(0, R.string.play_mode_order), SHUFFLE(1, R.string.play_mode_shuffle), SINGLE(
        2,
        R.string.play_mode_single
    ),
    UNKNOWN(3, R.string.empty);

    val title
        get() = Strings.getString(stringId)

    companion object {
        const val count = 3
        fun getPlayMode(index: Int): PlayMode {
            return when (index) {
                0 -> ORDER
                1 -> SHUFFLE
                2 -> SINGLE
                else -> UNKNOWN
            }
        }
    }

}