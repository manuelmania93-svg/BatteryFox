package com.batteryfox.app.core.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Same-process service lifecycle, never persisted: a killed process starts with stopped state. */
object MonitorState {
    private val running = MutableStateFlow(false)
    val isRunning = running.asStateFlow()
    fun started() { running.value = true }
    fun stopped() { running.value = false }
}
