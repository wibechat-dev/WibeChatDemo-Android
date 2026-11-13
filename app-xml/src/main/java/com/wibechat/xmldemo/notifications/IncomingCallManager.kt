package com.wibechat.xmldemo.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.wibechat.xmldemo.MainActivity
import com.wibechat.xmldemo.R
import com.wibechat.demo.core.CoreServices
import com.wibechat.demo.core.model.CallLogStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Date

/**
 * Native replacement for flutter_callkit_incoming on Android: a ringing
 * CallStyle notification with Accept/Decline, a full-screen ringing activity
 * (also over the lock screen), a 30 s ring timeout, and a missed-call notice.
 *
 * Push payload (shared with the Flutter app): `type` = call | video_call,
 * `roomId`, `title` (caller name), `callerId`.
 */
object IncomingCallManager {

    const val RING_TIMEOUT_MS = 30_000L

    private val _changed = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    /** Emits whenever a ringing call is answered, declined, timed out or cancelled. */
    val changed: SharedFlow<Unit> = _changed.asSharedFlow()

    fun isRinging(context: Context, callId: String): Boolean = PendingCallStore.get(context, callId) != null

    fun get(context: Context, callId: String): IncomingCall? = PendingCallStore.get(context, callId)

    fun show(context: Context, call: IncomingCall) {
        PendingCallStore.put(context, call)
        scheduleTimeout(context, call)

        if (!canNotify(context)) return
        val notification = buildRingingNotification(context, call)
        runCatching { NotificationManagerCompat.from(context).notify(call.notificationId, notification) }
    }

    /** User tapped Accept. Returns the call to join, or null if it already ended. */
    fun accept(context: Context, callId: String): IncomingCall? {
        val call = PendingCallStore.take(context, callId) ?: return null
        stopRinging(context, call)
        return call
    }

    /** User tapped Decline: tell the caller and log it, without opening the app. */
    suspend fun decline(context: Context, callId: String) {
        val call = PendingCallStore.take(context, callId) ?: return
        stopRinging(context, call)
        notifyCaller(call, "Call declined")
        logNotAnswered(call, CallLogStatus.Declined)
    }

    /** Nobody answered within [RING_TIMEOUT_MS]. */
    suspend fun timeout(context: Context, callId: String) {
        val call = PendingCallStore.take(context, callId) ?: return
        stopRinging(context, call)
        showMissedCall(context, call)
        notifyCaller(call, "Missed call")
        logNotAnswered(call, CallLogStatus.Missed)
    }

    /** The caller hung up (an `end_call` push): stop every ringing call, like CallKit's endAllCalls. */
    fun cancelAll(context: Context) {
        PendingCallStore.takeAll(context).forEach { stopRinging(context, it) }
    }

    // ---- internals --------------------------------------------------------

    private fun stopRinging(context: Context, call: IncomingCall) {
        NotificationManagerCompat.from(context).cancel(call.notificationId)
        context.getSystemService(AlarmManager::class.java).cancel(timeoutIntent(context, call.id))
        _changed.tryEmit(Unit)
    }

    private suspend fun notifyCaller(call: IncomingCall, desc: String) {
        val callerId = call.callerId ?: return
        CoreServices.push.sendCallEnded(toUserId = callerId, roomId = call.roomId, desc = desc)
    }

    /** The only case where a call ends without the call screen ever opening to log it. */
    private suspend fun logNotAnswered(call: IncomingCall, status: CallLogStatus) {
        val uid = CoreServices.auth.currentUser?.uid ?: return
        val callerId = call.callerId ?: return
        runCatching {
            CoreServices.callLogs.logCall(
                uid = uid,
                roomId = call.roomId,
                otherUid = callerId,
                otherLabel = call.callerName,
                video = call.video,
                outgoing = false,
                status = status,
                startedAt = Date(),
                durationSeconds = 0,
            )
        }
    }

    private fun buildRingingNotification(context: Context, call: IncomingCall): Notification {
        val caller = Person.Builder().setName(call.callerName).setImportant(true).build()

        val answer = PendingIntent.getActivity(
            context,
            call.notificationId,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_ACCEPT_CALL)
                .putExtra(MainActivity.EXTRA_CALL_ID, call.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val decline = PendingIntent.getBroadcast(
            context,
            call.notificationId,
            Intent(context, CallActionReceiver::class.java)
                .setAction(CallActionReceiver.ACTION_DECLINE)
                .putExtra(CallActionReceiver.EXTRA_CALL_ID, call.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val fullScreen = PendingIntent.getActivity(
            context,
            call.notificationId,
            IncomingCallActivity.intent(context, call.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, NotificationChannels.INCOMING_CALLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(call.callerName)
            .setContentText(if (call.video) "Incoming video call" else "Incoming call")
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, decline, answer).setIsVideo(call.video))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(RING_TIMEOUT_MS)
            .build()
            .apply { flags = flags or Notification.FLAG_INSISTENT } // keep ringing until handled
    }

    private fun showMissedCall(context: Context, call: IncomingCall) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context,
            call.notificationId,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.MISSED_CALLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Missed call")
            .setContentText(call.callerName)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(call.notificationId + 1, notification) }
    }

    private fun scheduleTimeout(context: Context, call: IncomingCall) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val at = SystemClock.elapsedRealtime() + RING_TIMEOUT_MS
        val pending = timeoutIntent(context, call.id)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()
        if (canExact) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
        }
    }

    private fun timeoutIntent(context: Context, callId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        callId.hashCode(),
        Intent(context, CallActionReceiver::class.java)
            .setAction(CallActionReceiver.ACTION_TIMEOUT)
            .putExtra(CallActionReceiver.EXTRA_CALL_ID, callId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
