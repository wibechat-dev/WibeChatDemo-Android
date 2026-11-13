package com.wibechat.demo.core.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.wibechat.demo.core.firebase.FirestoreConstants
import com.wibechat.demo.core.firebase.snapshots
import com.wibechat.demo.core.model.PresenceEntry
import com.wibechat.demo.core.model.RoomInfo
import com.wibechat.demo.core.model.SessionUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Rooms live in Firestore and belong to one workspace. The token function
 * only lets a user into a room of their own workspace that isn't `open: false`.
 *
 * Room ids carry the workspace so names can repeat across workspaces:
 * `<workspaceId>__<name>` for named rooms, `dm-<workspaceId>-<uidA>-<uidB>` for 1:1.
 */
class RoomRepository(private val firestore: FirebaseFirestore) {

    private val rooms get() = firestore.collection(FirestoreConstants.ROOMS)

    /** Newest first. Sorted here rather than in the query, so no composite index is needed. */
    fun watchRooms(workspaceId: String): Flow<List<RoomInfo>> =
        rooms.whereEqualTo(FirestoreConstants.WORKSPACE_ID, workspaceId).snapshots().map { snap ->
            snap.documents.map { doc ->
                RoomInfo(
                    id = doc.id,
                    name = doc.getString("name") ?: doc.id,
                    createdBy = doc.getString("createdBy"),
                    createdByEmail = doc.getString("createdByEmail"),
                    direct = doc.getBoolean("direct") == true,
                    createdAtMillis = doc.getTimestamp("createdAt")?.toDate()?.time ?: Long.MAX_VALUE,
                )
            }.sortedByDescending { it.createdAtMillis }
        }

    fun roomId(workspaceId: String, name: String) = "${workspaceId}__$name"

    suspend fun createRoom(name: String, creator: SessionUser, workspaceId: String) {
        rooms.document(roomId(workspaceId, name)).set(
            mapOf(
                "name" to name,
                "open" to true,
                FirestoreConstants.WORKSPACE_ID to workspaceId,
                "createdBy" to creator.uid,
                "createdByEmail" to creator.email,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    /** Firestore rules only allow the room's creator to do this. */
    suspend fun deleteRoom(roomId: String) {
        rooms.document(roomId).delete().await()
    }

    /**
     * Best-effort presence: written on join, removed on leave. Not
     * authoritative (a killed app won't clean up).
     */
    fun watchParticipants(roomId: String): Flow<List<PresenceEntry>> =
        rooms.document(roomId).collection(FirestoreConstants.PARTICIPANTS).snapshots().map { snap ->
            snap.documents.map { doc ->
                val label = doc.getString("displayName")?.trim()?.takeIf { it.isNotEmpty() }
                    ?: doc.getString("email")?.trim()?.takeIf { it.isNotEmpty() }
                    ?: doc.id
                PresenceEntry(uid = doc.id, label = label, photoUrl = doc.getString("photoUrl"))
            }
        }

    suspend fun joinPresence(roomId: String, user: SessionUser) {
        rooms.document(roomId).collection(FirestoreConstants.PARTICIPANTS).document(user.uid).set(
            mapOf(
                "uid" to user.uid,
                "email" to user.email,
                "displayName" to user.displayName,
                "photoUrl" to user.photoUrl,
                "joinedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun leavePresence(roomId: String, uid: String) {
        rooms.document(roomId).collection(FirestoreConstants.PARTICIPANTS).document(uid).delete().await()
    }

    /** Same id on both phones, whoever calls first: `dm-<workspace>-<smaller uid>-<larger uid>`. */
    fun directRoomId(workspaceId: String, uidA: String, uidB: String): String {
        val (first, second) = listOf(uidA, uidB).sorted()
        return "${FirestoreConstants.DIRECT_ROOM_PREFIX}$workspaceId-$first-$second"
    }

    /**
     * Returns the 1:1 room id, creating the room doc if needed. Only the
     * first caller creates it, since rules only let the creator update it.
     */
    suspend fun getOrCreateDirectRoom(
        currentUser: SessionUser,
        otherUid: String,
        otherLabel: String?,
        workspaceId: String,
    ): String {
        val roomId = directRoomId(workspaceId, currentUser.uid, otherUid)
        val doc = rooms.document(roomId)
        if (!doc.get().await().exists()) {
            doc.set(
                mapOf(
                    "name" to (otherLabel ?: roomId),
                    "open" to true,
                    "direct" to true,
                    FirestoreConstants.WORKSPACE_ID to workspaceId,
                    "createdBy" to currentUser.uid,
                    "createdByEmail" to currentUser.email,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
        return roomId
    }

    /**
     * The other person's uid in a `dm-<workspace>-<uidA>-<uidB>` room, or null
     * for a regular room. Workspace ids and Firebase uids never contain `-`.
     */
    fun otherUidInDirectRoom(roomId: String, currentUid: String): String? {
        if (!roomId.startsWith(FirestoreConstants.DIRECT_ROOM_PREFIX)) return null
        val parts = roomId.removePrefix(FirestoreConstants.DIRECT_ROOM_PREFIX).split("-")
        if (parts.size != 3) return null
        val uids = parts.drop(1)
        if (currentUid !in uids) return null
        return if (uids[0] == currentUid) uids[1] else uids[0]
    }
}
