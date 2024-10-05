package com.rahulislam.facepsy.stroop

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.rahulislam.facepsy.R

class StroopDescriptionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stroop_description)

        val stroopStartBtn = findViewById<Button>(R.id.stroopStartBtn)
        stroopStartBtn.setOnClickListener{
            val intent = Intent(this, StroopActivity::class.java).apply {  }
            startActivity(intent)
        }
    }
}