package com.example.remotecontrol

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class CommandsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_commands)

        val header = findViewById<android.widget.LinearLayout>(R.id.headerCommands)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { view, insets ->
            val statusBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
            val dp16 = (16 * resources.displayMetrics.density).toInt()
            view.setPadding(
                view.paddingLeft,
                statusBar + dp16,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

        findViewById<ImageButton>(R.id.btnBackCommands).setOnClickListener { finish() }
    }
}
