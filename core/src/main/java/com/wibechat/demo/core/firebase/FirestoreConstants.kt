package com.wibechat.demo.core.firebase

/** Collection and field names, shared with the Flutter app and the Cloud Functions. */
object FirestoreConstants {
    const val USERS = "users"
    const val WORKSPACES = "workspaces"

    /** Field on users and rooms: which workspace they belong to. */
    const val WORKSPACE_ID = "workspaceId"
    const val ROOMS = "rooms"

    /** Subcollection of [ROOMS]: who is currently in the call (best-effort presence). */
    const val PARTICIPANTS = "participants"

    /** Subcollection of [USERS]: each user's own call history. */
    const val CALL_LOGS = "callLogs"

    /** Prefix of 1:1 rooms: `dm-<workspaceId>-<uidA>-<uidB>` with the uids sorted. */
    const val DIRECT_ROOM_PREFIX = "dm-"
}
