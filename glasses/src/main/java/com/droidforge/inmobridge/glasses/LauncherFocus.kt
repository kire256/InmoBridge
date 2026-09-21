package com.droidforge.inmobridge.glasses

/**
 * Two-zone focus state machine for the glasses launcher with a
 * geometry-injectable panel (apps grid or a one-row submenu).
 *
 * Zones:
 *  - DOCK  : bottom strip, always visible. LEFT/RIGHT cycle items.
 *  - PANEL : the open panel above the dock. The Activity pushes the panel's
 *            shape via [setPanelGeometry] (rows x cols, item count). The apps
 *            grid is COLUMN-MAJOR (4 fixed rows; index = col*rows + row) so
 *            alphabetical order runs DOWN each column, then to the next
 *            column — the panel scrolls horizontally. LEFT/RIGHT move between
 *            columns; UP/DOWN between rows (UP at the top row wraps to the
 *            bottom; DOWN at the bottom edge reports false so the Activity
 *            can close the panel).
 *
 * Transitions (D-pad):
 *  - UP      : DOCK -> open the focused dock item's panel. PANEL -> row move
 *              (wrap at top).
 *  - DOWN    : DOCK -> no-op. PANEL -> row move; at the bottom edge closes.
 *  - BACK    : closes the panel.
 *  - LEFT/RIGHT : cycle dock items (DOCK) or panel columns/items (PANEL).
 *  - ENTER   : consumed; execution handled by the Activity (item lookup).
 *
 * Pure state -> trivially unit-testable; the Activity binds it to keys/views.
 */
class LauncherFocus(
    dockCount: Int,
    private val onState: (State) -> Unit,
) {
    enum class Zone { DOCK, PANEL }

    data class State(
        val zone: Zone,
        val dockIndex: Int,
        val panelRow: Int = 0,
        val panelCol: Int = 0,
        /** True once any panel has been opened (drives hint visibility). */
        val panelEverOpened: Boolean = false,
    ) {
        val appsVisible: Boolean get() = zone != Zone.DOCK
    }

    private val dockN = dockCount.coerceAtLeast(1)

    // Panel geometry, pushed by the Activity whenever the open panel changes.
    private var panelRows = 1
    private var panelCols = 1
    private var panelCount = 1

    var state: State = State(Zone.DOCK, 0)
        private set

    /**
     * Push the open panel's shape. Call before reading focus positions after
     * the panel content changes. Silently clamps the current cell (no emit).
     */
    fun setPanelGeometry(rows: Int, cols: Int, count: Int) {
        panelRows = rows.coerceAtLeast(1)
        panelCols = cols.coerceAtLeast(1)
        panelCount = count.coerceAtLeast(1)
        if (state.zone == Zone.PANEL) {
            val last = panelCount - 1
            val col = state.panelCol.coerceIn(0, panelCols - 1)
            val row = state.panelRow.coerceIn(0, lastValidRowInCol(col))
            state = if (cellIndex(row, col) > last) {
                state.copy(panelRow = last % panelRows, panelCol = last / panelRows)
            } else {
                state.copy(panelRow = row, panelCol = col)
            }
        }
    }

    /** Column-major flat index of the focused grid cell. */
    fun focusedGridIndex(): Int = cellIndex(state.panelRow, state.panelCol)

    /** Focused item within a one-row submenu. */
    fun focusedSubmenuIndex(): Int = state.panelCol

    /** UP from DOCK opens the focused dock item's panel. No-op in PANEL. */
    fun up() {
        if (state.zone == Zone.DOCK) {
            state = State(
                zone = Zone.PANEL,
                dockIndex = state.dockIndex,
                panelRow = 0,
                panelCol = 0,
                panelEverOpened = true,
            )
            onState(state)
        }
    }

    /** DOWN/BACK closes the panel. No-op in DOCK. */
    fun down() {
        if (state.zone == Zone.PANEL) {
            state = State(
                zone = Zone.DOCK,
                dockIndex = state.dockIndex,
                panelEverOpened = true,
            )
            onState(state)
        }
    }

    /** BACK behaves like DOWN. */
    fun back() = down()

    fun left() = cycle(-1)

    fun right() = cycle(1)

    /**
     * UP/DOWN between rows of the panel grid. UP at the top row wraps to the
     * bottom; DOWN at the bottom edge returns false (Activity closes the
     * panel). Single-row panels always return false.
     */
    fun panelVertical(dir: Int): Boolean {
        if (state.zone != Zone.PANEL || panelRows <= 1) return false
        val col = state.panelCol.coerceIn(0, panelCols - 1)
        val lastValidRow = lastValidRowInCol(col)
        val row = state.panelRow.coerceIn(0, lastValidRow)
        val newRow = if (dir < 0) {
            if (row == 0) lastValidRow else row - 1 // top wraps to bottom
        } else {
            if (row >= lastValidRow) return false   // bottom edge -> close
            row + 1
        }
        state = state.copy(panelRow = newRow, panelCol = col)
        onState(state)
        return true
    }

    private fun cycle(dir: Int) {
        when (state.zone) {
            Zone.DOCK -> {
                state = state.copy(dockIndex = wrap(state.dockIndex + dir, dockN))
                onState(state)
            }
            Zone.PANEL -> {
                val col = wrap(state.panelCol + dir, panelCols)
                val row = state.panelRow.coerceIn(0, lastValidRowInCol(col))
                state = state.copy(panelRow = row, panelCol = col)
                onState(state)
            }
        }
    }

    private fun cellIndex(row: Int, col: Int) = col * panelRows + row

    private fun lastValidRowInCol(col: Int): Int {
        val last = panelCount - 1
        return if (col < last / panelRows) panelRows - 1 else last % panelRows
    }

    fun onKey(keyCode: Int): Boolean = when (keyCode) {
        KEY_UP -> { if (!panelVertical(-1)) up(); true }
        KEY_DOWN -> { if (!panelVertical(1)) down(); true }
        KEY_LEFT -> { left(); true }
        KEY_RIGHT -> { right(); true }
        KEY_ENTER, KEY_ENTER_ALT -> true // execution handled by the Activity
        KEY_BACK -> { back(); true }
        else -> false
    }

    companion object {
        /** Fixed row count of the apps grid. */
        const val GRID_ROWS = 4
        const val KEY_UP = 19
        const val KEY_DOWN = 20
        const val KEY_LEFT = 21
        const val KEY_RIGHT = 22
        const val KEY_ENTER = 23        // KEYCODE_DPAD_CENTER
        const val KEY_ENTER_ALT = 66    // KEYCODE_ENTER
        const val KEY_BACK = 4

        private fun wrap(i: Int, n: Int) = ((i % n) + n) % n
    }
}
