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
import kotlin.math.max

/**
 * Fullscreen teleprompter overlay: the phone pushes a script
 * (`prompter` envelope); swipe LEFT/UP = previous line, RIGHT/DOWN = next
 * line, BACK = exit. Current line renders large and centered; upcoming lines
 * dim below.
 */
class PrompterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var lines: List<String> = emptyList()
        set(value) { field = value; index = 0.coerceAtMost(value.lastIndex.coerceAtLeast(0)); invalidate() }

    var index: Int = 0
        set(value) { field = value.coerceIn(0, max(0, lines.lastIndex)); invalidate() }

    var active: Boolean = false
        set(value) { field = value; invalidate() }

    private val bgPaint = Paint().apply { color = Color.BLACK }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt()
        textSize = 22f
    }
    private val currentPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 44f
        isFakeBoldText = true
    }
    private val nextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF.toInt()
        textSize = 30f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!active) return
        val d = resources.displayMetrics.density
        currentPaint.textSize = 44f * d
        nextPaint.textSize = 30f * d
        progressPaint.textSize = 20f * d

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        if (lines.isEmpty()) {
            canvas.drawText("(empty script)", width / 2f, height / 2f, progressPaint)
            return
        }

        canvas.drawText("${index + 1} / ${lines.size}", 20f * d, 30f * d, progressPaint)

        val textWidth = (width * 0.86f).toInt()
        var y = height * 0.38f
        val cur = StaticLayout.Builder
            .obtain(lines[index], 0, lines[index].length, currentPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        canvas.withTranslation(width * 0.07f, y - cur.height / 2f) { cur.draw(this) }
        y += cur.height + 24f * d

        for (i in (index + 1)..minOf(lines.lastIndex, index + 3)) {
            val sl = StaticLayout.Builder
                .obtain(lines[i], 0, lines[i].length, nextPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
            if (y > height) break
            canvas.withTranslation(width * 0.07f, y) { sl.draw(this) }
            y += sl.height + 14f * d
        }
    }

    /** True when the key was consumed by the prompter. */
    fun onKey(keyCode: Int): Boolean {
        if (!active) return false
        when (keyCode) {
            LauncherFocus.KEY_LEFT, LauncherFocus.KEY_UP -> { index--; return true }
            LauncherFocus.KEY_RIGHT, LauncherFocus.KEY_DOWN -> { index++; return true }
        }
        return false
    }

    private inline fun Canvas.withTranslation(x: Float, y: Float, block: Canvas.() -> Unit) {
        val checkpoint = save()
        translate(x, y)
        try {
            block()
        } finally {
            restoreToCount(checkpoint)
        }
    }
}
