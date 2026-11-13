package com.wibechat.demo.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.wibechat.demo.core.firebase.FirestoreConstants
import com.wibechat.demo.core.firebase.snapshots
import com.wibechat.demo.core.model.AppUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Read access to the `users` collection (the People tab, "Add to call"). */
class UserRepository(private val firestore: FirebaseFirestore) {

    /** Everyone in [workspaceId]. Firestore rules reject any query that isn't scoped like this. */
    fun watchUsers(workspaceId: String): Flow<List<AppUser>> =
        firestore.collection(FirestoreConstants.USERS)
            .whereEqualTo(FirestoreConstants.WORKSPACE_ID, workspaceId)
            .snapshots().map { snap ->
            snap.documents.map { it.toAppUser() }
        }

    private fun DocumentSnapshot.toAppUser() = AppUser(
        uid = id,
        email = getString("email"),
        displayName = getString("displayName"),
        photoUrl = getString("photoUrl"),
    )
}
