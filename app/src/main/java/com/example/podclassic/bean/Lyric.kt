package com.example.podclassic.bean

class Lyric(private val lyrics: ArrayList<LyricLine>) {

    class LyricLine(val time: Int, val lyric: String)

    private var prevIndex = 0

    fun getLyric(time: Int): String {
        lyrics.let {
            if (prevIndex >= 0 && prevIndex < it.size - 1 && it[prevIndex].time <= time && it[prevIndex + 1].time > time) {
                return it[prevIndex].lyric
            } else if (prevIndex >= 0 && prevIndex < it.size - 2 && it[prevIndex + 1].time <= time && it[prevIndex + 2].time > time) {
                return it[++prevIndex].lyric
            }
            var left = 0
            var right = it.size - 1
            while (left <= right) {
                val mid = (left + right) / 2
                if (it[mid].time > time) {
                    right = mid - 1
                } else {
                    left = mid + 1
                }
            }
            if (right < 0) {
                right = 0
            }
            prevIndex = right
            return it[right].lyric
        }
    }

}

