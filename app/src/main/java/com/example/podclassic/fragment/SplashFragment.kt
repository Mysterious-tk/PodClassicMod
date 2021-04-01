package com.example.podclassic.fragment

import android.annotation.SuppressLint
import android.os.AsyncTask
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.podclassic.R
import com.example.podclassic.`object`.Core

class SplashFragment(val runnable : Runnable) : Fragment() {

    private val asyncTask = @SuppressLint("StaticFieldLeak")
    object : AsyncTask<Unit, Unit, Unit>() {
        override fun doInBackground(vararg params: Unit?) {
            runnable.run()
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