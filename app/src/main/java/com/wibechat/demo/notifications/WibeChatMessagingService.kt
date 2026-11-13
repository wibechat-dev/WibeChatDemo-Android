package com.wibechat.demo.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.wibechat.demo.MainActivity
import com.wibechat.demo.R
import com.wibechat.demo.call.ActiveCall
import com.wibechat.demo.core.CoreServices
import com.wibechat.demo.core.data.NotificationType
import com.wibechat.demo.core.data.PushKeys
import java.util.UUID

/**
 * Routes every push by `data.type`:
 * - `call` / `video_call` → ring (IncomingCallManager)
 * - `end_call` → stop ringing and close that call if it's open
 * - anything else → a normal notification
 *
 * Call pushes are data-only (see the `sendNotification` function), so this
 * runs in every app state, including when the app was killed.
 */
class WibeChatMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        CoreServices.auth.onNewFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data[PushKeys.TYPE]) {
            NotificationType.CALL, NotificationType.VIDEO_CALL -> showIncomingCall(message)

            NotificationType.END_CALL -> {
                IncomingCallManager.cancelAll(this)
                data[PushKeys.ROOM_ID]?.takeIf { it.isNotBlank() }?.let(ActiveCall::notifyCallEndedRemotely)
            }

            // onMessageReceived only sees `notification` pushes while in the
            // foreground (the system shows them otherwise), so no duplicates.
            else -> showGeneralNotification(message)
        }
    }

    private fun showIncomingCall(message: RemoteMessage) {
        val data = message.data
        val roomId = data[PushKeys.ROOM_ID]?.takeIf { it.isNotBlank() } ?: return
        IncomingCallManager.show(
            this,
            IncomingCall(
                id = UUID.randomUUID().toString(),
                roomId = roomId,
                callerName = data[PushKeys.TITLE] ?: message.notification?.title ?: "Unknown",
                callerId = data[PushKeys.CALLER_ID],
                video = data[PushKeys.TYPE] == NotificationType.VIDEO_CALL,
            ),
        )
    }

    private fun showGeneralNotification(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data[PushKeys.TITLE]
        val body = message.notification?.body ?: message.data[PushKeys.BODY]
        if (title == null && body == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        // Tapping opens the app; MainActivity opens the room if the data says so.
        val tap = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        message.data.forEach { (k, v) -> tap.putExtra(k, v) }
        val id = message.messageId?.hashCode() ?: System.currentTimeMillis().toInt()
        val pending = PendingIntent.getActivity(this, id, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, NotificationChannels.GENERAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(id, notification) }
    }
}
