package com.droidforge.inmobridge.glasses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherFocusTest {

    private fun machine(dock: Int = 4, apps: Int = 6): Pair<LauncherFocus, MutableList<LauncherFocus.State>> {
        val states = mutableListOf<LauncherFocus.State>()
        val m = LauncherFocus(dock, apps) { states.add(it) }
        return m to states
    }

    @Test
    fun `starts in dock with container hidden`() {
        val (m, _) = machine()
        assertEquals(LauncherFocus.Zone.DOCK, m.state.zone)
        assertFalse(m.state.appsVisible)
    }

    @Test
    fun `up reveals apps down hides and returns to dock`() {
        val (m, s) = machine()
        m.up()
        assertEquals(LauncherFocus.Zone.APPS, m.state.zone)
        assertTrue(m.state.appsVisible)
        m.down()
        assertEquals(LauncherFocus.Zone.DOCK, m.state.zone)
        assertFalse(m.state.appsVisible)
        // exactly two emissions
        assertEquals(2, s.size)
    }

    @Test
    fun `up while in apps is a no-op`() {
        val (m, s) = machine()
        m.up(); m.up()
        assertEquals(LauncherFocus.Zone.APPS, m.state.zone)
        assertEquals(1, s.size) // second up emitted nothing
    }

    @Test
    fun `left right cycle with wraparound in current zone`() {
        val (m, _) = machine(dock = 3)
        assertEquals(0, m.state.dockIndex)
        m.right()
        assertEquals(1, m.state.dockIndex)
        m.right()
        assertEquals(2, m.state.dockIndex)
        m.right()
        assertEquals(0, m.state.dockIndex) // wrapped
        m.left()
        assertEquals(2, m.state.dockIndex) // wrapped back
    }

    @Test
    fun `cycling in apps zone does not touch dock index`() {
        val (m, _) = machine()
        m.right()
        val dockIdx = m.state.dockIndex
        m.up()
        m.right(); m.right()
        assertEquals(dockIdx, m.state.dockIndex)
        assertEquals(2, m.state.appsIndex)
    }

    @Test
    fun `key codes map to transitions`() {
        val (m, _) = machine()
        assertTrue(m.onKey(LauncherFocus.KEY_UP))
        assertEquals(LauncherFocus.Zone.APPS, m.state.zone)
        assertTrue(m.onKey(LauncherFocus.KEY_DOWN))
        assertEquals(LauncherFocus.Zone.DOCK, m.state.zone)
        assertTrue(m.onKey(LauncherFocus.KEY_ENTER))
        assertFalse(m.onKey(9999))
    }

    @Test
    fun `updateCounts clamps indices`() {
        val (m, _) = machine(dock = 6, apps = 8)
        m.right(); m.right(); m.right(); m.right()  // dock idx 4
        m.up()                                       // apps idx 0
        m.right(); m.right(); m.right(); m.right(); m.right(); m.right()  // apps idx 6
        m.updateCounts(3, 2)                         // shrink
        assertEquals(2, m.state.dockIndex)
        assertEquals(1, m.state.appsIndex)
    }
}
