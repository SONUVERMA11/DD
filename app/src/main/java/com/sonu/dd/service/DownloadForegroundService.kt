package com.sonu.dd.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sonu.dd.DDApplication
import com.sonu.dd.MainActivity
import com.sonu.dd.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DownloadForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundService()
            ACTION_STOP -> stopSelf()
            ACTION_PAUSE_ALL -> pauseAllDownloads()
            ACTION_RESUME_ALL -> resumeAllDownloads()
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = buildNotification("Downloading…", "DD is downloading files")
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun pauseAllDownloads() {
        updateNotification("Downloads paused", "All downloads have been paused")
    }

    private fun resumeAllDownloads() {
        updateNotification("Downloading…", "DD is downloading files")
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseIntent = PendingIntent.getService(
            this, 1, Intent(this, DownloadForegroundService::class.java).apply { action = ACTION_PAUSE_ALL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val resumeIntent = PendingIntent.getService(
            this, 2, Intent(this, DownloadForegroundService::class.java).apply { action = ACTION_RESUME_ALL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, DDApplication.CHANNEL_DOWNLOADS)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Pause All", pauseIntent)
            .addAction(android.R.drawable.ic_media_play, "Resume All", resumeIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val notification = buildNotification(title, text)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.sonu.dd.START"
        const val ACTION_STOP = "com.sonu.dd.STOP"
        const val ACTION_PAUSE_ALL = "com.sonu.dd.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "com.sonu.dd.RESUME_ALL"
    }
}
