package com.droidforge.inmobridge.phone

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Minimal single-screen config UI (deliberately View-based — zero Compose
 * dependency on the phone side keeps the APK tiny):
 * presets, base URL, model, API key, provider status + test button.
 */
class MainActivity : Activity() {

    private lateinit var baseUrl: EditText
    private lateinit var model: EditText
    private lateinit var apiKey: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cfg = AiConfig.load(this)
        val pad = (resources.displayMetrics.density * 16).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        fun label(t: String) = TextView(this).apply { text = t }

        root.addView(label("AI provider").apply { textSize = 20f })

        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val ctx = this
        fun preset(name: String, url: String, model: String) {
            presets.addView(Button(ctx).apply {
                text = name
                setOnClickListener {
                    baseUrl.setText(url)
                    model@ setText(model)  // field name collides with param — label it
                    apiKey.setText("")
                }
            })
        }
        preset("Ollama (LAN)", AiConfig.DEFAULT_BASE_URL, AiConfig.DEFAULT_MODEL)
        preset("OpenAI", AiConfig.PRESET_OPENAI.first, AiConfig.PRESET_OPENAI.second)
        preset("Gemini", AiConfig.PRESET_GEMINI.first, AiConfig.PRESET_GEMINI.second)
        root.addView(presets)

        root.addView(label("Base URL (OpenAI-compatible /v1)"))
        baseUrl = EditText(this).apply { setText(cfg.baseUrl); hint = "http://host:11434/v1" }
        root.addView(baseUrl)

        root.addView(label("Model"))
        model = EditText(this).apply { setText(cfg.model); hint = "qwen3-coder:30b" }
        root.addView(model)

        root.addView(label("API key (leave empty for local Ollama)"))
        apiKey = EditText(this).apply {
            setText(cfg.apiKey)
            hint = "sk-… (never committed)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(apiKey)

        root.addView(Button(this).apply {
            text = "Save + start bridge"
            setOnClickListener {
                val c = AiConfig(
                    kind = AiConfig.Kind.COMPAT,
                    baseUrl = baseUrl.text.toString().trim(),
                    apiKey = apiKey.text.toString().trim(),
                    model = model.text.toString().trim(),
                )
                if (c.baseUrl.isBlank() || c.model.isBlank()) {
                    Toast.makeText(this@MainActivity, "Base URL and model required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                c.save(this@MainActivity)
                stopService(Intent(this@MainActivity, BridgeService::class.java))
                startForegroundService(Intent(this@MainActivity, BridgeService::class.java))
                Toast.makeText(this@MainActivity, "Saved — bridge running", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(Button(this).apply {
            text = "Test AI connection"
            setOnClickListener {
                val c = AiConfig(
                    kind = AiConfig.Kind.COMPAT,
                    baseUrl = baseUrl.text.toString().trim(),
                    apiKey = apiKey.text.toString().trim(),
                    model = model.text.toString().trim(),
                )
                Toast.makeText(this@MainActivity, "Testing…", Toast.LENGTH_SHORT).show()
                Thread {
                    val card = c.toProvider().ask("Reply with the single word: ready")
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Title=${card.title} | ${card.body.firstOrNull().orEmpty()}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }.start()
            }
        })

        setContentView(ScrollView(this).apply { addView(root) })

        startForegroundService(Intent(this, BridgeService::class.java))
    }
}
