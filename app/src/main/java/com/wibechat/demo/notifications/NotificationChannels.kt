package com.wibechat.demo.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

object NotificationChannels {
    /** Same id the `sendNotification` function puts on non-call pushes. */
    const val GENERAL = "high_importance_channel"
    const val INCOMING_CALLS = "incoming_calls"
    const val MISSED_CALLS = "missed_calls"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(GENERAL, "Calls & messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming calls and chat notifications."
            },
        )

        nm.createNotificationChannel(
            NotificationChannel(INCOMING_CALLS, "Incoming Call", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rings when someone calls you."
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 600, 800, 600)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )

        nm.createNotificationChannel(
            NotificationChannel(MISSED_CALLS, "Missed Call", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Calls you didn't answer."
            },
        )
    }
}
