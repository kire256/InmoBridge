package com.droidforge.inmobridge.glasses

/**
 * Two-zone focus state machine for the glasses launcher.
 *
 * Zones:
 *  - DOCK   : bottom dock always visible; horizontal cycling among dock items.
 *  - APPS   : main container revealed; horizontal cycling among app items.
 *
 * Transitions (D-pad):
 *  - UP      : DOCK → APPS (reveal container). In APPS: no-op (already up).
 *  - DOWN    : APPS → DOCK and hide the container. In DOCK: no-op.
 *  - LEFT    : cycle -1 within the focused zone (wraps).
 *  - RIGHT   : cycle +1 within the focused zone (wraps).
 *  - ENTER   : execute focused item in the focused zone.
 *
 * The machine is pure state → trivially unit-testable; the Activity binds it to
 * key events and view updates.
 */
class LauncherFocus(
    dockCount: Int,
    appsCount: Int,
    private val onState: (State) -> Unit,
) {
    enum class Zone { DOCK, APPS }

    data class State(
        val zone: Zone,
        val dockIndex: Int,
        val appsIndex: Int,
        val appsVisible: Boolean,
    )

    private var dockN = dockCount.coerceAtLeast(1)
    private var appsN = appsCount.coerceAtLeast(1)

    var state: State = State(Zone.DOCK, 0, 0, false)
        private set

    /** New config from the phone: rebuild counts, keep indices valid. */
    fun updateCounts(dockCount: Int, appsCount: Int) {
        dockN = dockCount.coerceAtLeast(1)
        appsN = appsCount.coerceAtLeast(1)
        state = State(
            state.zone,
            state.dockIndex.coerceIn(0, dockN - 1),
            state.appsIndex.coerceIn(0, appsN - 1),
            state.appsVisible,
        )
        onState(state)
    }

    fun up() {
        if (state.zone == Zone.DOCK) {
            state = State(Zone.APPS, state.dockIndex, state.appsIndex, true)
            onState(state)
        }
    }

    fun down() {
        if (state.zone == Zone.APPS) {
            state = State(Zone.DOCK, state.dockIndex, state.appsIndex, false)
            onState(state)
        }
    }

    fun left() = cycle(-1)

    fun right() = cycle(1)

    private fun cycle(dir: Int) {
        state = if (state.zone == Zone.DOCK) {
            val i = ((state.dockIndex + dir) % dockN + dockN) % dockN
            state.copy(dockIndex = i)
        } else {
            val i = ((state.appsIndex + dir) % appsN + appsN) % appsN
            state.copy(appsIndex = i)
        }
        onState(state)
    }

    /** Focused item id within the current zone, for ENTER execution. */
    fun focusedIndex(zone: Zone = state.zone): Int =
        if (zone == Zone.DOCK) state.dockIndex else state.appsIndex

    fun onKey(keyCode: Int): Boolean = when (keyCode) {
        KEY_UP -> { up(); true }
        KEY_DOWN -> { down(); true }
        KEY_LEFT -> { left(); true }
        KEY_RIGHT -> { right(); true }
        KEY_ENTER -> true  // execution handled by the Activity (needs item lookup)
        else -> false
    }

    companion object {
        const val KEY_UP = 19
        const val KEY_DOWN = 20
        const val KEY_LEFT = 21
        const val KEY_RIGHT = 22
        const val KEY_ENTER = 23
    }
}
