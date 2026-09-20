package com.droidforge.inmobridge.phone

import android.content.Context
import java.net.NetworkInterface
import java.security.SecureRandom

/**
 * Pairing state: per-install token + the network addresses the phone can be
 * reached on. The token is generated once and stored; the glasses must present
 * it in their hello envelope.
 */
object PairingManager {

    private const val PREFS = "pairing"
    private const val KEY_TOKEN = "token"

    /** Stable per-install token (regenerating un-pairs every glasses device). */
    fun token(context: Context): String {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.getString(KEY_TOKEN, null)?.let { return it }
        val bytes = ByteArray(9)
        SecureRandom().nextBytes(bytes)
        val t = bytes.joinToString("") { "%02x".format(it) }
        sp.edit().putString(KEY_TOKEN, t).apply()
        return t
    }

    data class Addr(val name: String, val host: String)

    /** Non-loopback IPv4 addresses, friendliest names first. */
    fun addresses(): List<Addr> {
        val out = ArrayList<Addr>()
        runCatching {
            val en = NetworkInterface.getNetworkInterfaces() ?: return out
            for (ni in en) {
                for (ia in ni.interfaceAddresses) {
                    val host = ia.address?.hostAddress ?: continue
                    if (host.contains(':')) continue  // IPv6
                    if (host == "127.0.0.1") continue
                    val label = when {
                        ni.name.startsWith("tailscale") || host.startsWith("100.") -> "Tailscale (${ni.name})"
                        ni.name.startsWith("wlan") || ni.name.startsWith("sw") -> "Wi-Fi (${ni.name})"
                        ni.name.startsWith("eth") -> "Ethernet (${ni.name})"
                        else -> ni.name
                    }
                    out.add(Addr(label, host))
                }
            }
        }
        return out.sortedBy { it.name.contains("Tailscale").not() }
    }
}
