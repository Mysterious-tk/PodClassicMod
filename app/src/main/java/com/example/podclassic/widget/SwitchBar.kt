package com.example.podclassic.widget

import com.example.podclassic.storage.SPManager

class SwitchBar(title: String, bindSP: String, reverse : Boolean = false) : ListView.Item(title, object : ListView.OnItemClickListener {
    override fun onItemClick(index: Int, listView: ListView): Boolean {
        SPManager.getBoolean(bindSP).let {
            SPManager.setBoolean(bindSP, !it)
            listView.getCurrentItem().rightText = if (if (reverse) !it else it) "关闭" else "打开"
        }
        return true
    }
}, if (if (reverse) !SPManager.getBoolean(bindSP) else SPManager.getBoolean(bindSP)) "打开" else "关闭") {

}