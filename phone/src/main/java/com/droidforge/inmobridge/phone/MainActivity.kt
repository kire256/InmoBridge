package com.droidforge.inmobridge.phone

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.droidforge.inmobridge.phone.BuildConfig

/**
 * RayNeo-style tabbed UI: pure black, big title, dark rounded cards, and a
 * bottom floating tab bar (Glasses / Tools / AI / Me). Views-based, no
 * Compose — keeps the APK tiny.
 */
class MainActivity : Activity() {

    private lateinit var tabsBar: LinearLayout
    private lateinit var content: ScrollView
    private val tabViews = HashMap<String, TextView>()
    private var activeTab = "glasses"

    private lateinit var baseUrl: EditText
    private lateinit var model: EditText
    private lateinit var apiKey: EditText

    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (d * v).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cfg = AiConfig.load(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        // ---- Header: big title + version pill ----
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20f), dp(24f), dp(20f), dp(12f))
        }
        header.addView(TextView(this).apply {
            text = "InmoBridge"
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(this).apply {
            text = "v${BuildConfig.VERSION_NAME}"
            textSize = 12f
            setTextColor(0xFF9AA0A6.toInt())
            background = pill(0x22FFFFFF)
            setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
        })
        root.addView(header)

        // ---- Content area ----
        content = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) }
        root.addView(content)

        // ---- Bottom tab bar (floating rounded, RayNeo-style) ----
        tabsBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
            background = pill(0xFF141414.toInt(), radius = 28f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(dp(12f), dp(8f), dp(12f), dp(12f)) }
        }
        listOf("glasses" to "Glasses", "tools" to "Tools", "ai" to "AI", "me" to "Me")
            .forEach { (id, label) -> tabsBar.addView(makeTab(id, label)) }
        root.addView(tabsBar)

        setContentView(root)
        selectTab("glasses")
        startForegroundService(Intent(this, BridgeService::class.java))
    }

    // ---------------- Tabs ----------------

    private fun makeTab(id: String, label: String): TextView {
        val t = TextView(this).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(18f), dp(8f), dp(18f), dp(8f))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { selectTab(id) }
        }
        tabViews[id] = t
        return t
    }

    private fun selectTab(id: String) {
        activeTab = id
        tabViews.forEach { (tid, v) ->
            val on = tid == id
            v.setTextColor(if (on) Color.WHITE else 0xFF8A8F98.toInt())
            v.typeface = Typeface.create(Typeface.DEFAULT, if (on) Typeface.BOLD else Typeface.NORMAL)
        }
        content.removeAllViews()
        val pad = dp(20f)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(4f), pad, dp(8f))
        }
        when (id) {
            "glasses" -> buildGlassesTab(page)
            "tools" -> buildToolsTab(page)
            "ai" -> buildAiTab(page, AiConfig.load(this))
            "me" -> buildMeTab(page)
        }
        content.addView(page)
    }

    // ---------------- Glasses tab ----------------

    private fun buildGlassesTab(page: LinearLayout) {
        page.addView(sectionTitle("Glasses"))
        page.addView(caption("INMO Air 3 · tethered HUD"))

        // Status chips row (RayNeo's wifi/community chips)
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chips.addView(chip(if (BridgeService.running) "● bridge running" else "○ bridge stopped",
            if (BridgeService.running) 0xFF39D2C0.toInt() else 0xFF8A8F98.toInt()))
        chips.addView(chip("✦ ${PairingManager.token(this).take(6)}…", 0xFF9AA0A6.toInt()))
        page.addView(chips)

        page.addView(card {
            setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
            orientation = LinearLayout.VERTICAL
            addView(caption("Pair a new pair of glasses: the glasses app scans this code."))
            addView(Button(this@MainActivity).apply {
                text = "Pair glasses (show QR)"
                isAllCaps = false
                setOnClickListener { startActivity(Intent(this@MainActivity, PairingActivity::class.java)) }
            }, ll(-1, -2).also { it.topMargin = dp(8f) })
        })
    }

    // ---------------- Tools tab ----------------

    private fun buildToolsTab(page: LinearLayout) {
        page.addView(sectionTitle("Tools"))
        page.addView(caption("Send straight to the glasses HUD"))

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(toolCard("▶", "Teleprompter", "Scroll your script", 0xFF6C4DE8.toInt()) { ToolsDialogs.teleprompter(this) }, lp())
        row1.addView(toolCard("⌘", "Navigate", "Maps + HUD mirror", 0xFF2E7CF6.toInt()) { ToolsDialogs.navigate(this) }, lp())
        page.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(toolCard("文", "Translate", "12 languages, on-device", 0xFF22A85C.toInt()) { ToolsDialogs.translate(this) }, lp())
        row2.addView(toolCard("▦", "Layout", "Edit glasses dock", 0xFFE8A03C.toInt()) { showLayoutEditor() }, lp())
        page.addView(row2)

        page.addView(caption("Touchpad: on the glasses, swipes drive the teleprompter and the launcher; tap selects."))
    }

    private fun showLayoutEditor() {
        val (dock0, apps0) = DockConfig.load(this)
        val dock = dock0.toMutableList()
        val apps = apps0.toMutableList()
        val listText = TextView(this)
        fun renderLists() {
            listText.text = buildString {
                append("Dock:  ").append(dock.joinToString("  ") { it.icon })
                append("\nApps:  ").append(apps.joinToString("  ") { it.icon })
            }
        }
        renderLists()
        val moveRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        moveRow.addView(Button(this).apply {
            text = "← to dock"
            isAllCaps = false
            setOnClickListener { if (apps.isNotEmpty()) { dock.add(apps.removeAt(0)); renderLists() } }
        })
        moveRow.addView(Button(this).apply {
            text = "dock → apps"
            isAllCaps = false
            setOnClickListener { if (dock.isNotEmpty()) { apps.add(0, dock.removeAt(dock.lastIndex)); renderLists() } }
        })
        moveRow.addView(Button(this).apply {
            text = "reset"
            isAllCaps = false
            setOnClickListener {
                dock.clear(); apps.clear()
                dock.addAll(DockConfig.DEFAULT_DOCK)
                apps.addAll(DockConfig.DEFAULT_APPS)
                renderLists()
            }
        })
        val push = Button(this).apply {
            text = "Push layout to glasses"
            isAllCaps = false
            setOnClickListener {
                DockConfig.save(this@MainActivity, dock, apps)
                startService(
                    Intent(this@MainActivity, BridgeService::class.java)
                        .putExtra("cmd", "push_layout")
                )
                Toast.makeText(this@MainActivity, "Layout saved + pushed", Toast.LENGTH_SHORT).show()
            }
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Glasses layout")
            .setView(ScrollView(this).apply {
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(20f), dp(12f), dp(20f), 0)
                    addView(caption("Dock = bottom bar · Apps = category extras"))
                    addView(listText)
                    addView(moveRow)
                    addView(push)
                })
            })
            .setNegativeButton("Close", null)
            .show()
    }

    // ---------------- AI tab ----------------

    private fun buildAiTab(page: LinearLayout, cfg: AiConfig) {
        page.addView(sectionTitle("AI"))
        page.addView(caption("OpenAI-compatible endpoint (Ollama, OpenAI, Gemini-compat)"))

        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun preset(name: String, url: String, m: String) {
            presets.addView(Button(this).apply {
                text = name
                isAllCaps = false
                setOnClickListener {
                    baseUrl.setText(url)
                    model.setText(m)
                    apiKey.setText("")
                }
            }, lp())
        }
        preset("Ollama", AiConfig.DEFAULT_BASE_URL, AiConfig.DEFAULT_MODEL)
        preset("OpenAI", AiConfig.PRESET_OPENAI.first, AiConfig.PRESET_OPENAI.second)
        preset("Gemini", AiConfig.PRESET_GEMINI.first, AiConfig.PRESET_GEMINI.second)
        page.addView(presets)

        baseUrl = EditText(this).apply { setText(cfg.baseUrl); hint = "http://host:11434/v1" }
        model = EditText(this).apply { setText(cfg.model); hint = "qwen3-coder:30b" }
        apiKey = EditText(this).apply {
            setText(cfg.apiKey)
            hint = "sk-… (never committed)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        page.addView(field("Base URL", baseUrl))
        page.addView(field("Model", model))
        page.addView(field("API key (empty for local)", apiKey))

        page.addView(card {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
            addView(Button(this@MainActivity).apply {
                text = "Save + restart bridge"
                isAllCaps = false
                setOnClickListener {
                    val c = aiConfigFromFields() ?: run {
                        Toast.makeText(this@MainActivity, "Base URL and model required", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    c.save(this@MainActivity)
                    stopService(Intent(this@MainActivity, BridgeService::class.java))
                    startForegroundService(Intent(this@MainActivity, BridgeService::class.java))
                    Toast.makeText(this@MainActivity, "Saved — bridge running", Toast.LENGTH_SHORT).show()
                }
            })
            addView(Button(this@MainActivity).apply {
                text = "Test AI connection"
                isAllCaps = false
                setOnClickListener {
                    val c = aiConfigFromFields() ?: return@setOnClickListener
                    Toast.makeText(this@MainActivity, "Testing…", Toast.LENGTH_SHORT).show()
                    Thread {
                        val card = c.toProvider().ask("Reply with the single word: ready")
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "${card.title} | ${card.body.firstOrNull().orEmpty()}",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }.start()
                }
            })
        })
    }

    private fun aiConfigFromFields(): AiConfig? {
        val c = AiConfig(
            kind = AiConfig.Kind.COMPAT,
            baseUrl = baseUrl.text.toString().trim(),
            apiKey = apiKey.text.toString().trim(),
            model = model.text.toString().trim(),
        )
        return if (c.baseUrl.isBlank() || c.model.isBlank()) null else c
    }

    // ---------------- Me tab ----------------

    private fun buildMeTab(page: LinearLayout) {
        page.addView(sectionTitle("Me"))
        page.addView(card {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
            addView(TextView(this@MainActivity).apply {
                text = "InmoBridge"
                textSize = 18f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(caption("Tethered launcher + HUD bridge for INMO Air 3.\nPhone = brain · Glasses = display.\n\nv${BuildConfig.VERSION_NAME}\nDroidForge · github.com/kire256/InmoBridge"))
        })
    }

    // ---------------- Shared styled widgets ----------------

    private fun sectionTitle(t: String) = TextView(this).apply {
        text = t
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(Color.WHITE)
        setPadding(0, dp(8f), 0, dp(4f))
    }

    private fun caption(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(0xFF9AA0A6.toInt())
        setPadding(0, dp(2f), 0, dp(8f))
    }

    private fun field(label: String, input: EditText): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(caption(label))
            addView(input.apply {
                setSingleLine(true)
                setTextColor(Color.WHITE)
                setHintTextColor(0xFF6B7078.toInt())
                background = pill(0xFF141414.toInt(), radius = 12f)
                setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            })
        }

    private fun chip(label: String, color: Int): TextView = TextView(this).apply {
        text = label
        textSize = 13f
        setTextColor(color)
        background = pill(0xFF141414.toInt(), radius = 18f)
        setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .also { it.setMargins(0, 0, dp(8f), dp(8f)) }
    }

    private fun toolCard(icon: String, title: String, sub: String, color: Int, onClick: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = pill(0xFF141414.toInt(), radius = 18f)
            setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
            setOnClickListener { onClick() }
            addView(TextView(this@MainActivity).apply {
                text = icon
                textSize = 26f
                setTextColor(color)
            })
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 16f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(6f), 0, 0)
            })
            addView(TextView(this@MainActivity).apply {
                text = sub
                textSize = 12f
                setTextColor(0xFF9AA0A6.toInt())
            })
        }

    private fun card(build: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = pill(0xFF141414.toInt(), radius = 18f)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.setMargins(0, dp(8f), 0, dp(8f)) }
            build()
        }

    private fun pill(bg: Int, radius: Float = 20f): GradientDrawable =
        GradientDrawable().apply { setColor(bg); cornerRadius = dp(radius).toFloat() }

    private fun ll(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
    private fun lp() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        .also { it.setMargins(0, 0, dp(10f), dp(10f)) }
}
