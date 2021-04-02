package com.example.podclassic.base

import android.app.Application
import android.content.Context
import android.os.Environment
import com.example.podclassic.`object`.Core
import java.io.File
import java.io.FileWriter

class BaseApplication : Application() {
    companion object {
        lateinit var context: Context
    }

    override fun onCreate() {
        super.onCreate()
        context = this
        Core.init()

        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            val file = File(Environment.getExternalStorageDirectory().path + File.separator + "log.txt");
            val fw = FileWriter(file)
            fw.write(e?.message +"\n")
            fw.write(e?.localizedMessage +"\n")
            for (x in e!!.stackTrace) {
                fw.write(x.toString() + "\n")
            }
            fw.close()
        }
    }
}