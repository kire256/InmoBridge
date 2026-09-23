package com.droidforge.inmobridge.glasses

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Conversation mode HUD: minimalist vertical chat flow. My speech renders as
 * teal-tinted bubbles on the RIGHT (original text, small translated line
 * below); the other party's translated speech renders as grey bubbles on the
 * LEFT. Newest at the bottom, older lines scroll off. BACK exits.
 */
class ConversationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    data class Bubble(val mine: Boolean, val primary: String, val secondary: String, val live: Boolean)

    private val bubbles = ArrayList<Bubble>()
    private var active = false

    private val bgPaint = Paint().apply { color = Color.BLACK }
    private val myBubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x3339D2C0.toInt() }
    private val theirBubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF.toInt() }
    private val liveRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF39D2C0.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val primaryMine = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 30f; isFakeBoldText = true
    }
    private val primaryTheirs = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 30f }
    private val secondary = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt(); textSize = 22f }
    private val header = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF39D2C0.toInt(); textSize = 22f }
    private val hint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88FFFFFF.toInt(); textSize = 20f }

    fun start() {
        bubbles.clear()
        active = true
        invalidate()
    }

    fun stop() {
        active = false
        bubbles.clear()
        invalidate()
    }

    fun isActive() = active

    /** Add/refresh a bubble. A live bubble on the same side replaces the last live one. */
    fun push(mine: Boolean, primary: String, secondary: String, live: Boolean) {
        val last = bubbles.lastOrNull()
        if (live && last != null && last.mine == mine && last.live) {
            bubbles[bubbles.lastIndex] = Bubble(mine, primary, secondary, live)
        } else {
            bubbles.add(Bubble(mine, primary, secondary, live))
            while (bubbles.size > MAX_BUBBLES) bubbles.removeAt(0)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!active) return
        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        canvas.drawText("Conversation - BACK to exit", 16f * d, 28f * d, header)

        if (bubbles.isEmpty()) {
            canvas.drawText("Say something...", w / 2f, h / 2f, hint)
            return
        }

        val maxBubbleW = w * 0.72f
        val pad = 16f * d
        var y = 44f * d

        val visible = bubbles.takeLast(4)
        for (b in visible) {
            val p = layout(b.primary, if (b.mine) primaryMine else primaryTheirs, maxBubbleW - 2 * pad)
            val s = if (b.secondary.isBlank()) null else layout(b.secondary, secondary, maxBubbleW - 2 * pad)
            val extra = s?.height?.plus((6 * d).toInt()) ?: 0
            val bh = 20f * d + p.height + extra + 20f * d
            val bw = min(maxBubbleW, (maxOf(p.width.toFloat(), s?.width?.toFloat() ?: 0f) + 2 * pad).coerceAtLeast(120f * d))
            val x0 = if (b.mine) w - bw - pad else pad

            canvas.drawRoundRect(x0, y, x0 + bw, y + bh, 14f * d, 14f * d, if (b.mine) myBubble else theirBubble)
            if (b.live) canvas.drawRoundRect(x0, y, x0 + bw, y + bh, 14f * d, 14f * d, liveRing)

            var ty = y + 14f * d
            canvas.withTranslation(x0 + pad, ty) { p.draw(this) }
            ty += p.height + 4f * d
            s?.let { canvas.withTranslation(x0 + pad, ty) { it.draw(this) } }
            y += bh + 10f * d
            if (y > h - 20f * d) break
        }
    }

    private fun layout(text: String, paint: TextPaint, width: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(50))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()

    private inline fun Canvas.withTranslation(x: Float, y: Float, block: Canvas.() -> Unit) {
        val checkpoint = save()
        translate(x, y)
        try { block() } finally { restoreToCount(checkpoint) }
    }

    companion object {
        private const val MAX_BUBBLES = 12
    }
}
