package com.wibechat.xmldemo.notifications

import android.content.Context
import org.json.JSONObject

/** A ringing call that hasn't been answered, declined or timed out yet. */
data class IncomingCall(
    /** Random per ring; only identifies this ringing notification on this phone. */
    val id: String,
    val roomId: String,
    val callerName: String,
    val callerId: String?,
    val video: Boolean,
) {
    val notificationId: Int get() = id.hashCode()

    fun toJson(): String = JSONObject()
        .put("id", id).put("roomId", roomId).put("callerName", callerName)
        .put("callerId", callerId).put("video", video)
        .toString()

    companion object {
        fun fromJson(json: String): IncomingCall? = runCatching {
            val o = JSONObject(json)
            IncomingCall(
                id = o.getString("id"),
                roomId = o.getString("roomId"),
                callerName = o.optString("callerName", "Unknown"),
                callerId = o.optString("callerId").takeIf { it.isNotBlank() && it != "null" },
                video = o.optBoolean("video"),
            )
        }.getOrNull()
    }
}

/**
 * Ringing calls, persisted so a decline or timeout still finds the caller
 * after the process was killed (the role `_pendingCalls` + CallKit's own
 * storage play in the Flutter app).
 */
internal object PendingCallStore {
    private const val PREFS = "pending_calls"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun put(context: Context, call: IncomingCall) {
        prefs(context).edit().putString(call.id, call.toJson()).apply()
    }

    fun get(context: Context, id: String): IncomingCall? =
        prefs(context).getString(id, null)?.let(IncomingCall::fromJson)

    /** Removes and returns the call, or null if it was already handled. */
    @Synchronized
    fun take(context: Context, id: String): IncomingCall? {
        val call = get(context, id) ?: return null
        prefs(context).edit().remove(id).commit()
        return call
    }

    @Synchronized
    fun takeAll(context: Context): List<IncomingCall> {
        val all = prefs(context).all.values.mapNotNull { (it as? String)?.let(IncomingCall::fromJson) }
        prefs(context).edit().clear().commit()
        return all
    }
}
