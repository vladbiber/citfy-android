package com.ctify.player

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.ctify.player.player.PlayerViewModel
import com.ctify.player.ui.CtifyRoot
import com.ctify.player.ui.theme.Appearance
import com.ctify.player.ui.theme.CtifyTheme
import com.ctify.player.ui.theme.isAppInDarkTheme
import com.ctify.player.youtube.YtIntake

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* notification permission result — playback works regardless */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Appearance.init(this)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        YtIntake.offer(intent?.getStringExtra(Intent.EXTRA_TEXT))
        setContent {
            CtifyTheme {
                val dark = isAppInDarkTheme()
                // bar icons follow the app's theme, which can differ from the system's
                LaunchedEffect(dark) {
                    val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CtifyRoot(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        YtIntake.offer(intent.getStringExtra(Intent.EXTRA_TEXT))
    }
}
