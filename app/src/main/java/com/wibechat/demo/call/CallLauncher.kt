package com.wibechat.demo.call

import com.wibechat.demo.AppScope
import com.wibechat.demo.core.CoreServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Everything the call screen needs to open. */
data class CallArgs(
    val roomId: String,
    /** Shown instead of the raw room id (direct rooms are an opaque `dm-…` id). */
    val title: String?,
    /** Audio calls join with the camera off; it can still be turned on. */
    val startWithVideo: Boolean = true,
    /** True when this user placed the call: "Calling…" instead of "Connecting…". */
    val isOutgoing: Boolean = false,
)

/**
 * Single entry point for opening a call screen, from anywhere: UI buttons,
 * notification taps, the incoming-call Accept button. The signed-in nav
 * graph consumes [pending] and navigates.
 */
object CallLauncher {

    private val _pending = MutableStateFlow<CallArgs?>(null)
    val pending: StateFlow<CallArgs?> = _pending.asStateFlow()

    fun open(args: CallArgs) {
        _pending.value = args
    }

    fun consume() {
        _pending.value = null
    }

    /**
     * Creates/reuses the 1:1 room with [otherUid], rings them by push, and
     * opens the call screen as the caller. Same as Flutter's `startCall`.
     */
    suspend fun startCall(otherUid: String, label: String, video: Boolean) {
        val me = CoreServices.auth.sessionUser ?: return
        val workspaceId = CoreServices.workspace.currentId ?: error("Join a workspace first")
        val roomId = CoreServices.rooms.getOrCreateDirectRoom(me, otherUid, label, workspaceId)

        AppScope.launch {
            CoreServices.push.sendCallInvite(
                toUserId = otherUid,
                roomId = roomId,
                callerId = me.uid,
                callerLabel = me.callerLabel,
                video = video,
                desc = "${me.callerLabel} is calling you",
            )
        }

        open(CallArgs(roomId = roomId, title = label, startWithVideo = video, isOutgoing = true))
    }
}
