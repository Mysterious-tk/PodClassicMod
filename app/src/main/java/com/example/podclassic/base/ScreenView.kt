package com.example.podclassic.base

interface ScreenView {

    fun enter(): Boolean

    fun enterLongClick(): Boolean

    fun slide(slideVal: Int): Boolean

    companion object {
        const val LAUNCH_MODE_SINGLE = 0b00000001
        const val LAUNCH_MODE_NORMAL = 0b00000000
    }

    fun getTitle(): String?

    fun getLaunchMode(): Int = LAUNCH_MODE_NORMAL

    fun onViewCreate() {}

    fun onViewDelete() {}

    fun onViewAdd() {}

    fun onViewRemove() {}

    /**
     * Host lifecycle callbacks are separate from the custom screen stack callbacks.
     * Backgrounding the Activity must not change onViewAdd/onViewRemove semantics.
     */
    fun onHostStart() {}

    fun onHostStop() {}

    fun getObserver(): Observer? {
        return null
    }

    fun onConfigurationChanged() {}
}
