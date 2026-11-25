package com.baytna.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baytna.app.data.prefs.Settings
import com.baytna.app.data.prefs.ThemeMode
import com.baytna.app.ui.BaytnaRoot
import com.baytna.app.ui.theme.BaytnaTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BaytnaApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            BaytnaTheme(darkTheme = dark) {
                BaytnaRoot(container)
            }
        }
    }
}
