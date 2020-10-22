package com.example.podclassic.fragment

import android.annotation.SuppressLint
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.fragment.app.Fragment
import com.example.podclassic.R
import com.example.podclassic.`object`.Core
import com.example.podclassic.`object`.MediaPlayer
import com.example.podclassic.storage.SaveMusicLists
import com.example.podclassic.storage.SaveMusics
import com.example.podclassic.util.*
import java.lang.Exception

class SplashFragment : Fragment() {

    private val asyncTask = @SuppressLint("StaticFieldLeak")
    object : AsyncTask<Unit, Unit, Unit>() {
        override fun doInBackground(vararg params: Unit?) {
            MediaUtil.prepare()
        }

        override fun onPostExecute(result: Unit?) {
            fragmentManager
                ?.beginTransaction()
                ?.remove(this@SplashFragment)
                ?.commitAllowingStateLoss()
            Core.lock(false)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        asyncTask.execute()
        return inflater.inflate(R.layout.fragment_splash, container, false)
    }
}