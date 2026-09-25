package com.batteryfox.app.presentation

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.batteryfox.app.presentation.theme.BatteryFoxTheme
import com.batteryfox.app.presentation.ui.DashboardScreen
import com.batteryfox.app.presentation.viewmodel.BatteryViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: BatteryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle incoming shared bug report ZIP on cold launch
        handleIncomingShareIntent(intent)

        setContent {
            BatteryFoxTheme {
                DashboardScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle incoming shared bug report ZIP while app is in background
        handleIncomingShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshTelemetry()
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.action == Intent.ACTION_SEND) {
            val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }

            streamUri?.let { uri ->
                viewModel.parseBugReportUri(uri)
            }
        }
    }
}
