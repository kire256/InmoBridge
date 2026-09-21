package com.droidforge.inmobridge.glasses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherFocusTest {

    private fun machine(
        dock: Int = 6,
    ): Pair<LauncherFocus, MutableList<LauncherFocus.State>> {
        val states = mutableListOf<LauncherFocus.State>()
        val m = LauncherFocus(dock) { states.add(it) }
        return m to states
    }

    @Test
    fun `starts in dock with nothing visible`() {
        val (m, _) = machine()
        assertEquals(LauncherFocus.Zone.DOCK, m.state.zone)
        assertFalse(m.state.appsVisible)
        assertEquals(0, m.state.dockIndex)
    }

    @Test
    fun `up opens a panel for the focused dock item`() {
        val (m, s) = machine()
        m.right() // dock item 1
        m.up()
        assertEquals(LauncherFocus.Zone.PANEL, m.state.zone)
        assertTrue(m.state.appsVisible)
        assertEquals(1, m.state.dockIndex) // dock selection preserved
        assertTrue(m.state.panelEverOpened)
        assertEquals(2, s.size) // right + up only
    }

    @Test
    fun `dock cycling wraps both directions`() {
        val (m, _) = machine(dock = 3)
        m.right(); m.right(); m.right()
        assertEquals(0, m.state.dockIndex) // wrapped forward
        m.left()
        assertEquals(2, m.state.dockIndex) // wrapped back
    }

    @Test
    fun `grid is column-major with 4 fixed rows`() {
        val (m, _) = machine()
        m.up()
        // 20 items, 4 rows -> 5 columns
        m.setPanelGeometry(4, 5, 20)
        assertEquals(0, m.focusedGridIndex())
        m.panelVertical(1)                  // row 1, col 0
        assertEquals(1, m.focusedGridIndex())
        m.right()                           // col 1, row stays 1
        assertEquals(1 * 4 + 1, m.focusedGridIndex())
        m.panelVertical(-1)                 // row 0
        m.panelVertical(-1)                 // wraps to bottom row (3)
        assertEquals(1 * 4 + 3, m.focusedGridIndex())
    }

    @Test
    fun `up at top row wraps to bottom of same column`() {
        val (m, _) = machine()
        m.up()
        m.setPanelGeometry(4, 5, 20)
        assertTrue(m.panelVertical(-1)) // row 0 -> row 3
        assertEquals(3, m.state.panelRow)
        assertEquals(0, m.state.panelCol)
    }

    @Test
    fun `down at bottom edge closes the panel`() {
        val (m, _) = machine()
        m.up()
        m.setPanelGeometry(4, 5, 20)
        m.panelVertical(1); m.panelVertical(1); m.panelVertical(1) // row 3
        assertFalse(m.panelVertical(1)) // bottom edge -> refuse
        m.down()                        // -> closes
        assertEquals(LauncherFocus.Zone.DOCK, m.state.zone)
        assertFalse(m.state.appsVisible)
    }

    @Test
    fun `column move clamps row to the new column's last valid row`() {
        val (m, _) = machine()
        m.up()
        m.setPanelGeometry(4, 5, 18) // col 4 holds only items 16,17 (rows 0,1)
        m.panelVertical(1); m.panelVertical(1) // row 2, col 0
        m.right() // -> col 1 (full column, row 2 valid)
        assertEquals(1, m.state.panelCol)
        assertEquals(2, m.state.panelRow)
        m.right() // -> col 2
        m.right() // -> col 3
        m.right() // -> col 4: rows 2,3 don't exist there
        assertEquals(4, m.state.panelCol)
        assertEquals(1, m.state.panelRow)
        assertEquals(17, m.focusedGridIndex())
    }

    @Test
    fun `setPanelGeometry clamps an out-of-range cell when shrinking`() {
        val (m, _) = machine()
        m.up()
        m.setPanelGeometry(4, 5, 20)
        m.panelVertical(1); m.panelVertical(1); m.panelVertical(1) // row 3
        m.right(); m.right(); m.right(); m.right()                 // col 4
        assertEquals(19, m.focusedGridIndex())
        m.setPanelGeometry(4, 2, 6)                                 // shrink to 6 items
        assertEquals(5, m.focusedGridIndex())                       // snapped to last cell
        assertEquals(1, m.state.panelCol)
        assertEquals(1, m.state.panelRow)
    }

    @Test
    fun `single-row submenu uses columns and closes with down`() {
        val (m, _) = machine()
        m.up()
        m.setPanelGeometry(1, 3, 3)
        assertFalse(m.panelVertical(1)) // single row: vertical always refuses
        assertFalse(m.panelVertical(-1))
        m.right(); m.right(); m.right() // wrap 2 -> 0
        assertEquals(0, m.focusedSubmenuIndex())
        m.down()
        assertEquals(LauncherFocus.Zone.DOCK, m.state.zone)
    }

    @Test
    fun `back closes the panel like down`() {
        val (m, _) = machine()
        m.up()
        m.back()
        assertEquals(LauncherFocus.Zone.DOCK, m.state.zone)
    }

    @Test
    fun `key codes map to transitions`() {
        val (m, _) = machine()
        assertTrue(m.onKey(LauncherFocus.KEY_UP))          // open panel
        assertEquals(LauncherFocus.Zone.PANEL, m.state.zone)
        assertTrue(m.onKey(LauncherFocus.KEY_BACK))        // close
        assertEquals(LauncherFocus.Zone.DOCK, m.state.zone)
        assertTrue(m.onKey(LauncherFocus.KEY_ENTER))       // consumed
        assertTrue(m.onKey(LauncherFocus.KEY_ENTER_ALT))   // consumed
        assertFalse(m.onKey(9999))
    }
}
