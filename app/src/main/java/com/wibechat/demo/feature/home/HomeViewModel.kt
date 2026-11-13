package com.wibechat.demo.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wibechat.demo.call.CallLauncher
import com.wibechat.demo.core.CoreServices
import com.wibechat.demo.core.model.AppUser
import com.wibechat.demo.core.model.CallLogEntry
import com.wibechat.demo.core.model.RoomInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data object Failed : LoadState<Nothing>
    data class Loaded<T>(val data: T) : LoadState<T>
}

/**
 * Live data for all three tabs, kept subscribed while Home is on the back
 * stack so switching tabs is instant (like Flutter's IndexedStack).
 */
class HomeViewModel(private val workspaceId: String) : ViewModel() {

    private val uid = CoreServices.auth.currentUser?.uid

    val rooms: StateFlow<LoadState<List<RoomInfo>>> = CoreServices.rooms.watchRooms(workspaceId).asLoadState()

    val users: StateFlow<LoadState<List<AppUser>>> = CoreServices.users.watchUsers(workspaceId).asLoadState()

    val callLogs: StateFlow<LoadState<List<CallLogEntry>>> =
        (uid?.let { CoreServices.callLogs.watchCallLogs(it) } ?: flowOf(emptyList())).asLoadState()

    fun presence(roomId: String) = CoreServices.rooms.watchParticipants(roomId)

    suspend fun createRoom(name: String) {
        val me = CoreServices.auth.sessionUser ?: return
        CoreServices.rooms.createRoom(name, me, workspaceId)
    }

    suspend fun deleteRoom(roomId: String) = CoreServices.rooms.deleteRoom(roomId)

    suspend fun leaveWorkspace() = CoreServices.workspace.leave()

    suspend fun startCall(otherUid: String, label: String, video: Boolean) =
        CallLauncher.startCall(otherUid, label, video)

    private fun <T> Flow<T>.asLoadState(): StateFlow<LoadState<T>> =
        map<T, LoadState<T>> { LoadState.Loaded(it) }
            .catch { emit(LoadState.Failed) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)
}
