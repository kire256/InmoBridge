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
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.droidforge.inmobridge.core.NotifThemes
import com.droidforge.inmobridge.phone.BuildConfig

/**
 * Phone companion: three tabs.
 *  - Glasses: bridge status, pairing QR, relay master switch
 *  - Apps: per-app relay allowlist
 *  - Themes: per-notification-type theme + sound + vibration
 */
class MainActivity : Activity() {

    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (d * v).toInt()

    private lateinit var tabsBar: LinearLayout
    private lateinit var content: ScrollView
    private val tabViews = HashMap<String, TextView>()
    private var activeTab = "glasses"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20f), dp(24f), dp(20f), dp(12f))
        }
        header.addView(TextView(this).apply {
            text = "InmoBridge"
            textSize = 30f
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

        content = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) }
        root.addView(content)

        tabsBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
            background = pill(0xFF141414.toInt(), 28f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(dp(12f), dp(8f), dp(12f), dp(12f)) }
        }
        listOf("glasses" to "Glasses", "apps" to "Apps", "themes" to "Themes")
            .forEach { (id, label) ->
                tabsBar.addView(TextView(this).apply {
                    text = label
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(dp(18f), dp(8f), dp(18f), dp(8f))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener { selectTab(id) }
                    tabViews[id] = this
                })
            }
        root.addView(tabsBar)

        setContentView(root)
        selectTab("glasses")
        startForegroundService(Intent(this, BridgeService::class.java))
    }

    private fun selectTab(id: String) {
        activeTab = id
        tabViews.forEach { (tid, v) ->
            val on = tid == id
            v.setTextColor(if (on) Color.WHITE else 0xFF8A8F98.toInt())
            v.typeface = Typeface.create(Typeface.DEFAULT, if (on) Typeface.BOLD else Typeface.NORMAL)
        }
        content.removeAllViews()
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(4f), dp(20f), dp(8f))
        }
        when (id) {
            "glasses" -> buildGlasses(page)
            "apps" -> buildApps(page)
            "themes" -> buildThemes(page)
        }
        content.addView(page)
    }

    // ---------- Glasses tab ----------

    private fun buildGlasses(page: LinearLayout) {
        page.addView(title("Glasses"))
        page.addView(caption("Mirror phone notifications to the INMO Air 3"))

        val listenerOk = RelayService.isListenerConnected()
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chipRow.addView(chip(
            if (BridgeService.running) "● bridge running" else "○ bridge stopped",
            if (BridgeService.running) 0xFF39D2C0.toInt() else 0xFF8A8F98.toInt()))
        chipRow.addView(chip(
            if (listenerOk) "● listener granted" else "○ no listener access",
            if (listenerOk) 0xFF39D2C0.toInt() else 0xFFE8A03C.toInt()))
        page.addView(chipRow)

        page.addView(card {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
            addView(caption(if (listenerOk)
                "Notification access granted - matching notifications are mirrored."
            else
                "Grant notification access so InmoBridge can see notifications:"))
            addView(Button(this@MainActivity).apply {
                text = if (listenerOk) "Reopen notification settings" else "Grant notification access"
                isAllCaps = false
                setOnClickListener { RelayService.openListenerSettings(this@MainActivity) }
            })
        })

        page.addView(card {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
            addView(caption("Pair glasses: the glasses app scans this QR."))
            addView(Button(this@MainActivity).apply {
                text = "Pair glasses (show QR)"
                isAllCaps = false
                setOnClickListener { startActivity(Intent(this@MainActivity, PairingActivity::class.java)) }
            })
        })

        // Master relay switch
        page.addView(card {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
            addView(caption("Relay notifications to the glasses"))
            val sw = android.widget.Switch(this@MainActivity)
            sw.isChecked = RelayConfig.relayEnabled(this@MainActivity)
            sw.setOnCheckedChangeListener { _, checked ->
                RelayConfig.setRelayEnabled(this@MainActivity, checked)
                Toast.makeText(this@MainActivity, if (checked) "Relay ON" else "Relay OFF", Toast.LENGTH_SHORT).show()
            }
            addView(sw)
        })
    }

    // ---------- Apps tab ----------

    private fun buildApps(page: LinearLayout) {
        page.addView(title("Apps"))
        page.addView(caption("Choose which apps mirror to the glasses"))

        val pm = packageManager
        val launchables = pm.queryIntentActivities(
            android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0)
        val apps = launchables.asSequence()
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .sortedBy { it.first.lowercase() }
            .toList()

        // Default-on for common communication apps (first run)
        val defOn = listOf("whatsapp", "messages", "messaging", "telegram", "signal",
            "gmail", "dialer", "phone", "contacts", "calendar")
        apps.forEach { (label, pkg) ->
            if (!getSharedPreferences("relay_config", MODE_PRIVATE).contains("app_$pkg")) {
                RelayConfig.setAppAllowed(this, pkg, defOn.any { pkg.contains(it) || label.lowercase().contains(it) })
            }
        }

        apps.forEach { (label, pkg) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6f), 0, dp(6f))
            }
            row.addView(TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(android.widget.Switch(this).apply {
                isChecked = RelayConfig.isAppAllowed(this@MainActivity, pkg)
                setOnCheckedChangeListener { _, checked ->
                    RelayConfig.setAppAllowed(this@MainActivity, pkg, checked)
                }
            })
            page.addView(row)
        }
        if (apps.isEmpty()) page.addView(caption("No launchable apps found."))
    }

    // ---------- Themes tab ----------

    private fun buildThemes(page: LinearLayout) {
        page.addView(title("Themes"))
        page.addView(caption("Per notification type: card theme, sound, vibration"))

        RelayConfig.TYPES.forEach { type ->
            page.addView(card {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
                addView(TextView(this@MainActivity).apply {
                    text = RelayConfig.TYPE_LABELS[type] ?: type
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                })
                val rule = RelayConfig.ruleFor(this@MainActivity, type)

                // Theme selector row
                addView(caption("Theme"))
                val themeRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
                NotifThemes.ALL.forEach { t ->
                    themeRow.addView(TextView(this@MainActivity).apply {
                        text = t.label
                        textSize = 13f
                        setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
                        background = pill(if (rule.first == t.id) t.accent else 0xFF141414.toInt(), 16f)
                        setTextColor(if (rule.first == t.id) Color.BLACK else Color.WHITE)
                        setOnClickListener {
                            val (th, so, vi) = RelayConfig.ruleFor(this@MainActivity, type)
                            RelayConfig.setRule(this@MainActivity, type, t.id, so, vi)
                            selectTab("themes")
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                        ).also { it.setMargins(0, 0, dp(8f), dp(8f)) }
                    })
                }
                addView(themeRow)

                // Sound selector row
                addView(caption("Sound"))
                val soundRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
                RelayConfig.soundIds().forEach { s ->
                    soundRow.addView(TextView(this@MainActivity).apply {
                        text = s
                        textSize = 13f
                        setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
                        val on = rule.second == s
                        background = pill(if (on) 0xFF39D2C0.toInt() else 0xFF141414.toInt(), 16f)
                        setTextColor(if (on) Color.BLACK else Color.WHITE)
                        setOnClickListener {
                            val (th, so, vi) = RelayConfig.ruleFor(this@MainActivity, type)
                            RelayConfig.setRule(this@MainActivity, type, th, s, vi)
                            selectTab("themes")
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                        ).also { it.setMargins(0, 0, dp(8f), dp(8f)) }
                    })
                }
                addView(soundRow)

                // Vibration selector row
                addView(caption("Vibration"))
                val vibRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
                RelayConfig.vibrationIds().forEach { v ->
                    vibRow.addView(TextView(this@MainActivity).apply {
                        text = v
                        textSize = 13f
                        setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
                        val on = rule.third == v
                        background = pill(if (on) 0xFF39D2C0.toInt() else 0xFF141414.toInt(), 16f)
                        setTextColor(if (on) Color.BLACK else Color.WHITE)
                        setOnClickListener {
                            val (th, so, vi) = RelayConfig.ruleFor(this@MainActivity, type)
                            RelayConfig.setRule(this@MainActivity, type, th, so, v)
                            selectTab("themes")
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                        ).also { it.setMargins(0, 0, dp(8f), dp(8f)) }
                    })
                }
                addView(vibRow)
            })
        }
    }

    // ---------- shared widgets ----------

    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(Color.WHITE); setPadding(0, dp(8f), 0, dp(4f))
    }
    private fun caption(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(0xFF9AA0A6.toInt()); setPadding(0, dp(2f), 0, dp(8f))
    }
    private fun chip(text: String, color: Int) = TextView(this).apply {
        this.text = text; textSize = 13f; setTextColor(color)
        background = pill(0xFF141414.toInt(), 18f)
        setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.setMargins(0, 0, dp(8f), dp(8f)) }
    }
    private fun card(build: LinearLayout.() -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = pill(0xFF141414.toInt(), 18f)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.setMargins(0, dp(8f), 0, dp(8f)) }
        build()
    }
    private fun pill(bg: Int, radius: Float = 20f): GradientDrawable =
        GradientDrawable().apply { setColor(bg); cornerRadius = dp(radius).toFloat() }
}
