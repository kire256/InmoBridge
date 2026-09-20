package com.droidforge.inmobridge.phone

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.droidforge.inmobridge.core.QrPayload
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Shows the pairing QR: selected address + port + token. Tapping an address
 * row regenerates the code. Also displays the raw payload for manual entry
 * on the glasses.
 */
class PairingActivity : Activity() {

    private lateinit var qrImage: ImageView
    private lateinit var payloadText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (resources.displayMetrics.density * 16).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Pair glasses"
            textSize = 22f
        })

        val addrs = PairingManager.addresses()
        if (addrs.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "No network addresses — connect Wi-Fi or Tailscale first."
            })
        }
        var selected = addrs.firstOrNull()

        val addrList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addrs.forEach { a ->
            addrList.addView(Button(this).apply {
                text = "${a.name}\\n${a.host}  (tap to select)"
                isAllCaps = false
                setOnClickListener {
                    selected = a
                    render(selected)
                }
            })
        }
        root.addView(addrList)

        qrImage = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 720
            )
        }
        root.addView(qrImage)

        payloadText = TextView(this).apply { setTextIsSelectable(true) }
        root.addView(payloadText)

        render(selected)
        setContentView(android.widget.ScrollView(this).apply { addView(root) })
    }

    private fun render(addr: PairingManager.Addr?) {
        if (addr == null) return
        val payload = QrPayload.encode(addr.host, BridgeService.PORT, PairingManager.token(this), "Erik phone")
        qrImage.setImageBitmap(encodeQr(payload))
        payloadText.text = "Payload:\\n$payload"
    }

    private fun encodeQr(content: String): Bitmap {
        val matrix = QRCodeWriter().encode(
            content, BarcodeFormat.QR_CODE, 720, 720
        )
        val bmp = Bitmap.createBitmap(720, 720, Bitmap.Config.RGB_565)
        for (x in 0 until 720) {
            for (y in 0 until 720) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
