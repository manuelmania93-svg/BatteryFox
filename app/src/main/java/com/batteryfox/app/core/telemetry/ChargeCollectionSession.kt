package com.batteryfox.app.core.telemetry

/** A missing counter is a boundary, even if no valid sample can be persisted for that reading. */
class ChargeCollectionSession(private val newId: () -> String) {
    private var id = newId()
    private var wasAvailable: Boolean? = null

    fun observe(counterAvailable: Boolean): String {
        if (wasAvailable == true && !counterAvailable) id = newId()
        wasAvailable = counterAvailable
        return id
    }
}
