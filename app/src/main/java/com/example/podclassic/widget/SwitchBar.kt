package com.example.podclassic.widget

import com.example.podclassic.storage.SPManager
import com.example.podclassic.values.Strings

class SwitchBar(
    title: String,
    bindSP: String,
    reverse: Boolean = false,
    private val onSwitchListener: OnSwitchListener? = null
) : RecyclerListView.Item(
    title,
    object : RecyclerListView.OnItemClickListener {
        override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
            SPManager.getBoolean(bindSP).let {
                SPManager.setBoolean(bindSP, !it)
                listView.getCurrentItem().rightText =
                    if (if (reverse) !it else it) disable else enable
            }
            onSwitchListener?.onSwitch()
            return true
        }
    },
    if (if (reverse) !SPManager.getBoolean(bindSP) else SPManager.getBoolean(bindSP)) enable else disable
)

private val enable
    get() = Strings.ENABLE
private val disable
    get() = Strings.DISABLE

interface OnSwitchListener {
    fun onSwitch()
}
