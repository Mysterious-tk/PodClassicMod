package com.example.podclassic.base

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Environment
import com.example.podclassic.`object`.Core
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class BaseApplication : Application() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
    }

    override fun onCreate() {
        super.onCreate()
        context = this
        Core.init()

        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            val file = File(Environment.getExternalStorageDirectory().path + File.separator + "log.txt");
            val fw = FileWriter(file, true)
            fw.write("\n----------------------------------------------------\n")
            fw.write(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            fw.write(e?.message +"\n")
            fw.write(e?.localizedMessage +"\n")
            for (x in e!!.stackTrace) {
                fw.write(x.toString() + "\n")
            }
            fw.close()
        }
    }
}