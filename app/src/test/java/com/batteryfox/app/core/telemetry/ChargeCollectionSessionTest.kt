package com.batteryfox.app.core.telemetry

import org.junit.Assert.*
import org.junit.Test

class ChargeCollectionSessionTest {
    private var next = 0
    private fun tracker() = ChargeCollectionSession { (++next).toString() }
    @Test fun stableWhileCounterAvailable() {
        val tracker = tracker()
        assertEquals(tracker.observe(true), tracker.observe(true))
    }
    @Test fun missingCounterBreaksWindowOnNextValidReading() {
        val tracker = tracker()
        val before = tracker.observe(true)
        tracker.observe(false)
        assertNotEquals(before, tracker.observe(true))
    }
    @Test fun repeatedMissingReadingsDoNotKeepRotating() {
        val tracker = tracker()
        tracker.observe(true)
        val missing = tracker.observe(false)
        assertEquals(missing, tracker.observe(false))
        assertEquals(missing, tracker.observe(true))
    }
    @Test fun initiallyUnavailableDoesNotPretendPreviousEvidence() {
        val tracker = tracker()
        assertEquals(tracker.observe(false), tracker.observe(true))
    }
}
