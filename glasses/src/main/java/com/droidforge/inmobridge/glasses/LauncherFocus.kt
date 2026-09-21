package com.droidforge.inmobridge.glasses

/**
 * Multi-zone focus state machine for the glasses launcher.
 *
 * Zones:
 *  - DOCK  : bottom strip, always visible. LEFT/RIGHT cycle items.
 *  - PANEL : a panel above the dock (apps grid or a dock item's submenu).
 *            LEFT/RIGHT cycle within the current row; panelVertical() moves
 *            between rows using row sizes supplied by the [rowSizes] provider
 *            (the Activity knows whether a grid or a one-row submenu is open).
 *  - PAIR  : modal pairing-help overlay above everything.
 *
 * Transitions (D-pad):
 *  - UP      : DOCK -> open the focused dock item's panel.
 *              PANEL -> PAIR overlay (reachable from any panel's top row).
 *  - DOWN    : PANEL -> DOCK (close). PAIR -> back to the panel below.
 *  - BACK    : same as DOWN (close one level).
 *  - LEFT/RIGHT : cycle within the current zone (PAIR: no-op).
 *  - ENTER   : consumed here; execution handled by the Activity (item lookup).
 *
 * The machine is pure state -> trivially unit-testable; the Activity binds it
 * to key events and view updates. No-ops emit nothing.
 */
class LauncherFocus(
    dockCount: Int,
    private val rowSizes: () -> IntArray,
    private val onState: (State) -> Unit,
) {
    enum class Zone { DOCK, PANEL, PAIR }

    data class State(
        val zone: Zone,
        val dockIndex: Int,
        /** Column within the open panel's current row. */
        val panelCol: Int = 0,
        /** Row within the open panel. */
        val panelRow: Int = 0,
        /** Cell count of the open panel's current row. */
        val panelRowSize: Int = 1,
        /** True once any panel has been opened (drives hint visibility). */
        val panelEverOpened: Boolean = false,
    ) {
        val appsVisible: Boolean get() = zone != Zone.DOCK
    }

    private val dockN = dockCount.coerceAtLeast(1)

    var state: State = State(Zone.DOCK, 0)
        private set

    /** UP: DOCK opens the focused dock item's panel; PANEL shows the pair overlay. */
    fun up() {
        when (state.zone) {
            Zone.DOCK -> {
                state = State(
                    zone = Zone.PANEL,
                    dockIndex = state.dockIndex,
                    panelCol = 0,
                    panelRow = 0,
                    panelRowSize = currentRowSize(0),
                    panelEverOpened = true,
                )
                onState(state)
            }
            Zone.PANEL -> {
                state = state.copy(zone = Zone.PAIR)
                onState(state)
            }
            Zone.PAIR -> Unit
        }
    }

    /** DOWN/BACK: close one level (PAIR -> PANEL -> DOCK). No-op in DOCK. */
    fun down() {
        when (state.zone) {
            Zone.PANEL -> {
                state = state.copy(
                    zone = Zone.DOCK,
                    panelCol = 0,
                    panelRow = 0,
                    panelRowSize = 1,
                )
                onState(state)
            }
            Zone.PAIR -> {
                state = state.copy(zone = Zone.PANEL)
                onState(state)
            }
            Zone.DOCK -> Unit
        }
    }

    /** BACK behaves like DOWN (one level of closing). */
    fun back() = down()

    fun left() = cycle(-1)

    fun right() = cycle(1)

    private fun cycle(dir: Int) {
        when (state.zone) {
            Zone.DOCK -> {
                state = state.copy(dockIndex = wrap(state.dockIndex + dir, dockN))
                onState(state)
            }
            Zone.PANEL -> {
                val size = currentRowSize(state.panelRow)
                state = state.copy(panelCol = wrap(state.panelCol + dir, size), panelRowSize = size)
                onState(state)
            }
            Zone.PAIR -> Unit
        }
    }

    /** UP/DOWN movement *within* the panel grid (row-size aware). */
    fun panelVertical(dir: Int): Boolean {
        if (state.zone != Zone.PANEL) return false
        val rows = rowSizes()
        if (rows.isEmpty()) return false
        val newRow = state.panelRow + dir
        if (newRow < 0 || newRow >= rows.size) return false
        state = state.copy(
            panelRow = newRow,
            panelCol = state.panelCol.coerceAtMost(rows[newRow] - 1),
            panelRowSize = rows[newRow],
        )
        onState(state)
        return true
    }

    private fun currentRowSize(row: Int): Int {
        val rows = rowSizes()
        return rows.getOrNull(row)?.coerceAtLeast(1) ?: 1
    }

    /** Flat cell index within the panel grid ([GRID_COLS]-wide rows). */
    fun focusedIndex(): Int = when (state.zone) {
        Zone.DOCK -> state.dockIndex
        else -> state.panelRow * GRID_COLS + state.panelCol
    }

    fun onKey(keyCode: Int): Boolean = when (keyCode) {
        KEY_UP -> { up(); true }
        KEY_DOWN -> { down(); true }
        KEY_LEFT -> { left(); true }
        KEY_RIGHT -> { right(); true }
        KEY_ENTER -> true  // execution handled by the Activity (needs item lookup)
        KEY_BACK -> { back(); true }
        else -> false
    }

    companion object {
        const val GRID_COLS = 4
        const val KEY_UP = 19
        const val KEY_DOWN = 20
        const val KEY_LEFT = 21
        const val KEY_RIGHT = 22
        const val KEY_ENTER = 23
        const val KEY_BACK = 4

        private fun wrap(i: Int, n: Int) = ((i % n) + n) % n
    }
}
