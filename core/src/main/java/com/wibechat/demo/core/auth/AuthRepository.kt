package com.wibechat.demo.core.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.wibechat.demo.core.firebase.FirestoreConstants
import com.wibechat.demo.core.model.SessionUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Auth state as seen by the UI: still loading, signed out, or signed in. */
sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: SessionUser) : SessionState
}

/**
 * FirebaseAuth + the app's `users` collection. Mirrors the Flutter
 * `AuthRepository`: sign-up writes the profile doc, every sign-in stores the
 * device's FCM token so others can ring this user.
 */
class AuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
) {
    private val _session = MutableStateFlow<SessionState>(SessionState.Loading)

    /** Drives the auth gate: login screen vs home. */
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Uid whose FCM token was already saved this process, so we register once per session. */
    private var registeredUid: String? = null

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            publish(user)
            // Covers fresh sign-ins AND sessions restored at app start: without
            // this, a remembered login never stores this device's token and
            // nobody can ring it.
            if (user == null) {
                registeredUid = null
            } else if (user.uid != registeredUid) {
                registeredUid = user.uid
                scope.launch { registerFcmToken(user.uid) }
            }
        }
    }

    val currentUser: FirebaseUser? get() = auth.currentUser

    val sessionUser: SessionUser? get() = auth.currentUser?.toSessionUser()

    suspend fun idToken(): String? = auth.currentUser?.getIdToken(false)?.await()?.token

    suspend fun signUp(email: String, password: String, displayName: String): FirebaseUser {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user!!
        val name = displayName.trim()
        if (name.isNotEmpty()) {
            user.updateProfile(userProfileChangeRequest { this.displayName = name }).await()
            publish(user) // the auth listener fired before the name was set
        }
        users().document(user.uid).set(
            mapOf(
                "uid" to user.uid,
                "email" to user.email,
                "displayName" to name,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(), // keep the fcmToken the auth listener may already have written
        ).await()
        return user
    }

    suspend fun signIn(email: String, password: String): FirebaseUser {
        return auth.signInWithEmailAndPassword(email.trim(), password).await().user!!
    }

    /**
     * Signs in with a Google ID token obtained by the UI (Credential Manager),
     * then creates/merges the profile doc with the Google photo.
     */
    suspend fun signInWithGoogle(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val user = auth.signInWithCredential(credential).await().user!!
        val doc = users().document(user.uid)
        val exists = doc.get().await().exists()
        val data = buildMap<String, Any?> {
            put("uid", user.uid)
            put("email", user.email)
            put("displayName", user.displayName ?: "")
            put("photoUrl", user.photoUrl?.toString())
            if (!exists) put("createdAt", FieldValue.serverTimestamp())
        }
        doc.set(data, SetOptions.merge()).await()
        return user
    }

    suspend fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /**
     * Deletes the profile doc, then the auth account. Firebase needs a recent
     * sign-in for this; otherwise it throws `ERROR_REQUIRES_RECENT_LOGIN`.
     */
    suspend fun deleteAccount() {
        val user = auth.currentUser ?: return
        // Rules only let a signed-in user delete their own doc, so do it first.
        users().document(user.uid).delete().await()
        user.delete().await()
    }

    fun signOut() {
        auth.signOut()
    }

    /** Called from FirebaseMessagingService.onNewToken. */
    fun onNewFcmToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        scope.launch { saveFcmToken(uid, token) }
    }

    /** Best-effort: notification setup must never block sign-in. Failures are logged, never thrown. */
    private suspend fun registerFcmToken(uid: String) {
        val token = try {
            messaging.token.await()
        } catch (e: Exception) {
            // Usually google-services.json doesn't match the applicationId, the
            // API key is restricted to other apps, or Play services is missing.
            Log.e(TAG, "Could not get an FCM token; this device can't receive calls.", e)
            return
        }
        saveFcmToken(uid, token)
    }

    private suspend fun saveFcmToken(uid: String, token: String) {
        try {
            users().document(uid).set(
                mapOf(
                    "fcmToken" to token,
                    "fcmTokenUpdatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
            Log.i(TAG, "FCM token saved for this user; incoming calls will ring here.")
        } catch (e: Exception) {
            Log.e(TAG, "Could not save the FCM token to users/{uid}.", e)
        }
    }

    private fun users() = firestore.collection(FirestoreConstants.USERS)

    private fun publish(user: FirebaseUser?) {
        _session.value = user?.let { SessionState.SignedIn(it.toSessionUser()) } ?: SessionState.SignedOut
    }

    private fun FirebaseUser.toSessionUser() = SessionUser(
        uid = uid,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl?.toString(),
    )

    private companion object {
        const val TAG = "WibeChatFCM"
    }
}
