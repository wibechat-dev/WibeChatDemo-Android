package com.wibechat.demo.core.data

import com.wibechat.demo.core.config.AppConfig
import com.wibechat.demo.core.network.ApiService
import org.json.JSONObject

/**
 * `data.type` values in pushes. Shared with the Flutter app and the
 * `sendNotification` function, so both apps can ring each other.
 */
object NotificationType {
    const val CALL = "call"
    const val VIDEO_CALL = "video_call"
    const val END_CALL = "end_call"

    fun isIncomingCall(type: String?) = type == CALL || type == VIDEO_CALL
}

/** Keys inside a push's `data` payload. */
object PushKeys {
    const val TYPE = "type"
    const val ROOM_ID = "roomId"
    const val TITLE = "title"
    const val BODY = "body"
    const val CALLER_ID = "callerId"
}

/**
 * Calls the deployed `sendNotification` function, which looks up the target
 * user's FCM token in Firestore and sends through FCM. Call invites go out
 * data-only, so this app decides how to ring.
 */
class PushNotificationApi(private val api: ApiService) {

    /** Best-effort: a failed push must never break the caller's own flow. */
    suspend fun send(userId: String, title: String, desc: String, data: Map<String, String> = emptyMap()) {
        runCatching {
            api.post(
                AppConfig.sendNotificationEndpoint,
                JSONObject().apply {
                    put("user_id", userId)
                    put("title", title)
                    put("desc", desc)
                    put("data", JSONObject(data))
                },
            )
        }
    }

    /** Rings [toUserId]: shows their incoming-call screen for [roomId]. */
    suspend fun sendCallInvite(toUserId: String, roomId: String, callerId: String, callerLabel: String, video: Boolean, desc: String) =
        send(
            userId = toUserId,
            title = if (video) "Incoming video call" else "Incoming call",
            desc = desc,
            data = mapOf(
                PushKeys.TYPE to if (video) NotificationType.VIDEO_CALL else NotificationType.CALL,
                PushKeys.ROOM_ID to roomId,
                PushKeys.TITLE to callerLabel,
                PushKeys.CALLER_ID to callerId,
            ),
        )

    /** Tells [toUserId] the call in [roomId] ended (stops their ringing / closes their call). */
    suspend fun sendCallEnded(toUserId: String, roomId: String, desc: String) =
        send(
            userId = toUserId,
            title = "Call ended",
            desc = desc,
            data = mapOf(PushKeys.TYPE to NotificationType.END_CALL, PushKeys.ROOM_ID to roomId),
        )
}
