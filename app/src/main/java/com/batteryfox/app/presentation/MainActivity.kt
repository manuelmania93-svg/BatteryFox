package com.batteryfox.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.batteryfox.app.presentation.theme.BatteryFoxTheme
import com.batteryfox.app.presentation.ui.DashboardScreen
import com.batteryfox.app.presentation.viewmodel.BatteryViewModel

class MainActivity : ComponentActivity() {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 

    private val viewModel: BatteryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        super.onCreate(savedInstanceState)
        setContent {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            BatteryFoxTheme {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                DashboardScreen(viewModel = viewModel)
            
        
    

    override fun onResume() {.git{,hub,ignore},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        super.onResume()
        viewModel.refreshTelemetry()
    

