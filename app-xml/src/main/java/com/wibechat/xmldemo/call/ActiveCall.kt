package com.wibechat.xmldemo.call

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process signals between the call screen and the rest of the app
 * (push handling, Picture-in-Picture). Replaces the Flutter method channel +
 * `onCallEndedRemotely` stream.
 */
object ActiveCall {

    private val _isActive = MutableStateFlow(false)

    /** True while a call screen is open; MainActivity uses it to enter PiP on Home. */
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _inPipMode = MutableStateFlow(false)
    val inPipMode: StateFlow<Boolean> = _inPipMode.asStateFlow()

    private val _callEndedRemotely = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Emits a roomId when the other person ends that call (an `end_call` push). */
    val callEndedRemotely: SharedFlow<String> = _callEndedRemotely.asSharedFlow()

    private val _hangUpRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when the user taps "End call" on the PiP window. */
    val hangUpRequests: SharedFlow<Unit> = _hangUpRequests.asSharedFlow()

    fun setActive(active: Boolean) {
        _isActive.value = active
    }

    fun setPipMode(inPip: Boolean) {
        _inPipMode.value = inPip
    }

    fun notifyCallEndedRemotely(roomId: String) {
        _callEndedRemotely.tryEmit(roomId)
    }

    fun requestHangUp() {
        _hangUpRequests.tryEmit(Unit)
    }
}
