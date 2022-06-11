package com.example.podclassic.bean

import android.content.Intent
import com.example.podclassic.base.BaseApplication

class App(var name: String, var packageName: String) {

    var intent: Intent? = null
        get() {
            if (field != null) {
                return field
            }
            val packageManager = BaseApplication.context.packageManager
            field = packageManager.getLaunchIntentForPackage(packageName)
            return field
        }

}