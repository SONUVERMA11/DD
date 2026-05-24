package com.sonu.dd

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DDApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val downloadChannel = NotificationChannel(
            CHANNEL_DOWNLOADS,
            "Active Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress of active downloads"
            setShowBadge(false)
        }

        val completionChannel = NotificationChannel(
            CHANNEL_COMPLETION,
            "Download Complete",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies when downloads are complete"
        }

        val conversionChannel = NotificationChannel(
            CHANNEL_CONVERSION,
            "Format Conversion",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress of format conversions"
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(downloadChannel, completionChannel, conversionChannel)
        )
    }

    companion object {
        const val CHANNEL_DOWNLOADS = "dd_downloads"
        const val CHANNEL_COMPLETION = "dd_completion"
        const val CHANNEL_CONVERSION = "dd_conversion"
    }
}
