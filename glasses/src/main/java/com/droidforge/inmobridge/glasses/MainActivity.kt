package com.droidforge.inmobridge.glasses

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.droidforge.inmobridge.glasses.BuildConfig

/** Glasses app entry: bridge status + pairing. The real UI is the overlay. */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val pad = (d * 20).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "InmoBridge Relay"
            textSize = 26f
        })
        val st = TextView(this).apply {
            textSize = 15f
            setPadding(0, (d * 10).toInt(), 0, (d * 10).toInt())
        }
        root.addView(st)
        BridgeState.addListener { s ->
            runOnUiThread {
                st.text = "Bridge: " + when (s) {
                    BridgeState.CONNECTED -> "connected to phone"
                    BridgeState.CONNECTING -> "connecting..."
                    BridgeState.REJECTED -> "rejected - re-pair"
                    else -> "disconnected"
                }
            }
        }

        root.addView(Button(this).apply {
            text = "Pair with phone"
            setOnClickListener { startActivity(Intent(this@MainActivity, PairingActivity::class.java)) }
        })
        setContentView(root)

        // Keep the link service alive whenever the app is opened.
        startForegroundService(Intent(this, BridgeClientService::class.java))
    }
}
