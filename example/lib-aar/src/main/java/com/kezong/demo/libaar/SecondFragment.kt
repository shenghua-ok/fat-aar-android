package com.kezong.demo.libaar

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment

class SecondFragment : Fragment(R.layout.fragment_second) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.let {
            val age = SecondFragmentArgs.fromBundle(it).age
            view.findViewById<TextView>(R.id.tv_second)
                .setText("[Success] Navigation component, male is ${age} years old")
        }
    }
}
