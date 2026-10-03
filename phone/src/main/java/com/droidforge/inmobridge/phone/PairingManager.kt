package com.droidforge.inmobridge.phone

import android.content.Context
import java.net.NetworkInterface
import java.util.UUID

/** Per-install pairing token + the phone's candidate bridge addresses. */
object PairingManager {

    private const val PREFS = "pairing"
    private const val KEY_TOKEN = "token"

    /** Stable per-install token; regenerates only when app data is cleared. */
    fun token(ctx: Context): String {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.getString(KEY_TOKEN, null)?.let { return it }
        val t = UUID.randomUUID().toString().replace("-", "").take(24)
        sp.edit().putString(KEY_TOKEN, t).apply()
        return t
    }

    data class Addr(val name: String, val host: String)

    /** Candidate addresses (Tailscale 100.64/10 first, then LAN, loopback last). */
    fun addresses(): List<Addr> {
        val out = ArrayList<Addr>()
        runCatching {
            val ifs = NetworkInterface.getNetworkInterfaces() ?: return out
            for (nif in ifs.asSequence()) {
                if (!nif.isUp || nif.isLoopback()) continue
                for (ia in nif.inetAddresses) {
                    if (ia.isLoopbackAddress) continue
                    val host = ia.hostAddress?.substringBefore('%') ?: continue
                    if (host.contains(':')) continue // IPv6 skipped for QR brevity
                    val name = when {
                        nif.name.contains("tailscale", true) || host.startsWith("100.") -> "Tailscale"
                        nif.name.contains("wlan", true) -> "Wi-Fi"
                        else -> nif.name
                    }
                    out.add(Addr(name, host))
                }
            }
        }
        return out.sortedBy { addr -> when {
            addr.host.startsWith("100.") -> 0
            else -> 1
        } }
    }
}
