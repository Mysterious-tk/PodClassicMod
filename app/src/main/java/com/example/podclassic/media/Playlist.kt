package com.example.podclassic.media

open class Playlist<E>(private val mediaAdapter: MediaPlayer.MediaAdapter<E>) {

    fun play(mediaPlayer: android.media.MediaPlayer): Boolean {
        val e = getCurrent() ?: return false
        return mediaAdapter.onLoadMedia(e, mediaPlayer)
    }

    var playMode: PlayMode = PlayMode.ORDER
        set(value) {
            field = value
            if (value == PlayMode.SHUFFLE) {
                shufflePlayList()
            } else if (value == PlayMode.ORDER) {
                val current = getCurrent()
                currentPlaylist = ArrayList(orderedPlaylist)
                index = currentPlaylist.indexOf(current)
            }
        }

    var repeatMode: RepeatMode = RepeatMode.ALL

    protected var currentPlaylist: ArrayList<E> = ArrayList()
    protected var orderedPlaylist: ArrayList<E> = ArrayList()
    var index = -1
        set(value) {
            if (field == value) {
                return
            }
            if (value in -1 until currentPlaylist.size) {
                field = value
            }
        }

    fun setPlaylist(list: ArrayList<E>): Playlist<E> {
        if (list == orderedPlaylist) {
            return this
        }
        currentPlaylist = ArrayList(list)
        orderedPlaylist = ArrayList(list)
        index = -1
        if (playMode == PlayMode.SHUFFLE) {
            shufflePlayList()
        }
        return this
    }

    fun setPlaylist(list: ArrayList<E>, index: Int): Playlist<E> {
        if (list == orderedPlaylist) {
            if (playMode == PlayMode.SHUFFLE) {
                val music = list[index]
                this.index = currentPlaylist.indexOf(music)
            } else {
                this.index = index
            }
            return this
        }

        currentPlaylist = list.clone() as ArrayList<E>
        orderedPlaylist = list.clone() as ArrayList<E>
        this.index = index
        if (playMode == PlayMode.SHUFFLE) {
            shufflePlayList()
        }
        return this
    }

    fun getPlaylistSize(): Int {
        return currentPlaylist.size
    }

    fun getPlayList(): ArrayList<E> {
        return currentPlaylist
    }

    fun shufflePlay(list: ArrayList<E>): Boolean {
        setPlaylist(list)
        playMode = PlayMode.SHUFFLE
        index = 0
        return list.isNotEmpty()
    }

    fun shufflePlay(list: ArrayList<E>, index: Int): Boolean {
        setPlaylist(list)
        val music = list[index]
        playMode = PlayMode.SHUFFLE
        this.index = currentPlaylist.indexOf(music)
        return list.isNotEmpty()
    }

    fun add(e: E) {
        val index = currentPlaylist.indexOf(e)
        if (index == -1) {
            currentPlaylist.add(this.index + 1, e)
            orderedPlaylist.add(this.index + 1, e)
            this.index++
        } else {
            this.index = index
        }
    }

    fun remove(e: E) {
        val index = currentPlaylist.indexOf(e)
        if (index in 0 until currentPlaylist.size) {
            removeAt(index)
        }
    }

    fun removeAt(index: Int) {
        if (index !in 0 until currentPlaylist.size) {
            return
        }
        val music = currentPlaylist.removeAt(index)
        orderedPlaylist.remove(music)

        if (index == this.index) {
            if (index >= currentPlaylist.size) {
                this.index--
            }
            //startMediaPlayer()
        } else if (index < this.index) {
            this.index--
        }
    }

    fun setCurrent(music: E) {
        val index = currentPlaylist.indexOf(music)
        if (index != -1) {
            this.index = index
        }
    }

    fun getCurrent(): E? {
        if (index in 0 until currentPlaylist.size) {
            return currentPlaylist[index]
        }
        return null
    }

    fun next(): E? {
        if (currentPlaylist.isEmpty()) {
            return null
        }
        index = (index + 1) % currentPlaylist.size
        return currentPlaylist[index]
    }

    fun prev(): E? {
        if (currentPlaylist.isEmpty()) {
            return null
        }
        index = (index - 1 + currentPlaylist.size) % currentPlaylist.size
        return currentPlaylist[index]
    }

    fun onCompletion(): E? {
        if (currentPlaylist.isEmpty()) {
            return null
        }
        if (playMode == PlayMode.SINGLE) {
            return currentPlaylist[index]
        }
        if (repeatMode == RepeatMode.NONE && index == currentPlaylist.size) {
            return null
        }
        index = (index + 1) % currentPlaylist.size
        return currentPlaylist[index]
    }

    fun clear() {
        currentPlaylist.clear()
        orderedPlaylist.clear()
        index = -1
    }

    fun isEmpty(): Boolean {
        return currentPlaylist.size == 0
    }

    fun isNotEmpty(): Boolean {
        return !isEmpty()
    }

    fun size(): Int {
        return currentPlaylist.size
    }

    private fun shufflePlayList() {
        if (index !in 0 until currentPlaylist.size) {
            currentPlaylist.shuffle()
            return
        }
        val current = currentPlaylist[index]
        currentPlaylist.shuffle()
        index = currentPlaylist.indexOf(current)
    }
}
