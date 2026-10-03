package com.droidforge.inmobridge.glasses

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.droidforge.inmobridge.core.QrPayload
import com.droidforge.inmobridge.glasses.BuildConfig
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/** Glasses-side pairing: scan the phone QR or paste the payload manually. */
class PairingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val pad = (d * 16).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Pair with phone"; textSize = 22f })

        val saved = PairingStore.load(this)
        root.addView(TextView(this).apply {
            text = if (saved != null) "Paired: ${saved.host}:${saved.port} - v${BuildConfig.VERSION_NAME}"
            else "Not paired yet - v${BuildConfig.VERSION_NAME}"
            textSize = 15f
        })
        root.addView(TextView(this).apply {
            text = "1. Open InmoBridge on your phone\n2. Tap Pair glasses (show QR)\n3. Point here"
            setPadding(0, (d * 10).toInt(), 0, (d * 10).toInt())
        })

        root.addView(Button(this).apply {
            text = "Scan QR"
            setOnClickListener {
                val opts = ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    setPrompt("Point at the phone QR")
                    setBeepEnabled(false)
                    setOrientationLocked(true)
                }
                scanner.launch(opts)
            }
        })

        val manual = EditText(this).apply { hint = "{\"v\":1,\"h\":...}" }
        root.addView(manual)
        root.addView(Button(this).apply {
            text = "Save payload"
            setOnClickListener {
                val p = QrPayload.decode(manual.text.toString().trim())
                if (p == null) {
                    Toast.makeText(this@PairingActivity, "Invalid payload", Toast.LENGTH_SHORT).show()
                } else {
                    PairingStore.save(this@PairingActivity, p)
                    Toast.makeText(this@PairingActivity, "Saved - reconnecting", Toast.LENGTH_SHORT).show()
                    startService(Intent(this@PairingActivity, BridgeClientService::class.java)
                        .putExtra("repair", true))
                    finish()
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
                    startService(Intent(this, BridgeClientService::class.java).putExtra("repair", true))
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
