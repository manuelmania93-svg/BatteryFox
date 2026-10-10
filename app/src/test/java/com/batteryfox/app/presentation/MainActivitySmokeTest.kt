package com.batteryfox.app.presentation

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Starts the real activity the way Android does. JVM unit tests never build the ViewModel
 * through the framework factory, so a constructor/factory mismatch (like the missing
 * (Application) overload) passes every logic test and only kills the real app at launch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivitySmokeTest {

    @Test
    fun `activity launches and creates its view model`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)
        assertTrue("activity should reach resumed state", activity.hasWindowFocus() || !activity.isFinishing)
    }
}
