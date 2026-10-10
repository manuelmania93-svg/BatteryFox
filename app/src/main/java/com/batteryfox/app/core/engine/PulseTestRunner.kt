package com.batteryfox.app.core.engine

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay

/** A fresh safety snapshot is required before, during and after every load pulse. */
data class PulseSafetyState(val soc: Int?, val temperatureC: Float?, val plugged: Boolean?, val throttled: Boolean) {
    fun rejection(): String? = when {
        plugged != false -> "Disconnect the charger before running this experimental test."
        soc == null || soc !in 20..90 -> "SoC must stay between 20% and 90%."
        temperatureC == null || !temperatureC.isFinite() || temperatureC !in 10f..45f -> "Battery temp must stay between 10 C and 45 C."
        throttled -> "Device throttled mid-test."
        else -> null
    }
}

class PulseTestRunner(
    private val safety: () -> PulseSafetyState,
    private val startLoad: () -> (() -> Unit),
    private val pause: suspend (Long) -> Unit = { delay(it) }
) {
    fun checkSafety() { safety().rejection()?.let { throw IllegalStateException(it) } }
    suspend fun <T> pulse(sample: () -> T): T {
        currentCoroutineContext().ensureActive()
        checkSafety()
        val stop = startLoad()
        try {
            repeat(18) {
                pause(100)
                currentCoroutineContext().ensureActive()
                checkSafety()
            }
            return sample()
        } finally {
            // Never suspend cleanup: cancellation cannot skip stopping the CPU workers.
            stop()
        }
    }
}
