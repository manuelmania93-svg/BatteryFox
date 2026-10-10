package com.batteryfox.app.core.service

import org.junit.Assert.*
import org.junit.Test

class MonitorStateTest {
    @Test fun lifecycleOwnsStateAndRepeatedStopIsSafe() {
        MonitorState.stopped()
        assertFalse(MonitorState.isRunning.value)
        MonitorState.started()
        assertTrue(MonitorState.isRunning.value)
        MonitorState.stopped()
        MonitorState.stopped()
        assertFalse(MonitorState.isRunning.value)
    }
}
