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

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2101418.toInt() }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val appPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9AA0A6.toInt() }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 42f; isFakeBoldText = true
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8EAED.toInt(); textSize = 32f }

    /** Show a notification card; auto-hides after spec.timeoutMs. */
    fun show(s: NotifSpec) {
        spec = s
        showUntil = System.currentTimeMillis() + s.timeoutMs
        invalidate()
        keepAlive()
    }

    fun current(): NotifSpec? =
        spec?.takeIf { System.currentTimeMillis() < showUntil }

    private val ticker = Runnable {
        if (System.currentTimeMillis() >= showUntil) {
            spec = null
            invalidate()
        } else {
            keepAlive()
        }
    }

    private fun keepAlive() {
        handler.removeCallbacks(ticker)
        spec?.let { handler.postDelayed(ticker, 250) }
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
        val body = if (s.text.isBlank()) null else layout(s.text, textPaint, textW)
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
    }

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
