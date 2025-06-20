package com.kezong.demo.libaar

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment

class SecondFragment : Fragment(R.layout.fragment_second) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.let {
            val age = SecondFragmentArgs.fromBundle(it).age
            view.findViewById<Button>(R.id.tv_second)
                .setText("[Success] Navigation component, male is ${age} years old")
        }
    }
}
