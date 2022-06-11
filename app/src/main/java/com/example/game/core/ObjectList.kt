package com.example.game.core

class ObjectList {
    private val list = ArrayList<Object>()

    fun add(o: Object) {
        list.add(o)
    }

    fun remove(o: Object) {
        list.remove(o)
    }

    fun size(): Int {
        return list.size
    }

    fun get(i: Int): Object {
        return list[i]
    }

    fun clear() {
        list.clear()
    }

}
