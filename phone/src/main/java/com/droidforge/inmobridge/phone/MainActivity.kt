package com.droidforge.inmobridge.phone

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Minimal launcher activity: starts and stops the bridge service. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startForegroundService(Intent(this, BridgeService::class.java))
        finish()
    }
}
