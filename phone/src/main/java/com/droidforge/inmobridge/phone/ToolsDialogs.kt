package com.droidforge.inmobridge.phone

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.droidforge.inmobridge.core.BridgeMessage
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

/** Builders for the Tools-tab feature dialogs (Teleprompter/Translate/Navigate). */
object ToolsDialogs {

    /** True when the bridge service can carry an envelope right now. */
    private fun push(env: com.droidforge.inmobridge.core.Envelope): Boolean =
        BridgeService.instance?.sendToGlasses(env) == true

    private fun noBridge(ctx: Activity) {
        Toast.makeText(ctx, "Glasses not connected — start the bridge + pair first", Toast.LENGTH_LONG).show()
    }

    /** Teleprompter: multiline script -> fullscreen prompter on the glasses. */
    fun teleprompter(ctx: Activity) {
        val pad = dp(ctx, 20f)
        val input = EditText(ctx).apply {
            hint = "One line per cue…"
            minLines = 6
            gravity = Gravity.TOP
            setSingleLine(false)
        }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(ctx, 8f), pad, 0)
            addView(TextView(ctx).apply {
                text = "Each line becomes a slide on the glasses.\nSwipe LEFT/UP = previous, RIGHT/DOWN = next, BACK = exit."
            })
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(ctx)
            .setTitle("Teleprompter")
            .setView(box)
            .setPositiveButton("Send to glasses") { _, _ ->
                val lines = input.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isEmpty()) {
                    Toast.makeText(ctx, "Script is empty", Toast.LENGTH_SHORT).show()
                } else if (!push(BridgeMessage.prompter(lines, System.nanoTime()))) {
                    noBridge(ctx)
                } else {
                    Toast.makeText(ctx, "Prompter live on glasses", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Translate: on-device ML Kit -> result -> push to glasses HUD. */
    fun translate(ctx: Activity) {
        val pad = dp(ctx, 20f)
        val langs = listOf(
            "en" to TranslateLanguage.ENGLISH, "es" to TranslateLanguage.SPANISH,
            "fr" to TranslateLanguage.FRENCH, "de" to TranslateLanguage.GERMAN,
            "it" to TranslateLanguage.ITALIAN, "pt" to TranslateLanguage.PORTUGUESE,
            "zh" to TranslateLanguage.CHINESE, "ja" to TranslateLanguage.JAPANESE,
            "ko" to TranslateLanguage.KOREAN, "ru" to TranslateLanguage.RUSSIAN,
            "ar" to TranslateLanguage.ARABIC, "hi" to TranslateLanguage.HINDI,
        )
        val names = langs.map { it.first.uppercase() }

        val src = EditText(ctx).apply { hint = "Text to translate"; minLines = 2; setSingleLine(false) }
        val fromSpin = android.widget.Spinner(ctx).apply { adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, names) }
        val toSpin = android.widget.Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, names)
            setSelection(1) // es
        }
        val langRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(fromSpin, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(ctx).apply { text = "  →  " })
            addView(toSpin, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val result = TextView(ctx).apply {
            text = ""
            textSize = 18f
            setPadding(0, dp(ctx, 12f), 0, 0)
        }

        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(ctx, 8f), pad, 0)
            addView(src)
            addView(langRow)
            addView(result)
        }

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("Translate → glasses")
            .setView(box)
            .setPositiveButton("Send to glasses", null) // replaced below to keep dialog open on failure
            .setNeutralButton("Translate", null)
            .setNegativeButton("Close", null)
            .create()

        fun doTranslate(after: (String) -> Unit) {
            val text = src.text.toString().trim()
            if (text.isEmpty()) return
            val from = langs[fromSpin.selectedItemPosition].second
            val to = langs[toSpin.selectedItemPosition].second
            val translator = Translation.getClient(
                TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build()
            )
            result.text = "Translating…"
            translator.downloadModelIfNeeded()
                .addOnSuccessListener {
                    translator.translate(text)
                        .addOnSuccessListener { out ->
                            result.text = out
                            after(out)
                        }
                        .addOnFailureListener { e -> result.text = "Failed: ${e.message}" }
                }
                .addOnFailureListener { e -> result.text = "Model download failed: ${e.message}" }
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { doTranslate { } }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val out = result.text.toString()
                if (out.isBlank() || out.startsWith("Translating") || out.startsWith("Failed")) {
                    doTranslate { translated ->
                        pushOrToast(ctx, translated)
                        dialog.dismiss()
                    }
                } else {
                    pushOrToast(ctx, out)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun pushOrToast(ctx: Activity, text: String) {
        if (!push(BridgeMessage.nav(text, "", id = System.nanoTime()))) noBridge(ctx)
    }

    /** Navigate: fire Google Maps turn-by-turn; NavMirror mirrors it to glasses. */
    fun navigate(ctx: Activity) {
        val pad = dp(ctx, 20f)
        val dest = EditText(ctx).apply { hint = "Where to? (address or place)" }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(ctx, 8f), pad, 0)
            addView(TextView(ctx).apply {
                text = "Starts Google Maps navigation on the phone. Turn-by-turn is mirrored to the glasses via notification access."
            })
            addView(dest)
        }
        AlertDialog.Builder(ctx)
            .setTitle("Navigate")
            .setView(box)
            .setPositiveButton("Start Maps") { _, _ ->
                val q = dest.text.toString().trim()
                if (q.isEmpty()) return@setPositiveButton
                runCatching {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(q)}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure {
                    Toast.makeText(ctx, "Google Maps not installed", Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton("Notification access", null)
            .show().apply {
                getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    NavMirrorService.openListenerSettings(ctx)
                }
            }
    }

    private fun dp(ctx: Activity, v: Float): Int = (ctx.resources.displayMetrics.density * v).toInt()
}
