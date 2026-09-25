package com.batteryfox.app.presentation

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
        setContent {
            BatteryFoxTheme {
                DashboardScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshTelemetry()
    }
}
