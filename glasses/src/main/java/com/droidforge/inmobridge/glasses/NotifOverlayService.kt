package com.droidforge.inmobridge.glasses

import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.WindowManager
import com.droidforge.inmobridge.core.Envelope
import com.droidforge.inmobridge.core.BridgeMessage
import com.droidforge.inmobridge.core.NotifSpec

/**
 * Owns the notification overlay window (SYSTEM_ALERT_WINDOW) plus screen
 * wake, sound and vibration. Runs inside the always-on bridge process, so a
 * notification can light up the display even when it is off - no activity
 * start restrictions apply to overlay windows.
 */
object NotifOverlay {

    private val handler = Handler(Looper.getMainLooper())
    private var view: NotifOverlayView? = null
    private var windowAdded = false

    // Display settings pushed from the phone (config envelope).
    @Volatile var maxLines: Int = 3
        private set
    @Volatile var autoScrollMs: Long = 4000L
        private set
    @Volatile var timeoutMs: Long = 6000L
        private set

    private var wakeLock: PowerManager.WakeLock? = null
    private var tone: ToneGenerator? = null

    /** Must be called once from an attached context (BridgeClientService). */
    fun init(context: android.content.Context) {
        if (view != null) return
        handler.post {
            view = NotifOverlayView(context).apply { visibility = android.view.View.GONE }
        }
        this.appContext = context.applicationContext
    }
    private lateinit var appContext: android.content.Context

    /** Handle an envelope from the phone. */
    fun onEnvelope(env: Envelope) {
        when (env.type) {
            BridgeMessage.TYPE_NOTIF -> {
                NotifSpec.fromJson(env.payload)?.let { present(it) }
            }
            BridgeMessage.TYPE_CONFIG -> {
                NotifOverlay.applyConfig(env)
            }
        }
    }

    /** Apply display settings from a config envelope. */
    fun applyConfig(env: Envelope) {
        maxLines = env.payload.optInt("maxLines", 3).coerceIn(1, 8)
        autoScrollMs = env.payload.optLong("autoScrollMs", 4000L)
        timeoutMs = env.payload.optLong("timeout", 6000L)
    }

    private fun present(spec: NotifSpec) {
        val ctx = appContext
        handler.post {
            val v = view ?: return@post
            if (!windowAdded) {
                val wm = ctx.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                }
                runCatching { wm.addView(v, params); windowAdded = true }
                    .onFailure { return@post }
            }
            v.visibility = android.view.View.VISIBLE
            v.show(spec)
        }
        wakeScreen(spec.timeoutMs)
        playSound(spec.sound)
        vibrate(spec.vibrate)
    }

    /** Light the display up even from keyguard/off state. */
    private fun wakeScreen(timeoutMs: Long) {
        val ctx = appContext
        val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        runCatching {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "inmobridge:notif"
            ).apply { acquire(timeoutMs + 2000) }
        }
        // Newer devices also want this when locked:
        if (Build.VERSION.SDK_INT >= 27) {
            runCatching {
                val activityManager = ctx.getSystemService(android.app.Activity::class.java)
                // no-op: overlay shows over keyguard thanks to TYPE_APPLICATION_OVERLAY
            }
        }
    }

    private fun playSound(id: String) {
        val toneId = when (id) {
            "ping" -> ToneGenerator.TONE_PROP_BEEP
            "chime" -> ToneGenerator.TONE_PROP_BEEP2
            "ring" -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
            "alert" -> ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE
            else -> return
        }
        runCatching {
            tone?.release()
            tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).also {
                it.startTone(toneId, 250)
            }
        }
    }

    private fun vibrate(id: String) {
        if (id == "none") return
        val ctx = appContext
        val vib = ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator ?: return
        val pattern = when (id) {
            "tick" -> longArrayOf(0, 40)
            "pulse" -> longArrayOf(0, 60, 80, 60)
            "ring" -> longArrayOf(0, 300, 150, 300, 150, 300)
            else -> return
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) vib.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
            else @Suppress("DEPRECATION") vib.vibrate(pattern, -1)
        }
    }
}
