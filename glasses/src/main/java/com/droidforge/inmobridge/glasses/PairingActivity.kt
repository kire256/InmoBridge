package com.droidforge.inmobridge.glasses

import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.os.Bundle
import android.content.Intent
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.droidforge.inmobridge.core.QrPayload
import com.droidforge.inmobridge.glasses.BuildConfig
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Glasses-side pairing: scan the phone's QR (camera) or paste the payload
 * manually. Stores host/port/token in app prefs; the launcher reconnects with
 * them on next start. The screen doubles as illustrated instructions so the
 * flow is discoverable without the overlay.
 */
class PairingActivity : AppCompatActivity() {

    private var statusListener: ((Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val pad = (d * 16).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Pair with phone"
            textSize = 22f
        })

        val saved = PairingStore.load(this@PairingActivity)
        val status = TextView(this).apply {
            text = if (saved != null) {
                "Paired: ${saved.host}:${saved.port} · v${BuildConfig.VERSION_NAME}"
            } else {
                "Not paired yet · v${BuildConfig.VERSION_NAME}"
            }
            textSize = 15f
        }
        root.addView(status)

        // Live bridge state (updates the moment the phone accepts the token).
        val live = TextView(this).apply { textSize = 15f }
        root.addView(live)
        statusListener = { s ->
            live.text = "Bridge: ${statusText(s)}"
            live.setTextColor(
                when (s) {
                    BridgeState.CONNECTED -> 0xFF39D2C0.toInt()
                    BridgeState.REJECTED -> 0xFFE85D5D.toInt()
                    else -> 0xFF9AA0A6.toInt()
                }
            )
        }
        BridgeState.addListener(statusListener!!)

        root.addView(TextView(this).apply {
            text = "HOW TO PAIR"
            textSize = 13f
        })
        listOf(
            "1.  Open InmoBridge on your phone",
            "2.  Tap  \"Pair glasses (show QR)\"",
            "3.  Tap  Scan QR  below",
            "4.  Point these glasses at the QR",
        ).forEach { step ->
            root.addView(TextView(this).apply {
                text = step
                textSize = 16f
                setPadding((d * 8).toInt(), (d * 2).toInt(), 0, (d * 2).toInt())
            })
        }
        root.addView(TextView(this).apply {
            text = "No camera? Type the payload text shown under the phone's QR into the box below, then Save."
            textSize = 13f
            setPadding(0, (d * 6).toInt(), 0, 0)
        })

        root.addView(Button(this).apply {
            text = "Scan QR"
            setOnClickListener {
                val opts = ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    setPrompt("Point at the phone's QR")
                    setBeepEnabled(false)
                    setOrientationLocked(true)
                }
                scanner.launch(opts)
            }
        })

        root.addView(TextView(this).apply {
            text = "Or paste payload:"
            textSize = 13f
        })
        val manual = EditText(this).apply {
            hint = "{\"v\":1,\"h\":…}"
            textSize = 14f
            gravity = Gravity.TOP
            minLines = 2
        }
        root.addView(manual)

        root.addView(Button(this).apply {
            text = "Save payload"
            setOnClickListener {
                val p = QrPayload.decode(manual.text.toString().trim())
                if (p == null) {
                    Toast.makeText(this@PairingActivity, "Invalid payload", Toast.LENGTH_SHORT).show()
                } else {
                    PairingStore.save(this@PairingActivity, p)
                    status.text = "Paired: ${p.host}:${p.port} · v${BuildConfig.VERSION_NAME}"
                    Toast.makeText(this@PairingActivity, "Saved", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                }
            }
        })

        setContentView(android.widget.ScrollView(this).apply { addView(root) })
    }

    private fun statusText(s: Int) = when (s) {
        BridgeState.CONNECTED -> "Connected — pairing works"
        BridgeState.CONNECTING -> "Connecting…"
        BridgeState.REJECTED -> "Rejected — re-scan the QR"
        else -> "Disconnected"
    }

    override fun onDestroy() {
        statusListener?.let { BridgeState.removeListener(it) }
        super.onDestroy()
    }

    private val scanner = registerForActivityResult(ScanContract(),
        { result: com.journeyapps.barcodescanner.ScanIntentResult ->
            result.contents?.let { raw ->
                val p = QrPayload.decode(raw)
                if (p != null) {
                    PairingStore.save(this, p)
                    Toast.makeText(this, "Paired with ${p.name ?: p.host}", Toast.LENGTH_SHORT).show()
                    // Restart launcher so it picks up the new pairing immediately
                    startActivity(Intent(this, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                } else {
                    Toast.makeText(this, "Not a bridge QR code", Toast.LENGTH_SHORT).show()
                }
            }
        })
}

/** Stored pairing (host/port/token) in glasses app prefs. */
object PairingStore {
    private const val PREFS = "pairing"

    fun save(context: Context, p: QrPayload.Parsed) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("host", p.host)
            .putInt("port", p.port)
            .putString("token", p.token)
            .putString("name", p.name ?: "")
            .apply()
    }

    fun load(context: Context): QrPayload.Parsed? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val host = sp.getString("host", null) ?: return null
        return QrPayload.Parsed(host, sp.getInt("port", 0), sp.getString("token", "") ?: "",
            sp.getString("name", "")?.takeIf { it.isNotEmpty() })
    }
}
