package com.example.autotapper

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class TutorialActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)
        findViewById<View>(R.id.btn_tutorial_done).setOnClickListener { finish() }
    }
}
