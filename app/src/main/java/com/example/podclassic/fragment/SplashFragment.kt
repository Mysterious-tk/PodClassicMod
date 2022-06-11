package com.example.podclassic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.podclassic.R
import com.example.podclassic.util.ThreadUtil

class SplashFragment(private val runnable: Runnable) : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        ThreadUtil.asyncTask(runnable) {
            parentFragmentManager
                .beginTransaction()
                .remove(this@SplashFragment)
                .commitAllowingStateLoss()
        }

        return inflater.inflate(R.layout.fragment_splash, container, false)
    }
}