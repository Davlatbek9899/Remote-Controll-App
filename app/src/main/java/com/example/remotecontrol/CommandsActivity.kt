package com.example.remotecontrol

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class CommandsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_commands)
        findViewById<ImageButton>(R.id.btnBackCommands).setOnClickListener { finish() }
    }
}
