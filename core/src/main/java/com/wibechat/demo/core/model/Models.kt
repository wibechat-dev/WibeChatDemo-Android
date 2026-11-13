package com.wibechat.demo.core.model

import java.util.Date

/** The signed-in user, as the UI needs it. */
data class SessionUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
) {
    /** Display name, falling back to email, then "Someone". */
    val callerLabel: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: email ?: "Someone"
}

/**
 * A workspace: one WibeChat organisation (API server + client id). Users who
 * enter the same server URL and client id share a workspace (people, rooms,
 * calls). The API key never comes back to the app; only [apiKeyMasked] does.
 */
data class Workspace(
    val id: String,
    /** Display name; the API host, e.g. "api.wibechat.com". */
    val name: String,
    /** WibeChat API, e.g. https://api.wibechat.com */
    val apiUrl: String,
    /** Media server the calls use, e.g. wss://call.wibechat.com (from the server, never typed). */
    val serverUrl: String,
    val clientId: String,
    /** e.g. "••••Xi6D" */
    val apiKeyMasked: String,
)

/** A `users/{uid}` document. */
data class AppUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
) {
    /** Display name, falling back to email, falling back to the uid. */
    val label: String
        get() = displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: email?.trim()?.takeIf { it.isNotEmpty() }
            ?: uid
}

/** A `rooms/{roomId}` document. */
data class RoomInfo(
    val id: String,
    val name: String,
    val createdBy: String?,
    val createdByEmail: String?,
    /** True for 1:1 call rooms (`dm-…`), which the Rooms tab hides. */
    val direct: Boolean,
    /** Millis since epoch; 0 while the server timestamp is pending. Used for newest-first ordering. */
    val createdAtMillis: Long = 0,
)

/** A `rooms/{roomId}/participants/{uid}` presence document. */
data class PresenceEntry(
    val uid: String,
    val label: String,
    val photoUrl: String?,
)

/** Stored as the enum name (`completed`, `noAnswer`, …) to match the Flutter app. */
enum class CallLogStatus(val wireName: String) {
    Completed("completed"),
    NoAnswer("noAnswer"),
    Missed("missed"),
    Declined("declined"),
    Failed("failed"),
    ;

    companion object {
        fun fromWire(value: String?): CallLogStatus = entries.firstOrNull { it.wireName == value } ?: Completed
    }
}

/** A `users/{uid}/callLogs/{id}` document, written from that user's point of view. */
data class CallLogEntry(
    val id: String,
    val roomId: String,
    val otherUid: String,
    val otherLabel: String,
    val video: Boolean,
    val outgoing: Boolean,
    val status: CallLogStatus,
    val startedAt: Date,
    val durationSeconds: Int,
)
