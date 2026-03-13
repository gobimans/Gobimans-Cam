package com.gobimans.cam
// Fixed crashing issue

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Color

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this)
        layout.setBackgroundColor(Color.WHITE)
        
        val textView = TextView(this)
        textView.text = "Gobimans Camera App"
        textView.textSize = 20f
        textView.setTextColor(Color.BLACK)
        
        layout.addView(textView)
        setContentView(layout)
    }
}
