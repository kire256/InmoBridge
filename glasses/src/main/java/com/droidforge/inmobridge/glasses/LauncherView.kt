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
 * Single custom view that renders the entire launcher surface for the glasses:
 * transparent root, app container at top, dock at bottom baseline, cards on top.
 * One canvas pass, no Compose runtime — the glasses UI stack is unknown, so we
 * keep the dependency surface at zero beyond :core.
 */
class LauncherView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var dockItems: List<DockItem> = emptyList()
        set(value) { field = value; invalidate() }

    var appItems: List<DockItem> = emptyList()
        set(value) { field = value; invalidate() }

    /** Current focus state pushed by [LauncherFocus]. */
    var focusState: LauncherFocus.State =
        LauncherFocus.State(LauncherFocus.Zone.DOCK, 0, 0, false)
        set(value) { field = value; invalidate() }

    /** Transient card overlay (AI replies, alerts). */
    var card: CardSpec? = null
        set(value) { field = value; invalidate() }

    private val dockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000.toInt() }
    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAA39D2C0.toInt() }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC101418.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        isFakeBoldText = true
    }
    private val dimTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt()
        textSize = 22f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density

        // ---- Version indicator (top-left) ---- Draws once, cheap.
        val verPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt()
            textSize = 14f * density
        }
        canvas.drawText("v${BuildConfig.VERSION_NAME}", 12f * density, 24f * density, verPaint)

        // ---- App container (top) ----
        if (focusState.appsVisible) {
            val top = 16f * density
            val itemH = 44f * density
            val w = width.toFloat()
            val listH = appItems.size * itemH + 16f * density
            canvas.drawRoundRect(16f * density, top, w - 16f * density, top + listH, 12f * density, 12f * density, dockPaint)
            appItems.forEachIndexed { i, item ->
                val y = top + 8f * density + i * itemH
                if (focusState.zone == LauncherFocus.Zone.APPS && i == focusState.appsIndex) {
                    canvas.drawRoundRect(
                        24f * density, y, w - 24f * density, y + itemH - 6f * density,
                        8f * density, 8f * density, focusPaint
                    )
                }
                canvas.drawText("${item.icon}  ${item.label}", 36f * density, y + 30f * density, textPaint)
            }
        }

        // ---- Bottom dock ----
        if (dockItems.isNotEmpty()) {
            val itemW = 84f * density
            val dockH = 52f * density
            val totalW = dockItems.size * itemW
            val x0 = (width - totalW) / 2f
            val y0 = height - dockH - 10f * density
            canvas.drawRoundRect(x0 - 8f * density, y0, x0 + totalW + 8f * density, y0 + dockH, 12f * density, 12f * density, dockPaint)
            dockItems.forEachIndexed { i, item ->
                val cx = x0 + i * itemW + itemW / 2f
                val focused = focusState.zone == LauncherFocus.Zone.DOCK && i == focusState.dockIndex
                if (focused) {
                    canvas.drawRoundRect(
                        x0 + i * itemW + 4f * density, y0 + 6f * density,
                        x0 + (i + 1) * itemW - 4f * density, y0 + dockH - 6f * density,
                        10f * density, 10f * density, focusPaint
                    )
                }
                val tw = textPaint.measureText(item.icon)
                canvas.drawText(item.icon, cx - tw / 2f, y0 + 30f * density, if (focused) titlePaint else textPaint)
                val lw = dimTextPaint.measureText(item.label)
                canvas.drawText(item.label, cx - lw / 2f, y0 + 46f * density, dimTextPaint)
            }
        }

        // ---- Card overlay ----
        card?.let { c ->
            val w = width - 64f * density
            val lineH = 36f * density
            val h = 24f * density + 44f * density + c.body.size * lineH
            val x0 = 32f * density
            val y0 = (height - h) / 2f
            canvas.drawRoundRect(x0, y0, x0 + w, y0 + h, 14f * density, 14f * density, cardPaint)
            when (c.style) {
                CardSpec.STYLE_AI -> focusPaint.color = 0xAA9C6DE8.toInt()
                CardSpec.STYLE_WARN -> focusPaint.color = 0xAAE85D5D.toInt()
                else -> focusPaint.color = 0xAA39D2C0.toInt()
            }
            canvas.drawRoundRect(x0, y0, x0 + 6f * density, y0 + h, 3f * density, 3f * density, focusPaint)
            canvas.drawText(c.title, x0 + 20f * density, y0 + 40f * density, titlePaint)
            c.body.forEachIndexed { i, line ->
                canvas.drawText(line, x0 + 20f * density, y0 + 76f * density + i * lineH, textPaint)
            }
        }
    }
}
