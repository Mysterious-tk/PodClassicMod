package com.example.podclassic.storage

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.bean.App
import java.text.Collator
import java.util.*


class AppTable(private val name: String) {


    private fun getDatabase(): SQLiteDatabase {
        return DatabaseOpenHelper(BaseApplication.context, name, getTable(name)).writableDatabase
    }

    val list = initList()

    private fun initList(): ArrayList<App> {
        val db = getDatabase()
        val cursor =
            db.query(name, arrayOf(APP_NAME, PACKAGE_NAME), null, null, null, null, APP_NAME)
        val apps = ArrayList<App>(cursor.count)
        if (cursor.moveToFirst()) {
            while (cursor.moveToNext()) {
                val app = buildApp(cursor)
                if (app.intent != null) {
                    apps.add(app)
                }
            }
        }
        cursor.close()
        db.close()
        return apps
    }

    fun add(app: App) {
        list.add(app)
        val db = getDatabase()
        val value = putValues(app)
        db.insert(name, null, value)
        db.close()
    }

    fun remove(app: App) {
        list.remove(app)
        val db = getDatabase()
        db.delete(name, "$PACKAGE_NAME=?", arrayOf(app.packageName))
        db.close()
    }

    companion object {
        fun getTable(name: String): String {
            return "CREATE TABLE IF NOT EXISTS $name(number INTEGER PRIMARY KEY AUTOINCREMENT, $APP_NAME VARCHAR, $PACKAGE_NAME VARCHAR);"
        }

        const val APP_NAME = "app_name"
        private const val PACKAGE_NAME = "package_name"

        private const val FAVORITE_APP = "favorite_app"

        val favourite = AppTable(FAVORITE_APP)

        fun getAppList(): ArrayList<App> {
            val context: Context = BaseApplication.context
            val packageManager: PackageManager = context.packageManager
            val apps: ArrayList<App> = ArrayList()
            val packages = packageManager.getInstalledPackages(0)
            for (packageInfo in packages) {
                val packageName = packageInfo.packageName
                if (packageName == context.packageName) {
                    continue
                }
                val name = packageInfo.applicationInfo.loadLabel(packageManager).toString()
                val app = App(name, packageName)
                if (app.intent != null) {
                    apps.add(app)
                }
            }
            val collator = Collator.getInstance(Locale.CHINA)
            apps.sortWith { o1, o2 -> collator.compare(o1.name, o2.name) }
            return apps
        }

        private fun buildApp(cursor: Cursor): App {
            val appName = cursor.getString(cursor.getColumnIndex(APP_NAME))
            val packageName = cursor.getString(cursor.getColumnIndex(PACKAGE_NAME))
            return App(appName, packageName)
        }

        fun putValues(app: App): ContentValues {
            val values = ContentValues()
            values.put(APP_NAME, app.name)
            values.put(PACKAGE_NAME, app.packageName)
            return values
        }
    }
}