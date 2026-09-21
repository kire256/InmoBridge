package com.droidforge.inmobridge.glasses

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.droidforge.inmobridge.core.CardSpec
import com.droidforge.inmobridge.core.DockItem
import com.droidforge.inmobridge.glasses.BuildConfig

/**
 * What the open panel above the dock is showing.
 */
sealed class PanelUi {
    /** Apps tab: a horizontally scrolling strip, [LauncherFocus.GRID_ROWS] fixed rows. */
    class Grid(val items: List<DockItem>) : PanelUi()

    /** A dock item's submenu: one horizontal row of actions. */
    class Row(val title: String, val items: List<DockItem>) : PanelUi()
}

/**
 * Single custom view that renders the entire launcher surface for the glasses:
 * opaque root, horizontally scrolling app strip or submenu above the dock,
 * dock at the bottom baseline, cards on top. One canvas pass, no Compose.
 */
class LauncherView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var dockItems: List<DockItem> = emptyList()
        set(value) { field = value; invalidate() }

    /** Open panel above the dock, or null when closed (dock-only mode). */
    var panel: PanelUi? = null
        set(value) { field = value; invalidate() }

    /** Current focus state pushed by [LauncherFocus]. */
    var focusState: LauncherFocus.State = LauncherFocus.State(LauncherFocus.Zone.DOCK, 0)
        set(value) { field = value; invalidate() }

    /** Transient card overlay (AI replies, alerts). */
    var card: CardSpec? = null
        set(value) { field = value; invalidate() }

    private val dockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000.toInt() }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22000000.toInt() }
    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAA39D2C0.toInt() }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC101418.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        isFakeBoldText = true
    }
    private val dimTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt()
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val w = width.toFloat()
        val d = density

        // ---- Version indicator (top-left) ---- Draws once, cheap.
        val verPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt()
            textSize = 14f * d
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("v${BuildConfig.VERSION_NAME}", 12f * d, 24f * d, verPaint)

        // ---- Panel above the dock (app strip / submenu) ----
        panel?.let { p ->
            val dockH = 52f * d
            val top = 40f * d
            val bottom = height - dockH - 14f * d
            canvas.drawRoundRect(16f * d, top, w - 16f * d, bottom, 12f * d, 12f * d, dockPaint)
            val save = canvas.save()
            canvas.clipRect(16f * d, top, w - 16f * d, bottom)
            when (p) {
                is PanelUi.Grid -> drawGrid(canvas, p.items, top, bottom, d)
                is PanelUi.Row -> drawRow(canvas, p, top, bottom, d)
            }
            canvas.restoreToCount(save)
        }

        // ---- First-run hint (until the first panel open) ----
        if (!focusState.panelEverOpened && focusState.zone == LauncherFocus.Zone.DOCK) {
            val hint = "▲ open   ◀ ▶ choose   ● select"
            canvas.drawText(hint, w / 2f, height - 70f * d, dimTextPaint)
        }

        // ---- Bottom dock ----
        if (dockItems.isNotEmpty()) {
            val itemW = 84f * d
            val dockH = 52f * d
            val totalW = dockItems.size * itemW
            val x0 = (w - totalW) / 2f
            val y0 = height - dockH - 10f * d
            canvas.drawRoundRect(x0 - 8f * d, y0, x0 + totalW + 8f * d, y0 + dockH, 12f * d, 12f * d, dockPaint)
            dockItems.forEachIndexed { i, item ->
                val cx = x0 + i * itemW + itemW / 2f
                val focused = focusState.zone == LauncherFocus.Zone.DOCK && i == focusState.dockIndex
                if (focused) {
                    canvas.drawRoundRect(
                        x0 + i * itemW + 4f * d, y0 + 6f * d,
                        x0 + (i + 1) * itemW - 4f * d, y0 + dockH - 6f * d,
                        10f * d, 10f * d, focusPaint
                    )
                }
                val tw = iconPaint.measureText(item.icon)
                canvas.drawText(item.icon, cx - tw / 2f, y0 + 30f * d, if (focused) titlePaint else textPaint)
                val lw = dimTextPaint.measureText(item.label)
                canvas.drawText(item.label, cx - lw / 2f, y0 + 46f * d, dimTextPaint)
            }
        }

        // ---- Card overlay ----
        card?.let { c ->
            val cw = w - 64f * d
            val lineH = 36f * d
            val h = 24f * d + 44f * d + c.body.size * lineH
            val x0 = 32f * d
            val y0 = (height - h) / 2f
            canvas.drawRoundRect(x0, y0, x0 + cw, y0 + h, 14f * d, 14f * d, cardPaint)
            val accentPaint = Paint(focusPaint).apply {
                color = when (c.style) {
                    CardSpec.STYLE_AI -> 0xAA9C6DE8.toInt()
                    CardSpec.STYLE_WARN -> 0xAAE85D5D.toInt()
                    else -> 0xAA39D2C0.toInt()
                }
            }
            canvas.drawRoundRect(x0, y0, x0 + 6f * d, y0 + h, 3f * d, 3f * d, accentPaint)
            canvas.drawText(c.title, x0 + 20f * d, y0 + 40f * d, titlePaint)
            c.body.forEachIndexed { i, line ->
                canvas.drawText(line, x0 + 20f * d, y0 + 76f * d + i * lineH, textPaint)
            }
        }
    }

    /**
     * Horizontal app strip: GRID_ROWS fixed rows, columns scroll to keep the
     * focused column on screen. Alphabetical order runs DOWN each column then
     * to the next (column-major, matching [LauncherFocus.focusedGridIndex]).
     */
    private fun drawGrid(canvas: Canvas, items: List<DockItem>, top: Float, bottom: Float, d: Float) {
        val w = width.toFloat()
        val rows = LauncherFocus.GRID_ROWS
        val visibleCols = VISIBLE_COLS
        val totalCols = ((items.size + rows - 1) / rows).coerceAtLeast(1)

        val padSide = 24f * d
        val gap = 10f * d
        val cellW = (w - padSide * 2 - gap * (visibleCols - 1)) / visibleCols
        val availH = bottom - top - 20f * d
        val cellH = availH / rows
        val yTop = top + 10f * d

        // Scroll window: keep the focused column visible (whole columns).
        val focusCol = focusState.panelCol
        val firstCol = (focusCol - (visibleCols - 1)).coerceAtLeast(0)
            .coerceAtMost((totalCols - visibleCols).coerceAtLeast(0))

        items.forEachIndexed { idx, item ->
            val col = idx / rows
            if (col < firstCol || col >= firstCol + visibleCols + 1) return@forEachIndexed
            val row = idx % rows
            val x = padSide + (col - firstCol) * (cellW + gap)
            val y = yTop + row * (cellH + 6f * d)
            val selected = focusState.zone == LauncherFocus.Zone.PANEL &&
                col == focusCol && row == focusState.panelRow
            drawCell(canvas, item, x, y, cellW, cellH, selected, d)
        }

        // Scroll position indicator (thin bar, bottom of the panel)
        if (totalCols > visibleCols) {
            val barW = w - padSide * 2
            val frac = visibleCols.toFloat() / totalCols
            val pos = firstCol.toFloat() / (totalCols - visibleCols).coerceAtLeast(1)
            val trackY = bottom - 6f * d
            canvas.drawRoundRect(padSide, trackY, padSide + barW, trackY + 3f * d,
                2f * d, 2f * d, cellPaint)
            canvas.drawRoundRect(padSide + pos * barW * (1f - frac), trackY,
                padSide + pos * barW * (1f - frac) + barW * frac, trackY + 3f * d,
                2f * d, 2f * d, focusPaint)
        }
    }

    private fun drawRow(canvas: Canvas, p: PanelUi.Row, top: Float, bottom: Float, d: Float) {
        val w = width.toFloat()
        val items = p.items
        if (items.isEmpty()) return
        canvas.drawText(p.title, w / 2f, top + 30f * d, dimTextPaint)

        val gap = 12f * d
        val maxCellW = 170f * d
        val cellW = ((w - 56f * d - gap * (items.size - 1)) / items.size).coerceAtMost(maxCellW)
        val cellH = (bottom - top - 64f * d).coerceAtMost(120f * d)
        val totalW = items.size * cellW + gap * (items.size - 1)
        val x0 = (w - totalW) / 2f
        val y0 = top + 44f * d

        items.forEachIndexed { i, item ->
            val selected = focusState.zone == LauncherFocus.Zone.PANEL &&
                i == focusState.panelCol
            drawCell(canvas, item, x0 + i * (cellW + gap), y0, cellW, cellH, selected, d)
        }
    }

    private fun drawCell(
        canvas: Canvas,
        item: DockItem,
        x: Float,
        y: Float,
        cellW: Float,
        cellH: Float,
        selected: Boolean,
        d: Float,
    ) {
        if (selected) {
            canvas.drawRoundRect(x + 2f * d, y + 2f * d, x + cellW - 2f * d, y + cellH - 2f * d,
                10f * d, 10f * d, focusPaint)
        } else {
            canvas.drawRoundRect(x, y, x + cellW, y + cellH, 10f * d, 10f * d, cellPaint)
        }
        val cx = x + cellW / 2f
        val iconY = y + cellH * 0.45f
        canvas.drawText(item.icon, cx, iconY, iconPaint)
        val labelPaint = if (selected) {
            Paint(textPaint).apply { textSize = 20f * d; textAlign = Paint.Align.CENTER }
        } else {
            Paint(dimTextPaint).apply { textSize = 20f * d; textAlign = Paint.Align.CENTER }
        }
        canvas.drawText(item.label, cx, y + cellH - 10f * d, labelPaint)
    }

    companion object {
        /** Columns visible at once in the apps strip. */
        const val VISIBLE_COLS = 4
    }
}
