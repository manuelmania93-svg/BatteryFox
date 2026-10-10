package com.batteryfox.app.core.telemetry

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/** Subscription and ticker exist only for the lifetime of the collecting lifecycle. */
fun <T : Any> liveTelemetryFlow(
    intervalMs: Long,
    subscribe: ((T) -> Unit) -> (() -> Unit)
) = callbackFlow {
    val latest = java.util.concurrent.atomic.AtomicReference<T?>(null)
    val unsubscribe = subscribe { reading ->
        latest.set(reading)
        trySend(reading)
    }
    val ticker = launch {
        while (true) {
            delay(intervalMs)
            latest.get()?.let { trySend(it) }
        }
    }
    awaitClose {
        ticker.cancel()
        unsubscribe()
    }
}.buffer(Channel.CONFLATED)
