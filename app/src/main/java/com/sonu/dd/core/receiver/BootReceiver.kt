package com.sonu.dd.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sonu.dd.service.DownloadForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Auto-resume downloads on boot if enabled
            // In production, check DataStore for auto-resume preference
            // and pending downloads in Room database
        }
    }
}
