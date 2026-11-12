package com.focuslite.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focuslite.app.ui.MainScreen
import com.focuslite.app.ui.theme.FocusLiteTheme

/**
 * Main dashboard activity for FocusLite.
 * Provides controls for toggling YouTube Shorts and Instagram Reels blocking,
 * viewing real-time accessibility service status, and accessing Android settings.
 */
class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferencesManager = PreferencesManager.getInstance(applicationContext)

        setContent {
            FocusLiteTheme {
                // Reactive state for accessibility permission
                var isServiceEnabled by remember {
                    mutableStateOf(AccessibilityUtils.isAccessibilityServiceEnabled(this))
                }

                // Automatically re-check service status whenever user returns from Settings
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            isServiceEnabled = AccessibilityUtils.isAccessibilityServiceEnabled(this@MainActivity)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        isServiceEnabled = isServiceEnabled,
                        onOpenSettingsClick = {
                            AccessibilityUtils.openAccessibilitySettings(this)
                        },
                        preferencesManager = preferencesManager
                    )
                }
            }
        }
    }
}
