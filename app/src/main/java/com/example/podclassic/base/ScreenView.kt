package com.example.podclassic.base

interface ScreenView {

    fun enter() : Boolean

    fun enterLongClick() : Boolean

    fun slide(slideVal : Int) : Boolean

    companion object {
        const val LAUNCH_MODE_SINGLE = 0b00000001
        const val LAUNCH_MODE_NORMAL = 0b00000000
    }

    fun getTitle() : String

    fun getLaunchMode() : Int
}