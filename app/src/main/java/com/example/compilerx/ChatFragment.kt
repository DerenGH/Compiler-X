package com.example.compilerx

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class ChatFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(android.R.layout.simple_list_item_1, container, false)
        view.findViewById<TextView>(android.R.id.text1).apply {
            text = "This is the Chat Screen"
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
        }
        return view
    }
}