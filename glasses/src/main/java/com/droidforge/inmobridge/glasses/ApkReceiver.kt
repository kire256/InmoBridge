package com.droidforge.inmobridge.glasses

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Handler
import android.os.Looper
import com.droidforge.inmobridge.core.BridgeMessage
import com.droidforge.inmobridge.core.Envelope
import java.io.File
import java.util.Base64

/**
 * Receives an APK streamed from the phone (apk_begin/chunk/end envelopes),
 * writes it to cache and installs it via PackageInstaller. Progress and the
 * final result travel back to the phone as event envelopes.
 */
object ApkReceiver {

    private val main = Handler(Looper.getMainLooper())
    private var file: File? = null
    private var expectedSize = 0L
    private var expectedChunks = 0
    private var receivedChunks = 0
    private var name = "app.apk"
    private var lastReport = 0L

    fun onEnvelope(env: Envelope, reply: (Envelope) -> Unit) {
        when (env.type) {
            BridgeMessage.TYPE_APK_BEGIN -> {
                name = env.payload.optString("name", "app.apk").ifBlank { "app.apk" }
                expectedSize = env.payload.optLong("size", 0L)
                expectedChunks = 0
                receivedChunks = 0
                file = File(AppHolder.ctx.cacheDir, "upload.apk").apply {
                    delete(); createNewFile()
                }
                reply(event("apk_started", """{"name":"$name"}"""))
            }
            BridgeMessage.TYPE_APK_CHUNK -> {
                val f = file ?: return
                val bytes = runCatching {
                    Base64.getDecoder().decode(env.payload.optString("b64"))
                }.getOrNull() ?: return
                f.appendBytes(bytes)
                receivedChunks++
                val now = System.currentTimeMillis()
                if (now - lastReport > 400) { // throttle progress events
                    lastReport = now
                    reply(event("apk_progress", """{"chunks":$receivedChunks,"bytes":${f.length()}}"""))
                }
            }
            BridgeMessage.TYPE_APK_END -> {
                val f = file
                expectedChunks = env.payload.optInt("chunks", 0)
                if (f == null || !f.exists()) {
                    reply(event("apk_result", """{"ok":false,"error":"no transfer in progress"}"""))
                    return
                }
                if (expectedChunks in 1..receivedChunks && receivedChunks != expectedChunks) {
                    reply(event("apk_result", """{"ok":false,"error":"chunk mismatch: got $receivedChunks want $expectedChunks"}"""))
                    f.delete(); file = null
                    return
                }
                if (expectedSize > 0 && kotlin.math.abs(f.length() - expectedSize) > 4096) {
                    reply(event("apk_result", """{"ok":false,"error":"size mismatch: ${f.length()} vs $expectedSize"}"""))
                    f.delete(); file = null
                    return
                }
                reply(event("apk_progress", """{"chunks":$receivedChunks,"bytes":${f.length()},"phase":"installing"}"""))
                install(f, reply)
            }
        }
    }

    private fun install(apk: File, reply: (Envelope) -> Unit) {
        val ctx = AppHolder.ctx
        try {
            val pi = ctx.packageManager.packageInstaller
            val sessionId = pi.createSession(
                PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            )
            val session = pi.openSession(sessionId)
            session.use { s ->
                apk.inputStream().use { input ->
                    s.openWrite("base.apk", 0, apk.length()).use { out ->
                        input.copyTo(out, 64 * 1024)
                        s.fsync(out)
                    }
                }
                val intent = android.content.Intent(ctx, InstallDoneReceiver::class.java)
                    .setAction(INSTALL_ACTION)
                    .putExtra("session", sessionId)
                val pending = android.app.PendingIntent.getBroadcast(
                    ctx, sessionId, intent,
                    android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
                s.commit(pending.intentSender)
            }
            pendingReplies[sessionId] = reply
        } catch (e: Exception) {
            reply(event("apk_result", """{"ok":false,"error":"${e.message?.replace("\"", "'")}"}"""))
            apk.delete(); file = null
        }
    }

    /** Called by InstallDoneReceiver when the session reports a status. */
    fun onInstallStatus(sessionId: Int, status: Int, message: String?) {
        val reply = pendingReplies.remove(sessionId) ?: return
        if (status == PackageInstaller.STATUS_SUCCESS) {
            reply(event("apk_result", """{"ok":true,"name":"$name"}"""))
        } else {
            val pending = status == PackageInstaller.STATUS_PENDING_USER_ACTION
            reply(event(
                "apk_result",
                """{"ok":false,"pending_user":$pending,"status":$status,"error":"${message?.replace("\"", "'") ?: "install failed"}"}"""
            ))
        }
        file?.delete(); file = null
    }

    private fun event(name: String, dataJson: String): Envelope =
        Envelope(System.nanoTime(), BridgeMessage.TYPE_EVENT,
            org.json.JSONObject().put("name", name).put("data", org.json.JSONObject(dataJson)))

    private val pendingReplies = java.util.concurrent.ConcurrentHashMap<Int, (Envelope) -> Unit>()

    const val INSTALL_ACTION = "com.droidforge.inmobridge.glasses.INSTALL_DONE"

    /** Application holder for non-activity contexts. */
    object AppHolder {
        lateinit var ctx: Context
    }
}
