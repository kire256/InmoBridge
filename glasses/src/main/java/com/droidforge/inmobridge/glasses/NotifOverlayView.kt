package com.droidforge.inmobridge.glasses

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import com.droidforge.inmobridge.core.NotifSpec
import com.droidforge.inmobridge.core.NotifThemes
import kotlin.math.min

/**
 * Notification overlay card: themed accent bar, app label, title, text.
 * Drawn into a system-overlay window; one notification at a time (newest wins).
 */
class NotifOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var spec: NotifSpec? = null
    private var showUntil: Long = 0
    private var page = 0
    private var pageCount = 1
    private var fullText: String = ""
    private var lastPageAt = 0L

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2101418.toInt() }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val appPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9AA0A6.toInt() }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 42f; isFakeBoldText = true
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8EAED.toInt(); textSize = 32f }

    /** Show a notification card; pages long text, auto-hides after timeout. */
    fun show(s: NotifSpec) {
        spec = s
        fullText = s.text
        page = 0
        lastPageAt = System.currentTimeMillis()
        showUntil = lastPageAt + s.timeoutMs
        invalidate()
        keepAlive()
    }

    fun current(): NotifSpec? =
        spec?.takeIf { System.currentTimeMillis() < showUntil }

    private val ticker = Runnable {
        val s = spec ?: return@Runnable
        val now = System.currentTimeMillis()
        if (now >= showUntil) {
            spec = null
            visibility = android.view.View.GONE
            invalidate()
            return@Runnable
        }
        // Auto-advance through pages of long text until the timeout wins.
        val interval = NotifOverlay.autoScrollMs.takeIf { it > 0 }
        if (pageCount > 1 && interval != null && now - lastPageAt >= interval) {
            page = (page + 1).mod(pageCount)
            lastPageAt = now
            invalidate()
        }
        keepAlive()
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun keepAlive() {
        mainHandler.removeCallbacks(ticker)
        spec?.let { mainHandler.postDelayed(ticker, 250) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = spec ?: return
        if (System.currentTimeMillis() >= showUntil) return
        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()

        val theme = NotifThemes.byId(s.theme)
        accentPaint.color = theme.accent
        appPaint.textSize = 20f * d
        titlePaint.textSize = 42f * d
        textPaint.textSize = 32f * d

        val margin = 40f * d
        val cardW = w - margin * 2
        val textW = (cardW - 48f * d).toInt()

        val title = layout(s.title, titlePaint, textW)
        textPaint.textSize = 32f * d

        // Paginate body text: each page holds at most NotifOverlay.maxLines lines.
        val maxLines = NotifOverlay.maxLines.coerceAtLeast(1)
        val bodyFull = layout(fullText.ifBlank { return }, textPaint, textW)
        pageCount = bodyFull.lineCount.coerceAtLeast(1)
        page = page.coerceIn(0, pageCount - 1)
        val startLine = page * maxLines
        val body = if (startLine < pageCount) {
            val endLine = min(startLine + maxLines, pageCount)
            val startOff = bodyFull.getLineStart(startLine)
            val endOff = bodyFull.getLineEnd(endLine - 1)
            val text = bodyFull.text.subSequence(startOff, endOff).trim().toString()
            layout(text, textPaint, textW)
        } else null
        val bodyH = body?.height?.toFloat() ?: 0f
        val cardH = 28f * d + 26f * d + title.height + bodyH + (if (body != null) 8f * d else 0f) + 28f * d
        val cardTop = h - cardH - 60f * d

        canvas.drawRoundRect(margin, cardTop, margin + cardW, cardTop + cardH, 18f * d, 18f * d, bgPaint)
        canvas.drawRoundRect(margin, cardTop, margin + 8f * d, cardTop + cardH, 4f * d, 4f * d, accentPaint)

        var y = cardTop + 24f * d
        canvas.drawText(s.app.uppercase(), margin + 24f * d, y + 18f * d, appPaint)
        y += 34f * d
        canvas.withTranslation(margin + 24f * d, y) { title.draw(this) }
        y += title.height + 8f * d
        body?.let { canvas.withTranslation(margin + 24f * d, y) { it.draw(this) } }

        // Page dots when the text spans multiple pages.
        if (pageCount > 1) {
            val dot = 5f * d
            val gap = 12f * d
            val totalW = (pageCount * dot + (pageCount - 1) * gap)
            var dx = margin + cardW - 24f * d - totalW
            val dy = cardTop + cardH - 18f * d
            for (i in 0 until pageCount) {
                pageDotPaint.color = if (i == page) Color.WHITE else 0xFF5F6368.toInt()
                canvas.drawCircle(dx + dot / 2, dy, dot / 2, pageDotPaint)
                dx += dot + gap
            }
        }
    }

    private val pageDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(50))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()

    private inline fun Canvas.withTranslation(x: Float, y: Float, block: Canvas.() -> Unit) {
        val checkpoint = save()
        translate(x, y)
        try { block() } finally { restoreToCount(checkpoint) }
    }
}
