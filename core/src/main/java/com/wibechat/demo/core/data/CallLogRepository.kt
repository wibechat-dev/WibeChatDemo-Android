package com.wibechat.demo.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.wibechat.demo.core.firebase.FirestoreConstants
import com.wibechat.demo.core.firebase.snapshots
import com.wibechat.demo.core.model.CallLogEntry
import com.wibechat.demo.core.model.CallLogStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Per-user history of 1:1 calls. Each side writes its own entry, so a
 * declined call reads "You declined" for the callee and "Declined" for the caller.
 */
class CallLogRepository(private val firestore: FirebaseFirestore) {

    private fun logsFor(uid: String) = firestore.collection(FirestoreConstants.USERS)
        .document(uid)
        .collection(FirestoreConstants.CALL_LOGS)

    suspend fun logCall(
        uid: String,
        roomId: String,
        otherUid: String,
        otherLabel: String,
        video: Boolean,
        outgoing: Boolean,
        status: CallLogStatus,
        startedAt: Date,
        durationSeconds: Int,
    ) {
        logsFor(uid).add(
            mapOf(
                "roomId" to roomId,
                "otherUid" to otherUid,
                "otherLabel" to otherLabel,
                "video" to video,
                "outgoing" to outgoing,
                "status" to status.wireName,
                "startedAt" to Timestamp(startedAt),
                "durationSeconds" to durationSeconds,
            ),
        ).await()
    }

    fun watchCallLogs(uid: String): Flow<List<CallLogEntry>> =
        logsFor(uid).orderBy("startedAt", Query.Direction.DESCENDING).limit(200).snapshots().map { snap ->
            snap.documents.map { doc ->
                CallLogEntry(
                    id = doc.id,
                    roomId = doc.getString("roomId").orEmpty(),
                    otherUid = doc.getString("otherUid").orEmpty(),
                    otherLabel = doc.getString("otherLabel").orEmpty(),
                    video = doc.getBoolean("video") == true,
                    outgoing = doc.getBoolean("outgoing") == true,
                    status = CallLogStatus.fromWire(doc.getString("status")),
                    startedAt = doc.getTimestamp("startedAt")?.toDate() ?: Date(),
                    durationSeconds = doc.getLong("durationSeconds")?.toInt() ?: 0,
                )
            }
        }
}
