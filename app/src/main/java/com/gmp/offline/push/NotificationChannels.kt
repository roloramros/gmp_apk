package com.gmp.offline.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val JOBS_CHANNEL_ID = "gmp_jobs"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            JOBS_CHANNEL_ID,
            "Montajes",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Asignaciones y cambios de estado de montajes"
        }
        manager.createNotificationChannel(channel)
    }
}
