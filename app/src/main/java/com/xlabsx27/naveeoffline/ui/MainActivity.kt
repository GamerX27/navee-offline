package com.xlabsx27.naveeoffline.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xlabsx27.naveeoffline.container
import com.xlabsx27.naveeoffline.ui.theme.NaveeTheme

/** AppCompat only for per-app language (🇳🇴/🇬🇧) on Android 12 and older; the UI is all Compose. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val c = container
        setContent {
            val settings by c.settings.collectAsStateWithLifecycle()
            NaveeTheme(settings.theme, settings.dynamicColor) {
                AppNav(c)
            }
        }
    }
}
