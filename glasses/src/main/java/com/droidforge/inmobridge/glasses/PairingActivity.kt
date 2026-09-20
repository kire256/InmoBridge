package com.droidforge.inmobridge.glasses

import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.droidforge.inmobridge.core.QrPayload
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Glasses-side pairing: scan the phone's QR (camera) or paste the payload
 * manually. Stores host/port/token in app prefs; the launcher reconnects with
 * them on next start.
 */
class PairingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (resources.displayMetrics.density * 16).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Pair with phone"
            textSize = 22f
        })

        val status = TextView(this).apply {
            val saved = PairingStore.load(this@PairingActivity)
            text = if (saved != null) "Paired: ${saved.host}:${saved.port}" else "Not paired"
        }
        root.addView(status)

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

        root.addView(TextView(this).apply { text = "Or paste payload:" })
        val manual = EditText(this).apply { hint = "{\"v\":1,\"h\":…}" }
        root.addView(manual)

        root.addView(Button(this).apply {
            text = "Save payload"
            setOnClickListener {
                val p = QrPayload.decode(manual.text.toString().trim())
                if (p == null) {
                    Toast.makeText(this@PairingActivity, "Invalid payload", Toast.LENGTH_SHORT).show()
                } else {
                    PairingStore.save(this@PairingActivity, p)
                    status.text = "Paired: ${p.host}:${p.port}"
                    Toast.makeText(this@PairingActivity, "Saved", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                }
            }
        })

        setContentView(android.widget.ScrollView(this).apply { addView(root) })
    }

    private val scanner = registerForActivityResult(ScanContract(),
        { result: com.journeyapps.barcodescanner.ScanIntentResult ->
            result.contents?.let { raw ->
                val p = QrPayload.decode(raw)
                if (p != null) {
                    PairingStore.save(this, p)
                    Toast.makeText(this, "Paired with ${p.name ?: p.host}", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
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
