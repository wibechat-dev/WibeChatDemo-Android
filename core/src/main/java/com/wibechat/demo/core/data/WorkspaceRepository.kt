package com.wibechat.demo.core.data

import com.google.firebase.firestore.FirebaseFirestore
import com.wibechat.demo.core.auth.AuthRepository
import com.wibechat.demo.core.auth.SessionState
import com.wibechat.demo.core.config.AppConfig
import com.wibechat.demo.core.firebase.FirestoreConstants
import com.wibechat.demo.core.model.Workspace
import com.wibechat.demo.core.network.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.json.JSONObject

/** Whether the signed-in user has joined a workspace yet. */
sealed interface WorkspaceState {
    data object Loading : WorkspaceState

    /** Signed in, but no workspace: show the "Connect your workspace" screen. */
    data object None : WorkspaceState

    data class Joined(val workspace: Workspace) : WorkspaceState
}

/**
 * Workspace membership. The server URL, API key and client ID are entered
 * once per account; the `workspace` Cloud Function validates them, stores the secret
 * server-side and writes `users/{uid}.workspaceId`. Because that lives in
 * Firestore, a returning user (any device, after reinstall) goes straight
 * to Home.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceRepository(
    private val firestore: FirebaseFirestore,
    private val api: ApiService,
    auth: AuthRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Live membership for whoever is signed in. Drives the gate after sign-in. */
    val state: StateFlow<WorkspaceState> = auth.session
        .flatMapLatest { session ->
            when (session) {
                is SessionState.SignedIn -> watchMembership(session.user.uid)
                SessionState.SignedOut -> flowOf(WorkspaceState.None)
                SessionState.Loading -> flowOf(WorkspaceState.Loading)
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, WorkspaceState.Loading)

    /** The current workspace id, or null. For code paths that run after the gate (calls, lists). */
    val currentId: String? get() = (state.value as? WorkspaceState.Joined)?.workspace?.id

    /**
     * Checks the credentials with the server and, unless [dryRun], joins the
     * workspace. Throws [com.wibechat.demo.core.network.ApiException] with a
     * user-readable message ("The server rejected this API key or secret").
     */
    suspend fun connect(config: WorkspaceConfig, dryRun: Boolean = false): Workspace {
        val body = api.post(
            AppConfig.workspaceConnectEndpoint,
            JSONObject()
                .put("serverUrl", config.serverUrl.orEmpty().trim())
                .put("apiKey", config.apiKey.orEmpty().trim())
                .put("clientId", config.clientId.orEmpty().trim())
                .put("dryRun", dryRun),
        )
        // On success the user doc changes and [state] moves to Joined by itself.
        return Workspace(
            id = body.getString("workspaceId"),
            name = body.optString("name"),
            apiUrl = body.optString("apiUrl"),
            serverUrl = body.optString("serverUrl"),
            clientId = body.optString("clientId"),
            apiKeyMasked = body.optString("apiKeyMasked"),
        )
    }

    /** Leaves the workspace; the gate shows the setup screen again. */
    suspend fun leave() {
        api.post(AppConfig.workspaceLeaveEndpoint)
    }

    private fun watchMembership(uid: String): Flow<WorkspaceState> =
        firestore.collection(FirestoreConstants.USERS).document(uid).snapshotFlow()
            .map { it?.getString(FirestoreConstants.WORKSPACE_ID) }
            .distinctUntilChanged()
            .flatMapLatest { workspaceId ->
                if (workspaceId == null) {
                    flowOf(WorkspaceState.None)
                } else {
                    firestore.collection(FirestoreConstants.WORKSPACES).document(workspaceId).snapshotFlow()
                        .map { doc ->
                            // No clientId = made by the earlier key/secret setup: connect again.
                            if (doc == null || !doc.exists() || doc.getString("clientId").isNullOrBlank()) {
                                WorkspaceState.None
                            } else {
                                WorkspaceState.Joined(
                                    Workspace(
                                        id = workspaceId,
                                        name = doc.getString("name").orEmpty(),
                                        apiUrl = doc.getString("apiUrl").orEmpty(),
                                        serverUrl = doc.getString("serverUrl").orEmpty(),
                                        clientId = doc.getString("clientId").orEmpty(),
                                        apiKeyMasked = doc.getString("apiKeyMasked").orEmpty(),
                                    ),
                                )
                            }
                        }
                }
            }
            .catch { emit(WorkspaceState.None) }

    private fun com.google.firebase.firestore.DocumentReference.snapshotFlow() =
        callbackFlow {
            val registration = addSnapshotListener { snapshot, error ->
                if (error != null) close(error) else trySend(snapshot)
            }
            awaitClose { registration.remove() }
        }
}
