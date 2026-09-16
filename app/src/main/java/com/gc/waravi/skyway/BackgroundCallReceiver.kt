package com.gc.waravi.skyway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BackgroundCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if ("android.intent.action.BOOT_COMPLETED" == intent?.action) {
            context?.let {
                val pushIntent = Intent(context, SkywayService::class.java)
                ContextCompat.startForegroundService(context, pushIntent)
            }
        }
    }
}