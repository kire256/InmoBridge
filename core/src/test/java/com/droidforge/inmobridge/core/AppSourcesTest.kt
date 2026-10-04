package com.droidforge.inmobridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSourcesTest {

    @Test
    fun `merge unifies launchables and observed notifiers`() {
        val merged = AppSources.merge(
            launchables = listOf(("Facebook" to "com.facebook.katana"), ("Calculator" to "com.calc")),
            observed = setOf("com.facebook.katana", "com.background.thing"),
        )
        // background-only notifier appears
        assertTrue(merged.any { it.pkg == "com.background.thing" && it.observed })
        // launchable that also notifies is flagged
        assertTrue(merged.first { it.pkg == "com.facebook.katana" }.observed)
        // launchable that never notified is present but unflagged
        assertTrue(merged.first { it.pkg == "com.calc" }.let { !it.observed })
        // observed notifiers sort first
        assertEquals("com.background.thing", merged.first().pkg)
        // alphabetical within groups
        val launchableOnly = merged.filter { !it.observed }.map { it.label.lowercase() }
        assertEquals(launchableOnly.sorted(), launchableOnly)
    }
}
