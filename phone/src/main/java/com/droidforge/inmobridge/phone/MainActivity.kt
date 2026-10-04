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
    private var installStatus: TextView? = null
    private val eventSink = { env: com.droidforge.inmobridge.core.Envelope ->
        if (env.type == "event") {
            val name = env.payload.optString("name")
            val data = env.payload.optJSONObject("data")
            val line = when (name) {
                "apk_started" -> "Upload started…"
                "apk_progress" -> {
                    val b = data?.optLong("bytes") ?: 0L
                    val phase = data?.optString("phase").orEmpty()
                    if (phase == "installing") "Installing on glasses…"
                    else "Uploading: ${"%.1f".format(b / 1048576f)} MB"
                }
                "apk_result" ->
                    if (data?.optBoolean("ok") == true) "✔ Installed on glasses"
                    else if (data?.optBoolean("pending_user") == true)
                        "Confirm the install prompt on the glasses"
                    else "✖ Install failed: ${data?.optString("error").orEmpty()}"
                else -> null
            }
            if (line != null) runOnUiThread {
                installStatus?.text = line
                if (name == "apk_result") {
                    Toast.makeText(this, line, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

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

        content = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
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

        BridgeService.addEventListener(eventSink)

        // targetSdk 35 = edge-to-edge: keep the tab bar clear of the nav bar
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }

        selectTab("glasses")
        startForegroundService(Intent(this, BridgeService::class.java))
    }

    override fun onDestroy() {
        BridgeService.removeEventListener(eventSink)
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_INSTALL && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) installFromUri(uri)
        }
    }

    private fun installFromUri(uri: android.net.Uri) {
        val svc = BridgeService.instance
        if (svc == null || !BridgeService.running) {
            Toast.makeText(this, "Bridge service not running", Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            try {
                val input = contentResolver.openInputStream(uri)
                if (input == null) {
                    runOnUiThread { Toast.makeText(this, "Cannot read that file", Toast.LENGTH_SHORT).show() }
                    return@Thread
                }
                val display = uri.lastPathSegment ?: "app.apk"
                val cacheFile = java.io.File(cacheDir, "upload_to_glasses.apk")
                cacheFile.outputStream().use { out -> input.copyTo(out, 64 * 1024) }
                input.close()
                val ok = svc.sendApk(cacheFile, display)
                runOnUiThread {
                    if (!ok) Toast.makeText(this, "Glasses not connected", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Read failed: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    companion object {
        private const val REQ_INSTALL = 51
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

        // Tools first - testing the relay is the primary action.
        page.addView(card {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
            addView(caption("Send a sample notification to the glasses:"))
            addView(Button(this@MainActivity).apply {
                text = "Send test notification"
                isAllCaps = false
                setOnClickListener {
                    val ok = BridgeService.instance?.sendTestNotification() == true
                    Toast.makeText(
                        this@MainActivity,
                        if (ok) "Sent - check the glasses" else "Glasses not connected",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
            addView(caption("Install an APK on the glasses over the bridge:"))
            addView(Button(this@MainActivity).apply {
                text = "Pick APK to install…"
                isAllCaps = false
                setOnClickListener {
                    val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT)
                    intent.type = "application/vnd.android.package-archive"
                    intent.addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    startActivityForResult(
                        android.content.Intent.createChooser(intent, "Pick APK"), REQ_INSTALL
                    )
                }
            })
            addView(TextView(this@MainActivity).apply {
                textSize = 13f
                setTextColor(0xFF39D2C0.toInt())
                setPadding(0, dp(4f), 0, 0)
                installStatus = this
            })
        })

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
            addView(switch(this@MainActivity, RelayConfig.relayEnabled(this@MainActivity)) { checked ->
                RelayConfig.setRelayEnabled(this@MainActivity, checked)
                Toast.makeText(this@MainActivity, if (checked) "Relay ON" else "Relay OFF", Toast.LENGTH_SHORT).show()
            })
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
            row.addView(switch(this@MainActivity, RelayConfig.isAppAllowed(this@MainActivity, pkg)) { checked ->
                RelayConfig.setAppAllowed(this@MainActivity, pkg, checked)
            })
            page.addView(row)
        }
        if (apps.isEmpty()) page.addView(caption("No launchable apps found."))
    }

    // ---------- Themes tab ----------

    private fun buildThemes(page: LinearLayout) {
        page.addView(title("Themes"))
        page.addView(caption("Per notification type: card theme, sound, vibration"))

        // Glasses display settings
        page.addView(card {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
            addView(TextView(this@MainActivity).apply {
                text = "Glasses display"
                textSize = 16f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(caption("How much of each notification the glasses show"))

            val maxLines = RelayConfig.maxLines(this@MainActivity)
            addView(caption("Max text lines per card: $maxLines"))
            val lineRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            lineRow.addView(Button(this@MainActivity).apply {
                text = "−"; isAllCaps = false
                setOnClickListener {
                    val v = RelayConfig.maxLines(this@MainActivity) - 1
                    RelayConfig.setMaxLines(this@MainActivity, v)
                    BridgeService.instance?.pushConfig()
                    selectTab("themes")
                }
            })
            lineRow.addView(Button(this@MainActivity).apply {
                text = "+"; isAllCaps = false
                setOnClickListener {
                    val v = RelayConfig.maxLines(this@MainActivity) + 1
                    RelayConfig.setMaxLines(this@MainActivity, v)
                    BridgeService.instance?.pushConfig()
                    selectTab("themes")
                }
            })
            addView(lineRow)

            addView(caption("Auto-scroll long text after a moment (pages through it):"))
            val autoOn = RelayConfig.autoScrollMs(this@MainActivity) > 0
            addView(switch(this@MainActivity, autoOn) { checked ->
                RelayConfig.setAutoScrollMs(this@MainActivity, if (checked) 4000L else 0L)
                BridgeService.instance?.pushConfig()
            })

            addView(caption("Cards auto-hide after ~6s. Changes apply to new notifications."))
        })

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
    /** Switch with an unmistakable state scheme: ON = teal track + white thumb,
     *  OFF = dim grey track + grey thumb (Samsung's default reads backwards on black). */
    private fun switch(context: android.content.Context, on: Boolean, onChange: (Boolean) -> Unit): android.widget.Switch =
        android.widget.Switch(context).apply {
            isChecked = on
            trackTintList = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(-android.R.attr.state_checked),
                    intArrayOf(android.R.attr.state_checked),
                ),
                intArrayOf(0xFF3A3F46.toInt(), 0xFF39D2C0.toInt()),
            )
            thumbTintList = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(-android.R.attr.state_checked),
                    intArrayOf(android.R.attr.state_checked),
                ),
                intArrayOf(0xFF9AA0A6.toInt(), Color.WHITE),
            )
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }

    private fun pill(bg: Int, radius: Float = 20f): GradientDrawable =
        GradientDrawable().apply { setColor(bg); cornerRadius = dp(radius).toFloat() }
}
