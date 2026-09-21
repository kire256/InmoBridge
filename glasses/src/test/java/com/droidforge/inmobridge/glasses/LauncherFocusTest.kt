package com.droidforge.inmobridge.glasses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherFocusTest {

    /** Mutable row-size provider so tests can simulate grid vs submenu panels. */
    private class Rows(var sizes: IntArray = intArrayOf(1)) {
        operator fun invoke(): IntArray = sizes
    }

    private fun machine(
        dock: Int = 4,
        rows: Rows = Rows(intArrayOf(1)),
    ): Triple<LauncherFocus, Rows, MutableList<LauncherFocus.State>> {
        val states = mutableListOf<LauncherFocus.State>()
        val m = LauncherFocus(dock, { rows() }) { states.add(it) }
        return Triple(m, rows, states)
    }

    @Test
    fun `starts in dock with nothing visible`() {
        val (m, _, _) = machine()
        assertEquals(LauncherFocus.Zone.DOCK, m.state.zone)
        assertFalse(m.state.appsVisible)
        assertEquals(0, m.state.dockIndex)
    }

    @Test
    fun `up opens a panel for the focused dock item`() {
        val (m, rows, s) = machine()
        m.right() // focus dock item 1
        m.up()
        assertEquals(LauncherFocus.Zone.PANEL, m.state.zone)
        assertTrue(m.state.appsVisible)
        assertEquals(1, m.state.dockIndex) // dock selection preserved
        assertEquals(0, m.state.panelCol)
        assertEquals(0, m.state.panelRow)
        assertEquals(intArrayOf(1).size, rows().size) // provider was consultable
        assertTrue(m.state.panelEverOpened)
        assertEquals(2, s.size) // right + up, nothing else
    }

    @Test
    fun `up again from panel shows pair overlay and down unwinds one level`() {
        val (m, _, s) = machine()
        m.up()          // DOCK -> PANEL
        m.up()          // PANEL -> PAIR
        assertEquals(LauncherFocus.Zone.PAIR, m.state.zone)
        assertEquals(2, s.size)
        m.down()        // PAIR -> PANEL
        assertEquals(LauncherFocus.Zone.PANEL, m.state.zone)
        m.down()        // PANEL -> DOCK
        assertEquals(LauncherFocus.Zone.DOCK, m.state.zone)
        assertFalse(m.state.appsVisible)
        assertEquals(4, s.size)
    }

    @Test
    fun `back closes one level like down`() {
        val (m, _, _) = machine()
        m.up()
        m.back()
        assertEquals(LauncherFocus.Zone.DOCK, m.state.zone)
    }

    @Test
    fun `up and down in dock are the only panel openers and emit once`() {
        val (m, _, s) = machine()
        m.down() // no-op in dock
        assertEquals(LauncherFocus.Zone.DOCK, m.state.zone)
        assertEquals(0, s.size)
        m.up()
        assertEquals(1, s.size)
        m.up() // PANEL -> PAIR
        m.up() // no-op in PAIR
        assertEquals(LauncherFocus.Zone.PAIR, m.state.zone)
        assertEquals(2, s.size)
    }

    @Test
    fun `left right cycle dock with wraparound`() {
        val (m, _, _) = machine(dock = 3)
        m.right(); m.right(); m.right()
        assertEquals(0, m.state.dockIndex) // wrapped
        m.left()
        assertEquals(2, m.state.dockIndex) // wrapped back
    }

    @Test
    fun `panel left right wrap within current row size`() {
        val (m, rows, _) = machine(rows = Rows(intArrayOf(4, 2)))
        m.up() // panel row 0, size 4
        assertEquals(4, m.state.panelRowSize)
        m.left()
        assertEquals(3, m.state.panelCol) // wrapped 0 -> 3
        m.right(); m.right() // 0, 1
        assertEquals(1, m.state.panelCol)
        rows.sizes = intArrayOf(4, 2) // simulate switching row below via provider
        assertTrue(m.panelVertical(1))
        assertEquals(1, m.state.panelRow)
        assertEquals(2, m.state.panelRowSize)
        m.right()
        assertEquals(0, m.state.panelCol) // wrapped 1 -> 0 in a 2-wide row
    }

    @Test
    fun `panelVertical clamps column to the new row and refuses edges`() {
        val (m, rows, _) = machine(rows = Rows(intArrayOf(4, 1)))
        m.up()
        m.right(); m.right() // col 2
        assertTrue(m.panelVertical(1)) // row 1 has 1 cell -> col clamped to 0
        assertEquals(0, m.state.panelCol)
        assertFalse(m.panelVertical(1)) // already last row
        assertFalse(m.panelVertical(-1).not()) // up works (returns true)
        assertEquals(0, m.state.panelRow)
        rows.sizes = intArrayOf(1)
        m.panelVertical(1)
        assertTrue(m.panelVertical(1).not()) // refused at edge again
    }

    @Test
    fun `focusedIndex is flat grid math in panel and dock index in dock`() {
        val (m, rows, _) = machine(rows = Rows(intArrayOf(4, 4)))
        m.up()
        m.panelVertical(1)
        m.right(); m.right()
        assertEquals(1 * LauncherFocus.GRID_COLS + 2, m.focusedIndex())
        m.down()
        m.right()
        assertEquals(1, m.focusedIndex())
    }

    @Test
    fun `key codes map to transitions including back`() {
        val (m, _, _) = machine()
        assertTrue(m.onKey(LauncherFocus.KEY_UP))
        assertEquals(LauncherFocus.Zone.PANEL, m.state.zone)
        assertTrue(m.onKey(LauncherFocus.KEY_ENTER)) // consumed, no crash
        assertTrue(m.onKey(LauncherFocus.KEY_BACK))
        assertEquals(LauncherFocus.Zone.DOCK, m.state.zone)
        assertTrue(m.onKey(LauncherFocus.KEY_DOWN).not().not()) // consumed no-op
        assertFalse(m.onKey(9999))
    }
}
