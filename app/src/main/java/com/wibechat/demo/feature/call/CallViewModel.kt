package com.wibechat.demo.feature.call

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wibechat.demo.AppScope
import com.wibechat.demo.call.ActiveCall
import com.wibechat.demo.call.CallArgs
import com.wibechat.demo.calls.CallCredentials
import com.wibechat.demo.calls.CallSession
import com.wibechat.demo.calls.CallSessionEvent
import com.wibechat.demo.calls.CallSessionState
import com.wibechat.demo.calls.ConnectionStatus
import com.wibechat.demo.calls.QualityMode
import com.wibechat.demo.core.CoreServices
import com.wibechat.demo.core.model.AppUser
import com.wibechat.demo.core.model.CallLogStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

data class CallUiState(
    /** First connection (token + room) in progress. */
    val connecting: Boolean = true,
    /** Set when joining failed or the connection was lost for good. */
    val error: String? = null,
    /** False when mic/camera permission was denied: the user joined listen-only. */
    val mediaGranted: Boolean = true,
    val elapsedSeconds: Int = 0,
    val session: CallSessionState = CallSessionState(),
) {
    val reconnecting: Boolean get() = session.connection == ConnectionStatus.Reconnecting
}

/**
 * Call logic, same rules as the Flutter `CallScreen`:
 * token from the Cloud Function → connect → presence → mic/camera;
 * 1:1 rooms end when the other person leaves; the call log and the
 * "call ended" push are written once on the way out.
 */
class CallViewModel(app: Application, val args: CallArgs) : AndroidViewModel(app) {

    private val auth = CoreServices.auth
    private val rooms = CoreServices.rooms
    private val myUid: String? = auth.currentUser?.uid
    private val otherUid: String? = myUid?.let { rooms.otherUidInDirectRoom(args.roomId, it) }

    /** Only a 1:1 room ends when the other person leaves, and only it can grow via "Add to call". */
    val isDirectRoom: Boolean = otherUid != null

    val session = CallSession(app)

    private val _ui = MutableStateFlow(CallUiState())
    val ui: StateFlow<CallUiState> = _ui.asStateFlow()

    private val _close = Channel<Unit>(Channel.CONFLATED)

    /** Emits once when the screen should close. */
    val close: Flow<Unit> = _close.receiveAsFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var permissionsChecked = false
    private var joinInProgress = false
    private var left = false
    private var answered = false
    private var callLogged = false
    private val startedAt = Date()
    private var timerJob: Job? = null

    init {
        ActiveCall.setActive(true)
        // Like a phone call: audio calls start on the earpiece, video calls on the loudspeaker.
        session.setSpeakerphone(args.startWithVideo)

        viewModelScope.launch {
            session.state.collect { s ->
                if (s.remotes.isNotEmpty()) answered = true
                _ui.update { it.copy(session = s) }
            }
        }

        viewModelScope.launch {
            session.events.collect { event ->
                when (event) {
                    is CallSessionEvent.ParticipantLeft -> {
                        // A reconnect briefly rebuilds participants; that isn't a hang-up.
                        val ui = _ui.value
                        if (!ui.reconnecting && !ui.connecting && isDirectRoom && event.remaining == 0) leave()
                    }
                    CallSessionEvent.ConnectionLost -> {
                        if (!left && !joinInProgress) {
                            _ui.update {
                                it.copy(connecting = false, error = "The connection was lost. Check your network and try again.")
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }

        // The other side ended before ever joining the room (declined, cancelled).
        viewModelScope.launch {
            ActiveCall.callEndedRemotely.collect { roomId -> if (roomId == args.roomId) leave() }
        }
        // "End call" on the Picture-in-Picture window.
        viewModelScope.launch { ActiveCall.hangUpRequests.collect { leave() } }
    }

    /** Called once by the screen after asking for microphone + camera. */
    fun onPermissionsResult(granted: Boolean) {
        if (permissionsChecked) return
        permissionsChecked = true
        _ui.update { it.copy(mediaGranted = granted) }
        join()
    }

    fun join() {
        if (joinInProgress || left) return
        joinInProgress = true
        _ui.update { it.copy(connecting = true, error = null) }

        viewModelScope.launch {
            try {
                val token = CoreServices.callTokens.fetchToken(args.roomId)
                if (left) return@launch
                session.connect(CallCredentials(serverUrl = token.serverUrl, token = token.token))
                if (left) {
                    session.disconnect()
                    return@launch
                }

                auth.sessionUser?.let { me -> runCatching { rooms.joinPresence(args.roomId, me) } }

                if (_ui.value.mediaGranted) {
                    session.setMicrophoneEnabled(true)
                    session.setCameraEnabled(args.startWithVideo)
                }

                startTimer()
                _ui.update { it.copy(connecting = false, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!left) _ui.update { it.copy(connecting = false, error = e.message ?: e.toString()) }
            } finally {
                joinInProgress = false
            }
        }
    }

    fun toggleMicrophone() = withMedia {
        session.setMicrophoneEnabled(!session.state.value.microphoneEnabled)
    }

    fun toggleCamera() = withMedia {
        val cameraOn = !session.state.value.cameraEnabled
        session.setCameraEnabled(cameraOn)
        // Camera on means the phone is in front of you, not at your ear: follow it with the speaker.
        session.setSpeakerphone(cameraOn)
    }

    /** Loudspeaker ↔ earpiece. A connected headset always plays regardless. */
    fun toggleSpeaker() = session.setSpeakerphone(!session.state.value.speakerphone)

    fun flipCamera() = session.flipCamera()

    /** Live connection rating + measured video, straight from the call session. */
    val quality = session.quality

    fun setQualityMode(mode: QualityMode) {
        viewModelScope.launch {
            try {
                session.setQualityMode(mode)
                _messages.emit(if (mode == QualityMode.Highest) "Switched to highest quality" else "Switched to auto quality")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit("Could not change quality: ${e.message}")
            }
        }
    }

    fun startScreenShare(permissionData: Intent) {
        viewModelScope.launch {
            try {
                if (!session.startScreenShare(permissionData)) _messages.emit("Could not start screen share")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit("Could not start screen share: ${e.message}")
            }
        }
    }

    fun stopScreenShare() {
        viewModelScope.launch { runCatching { session.stopScreenShare() } }
    }

    // ---- Add to call (1:1 rooms only) ---------------------------------------

    fun watchUsers(): Flow<List<AppUser>> =
        CoreServices.workspace.currentId?.let { CoreServices.users.watchUsers(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList())

    /** Uids that must not be offered in "Add to call": me + everyone already in the room. */
    fun inCallUids(): Set<String> =
        setOfNotNull(myUid) + session.state.value.remotes.map { it.identity }

    /** Rings another user into this same room. */
    fun invite(uid: String, label: String) {
        val me = auth.sessionUser ?: return
        viewModelScope.launch {
            CoreServices.push.sendCallInvite(
                toUserId = uid,
                roomId = args.roomId,
                callerId = me.uid,
                callerLabel = me.callerLabel,
                video = args.startWithVideo,
                desc = "${me.callerLabel} is adding you to a call",
            )
            _messages.emit("Invited $label to the call")
        }
    }

    // ---- leaving -------------------------------------------------------------

    fun leave() {
        if (left) return
        left = true
        cleanUp()
        session.disconnect()
        _close.trySend(Unit)
    }

    override fun onCleared() {
        if (!left) {
            left = true
            cleanUp()
        }
        session.release()
        ActiveCall.setActive(false)
        super.onCleared()
    }

    /** Call log, presence, "call ended" push. Runs in AppScope so it outlives this screen. */
    private fun cleanUp() {
        timerJob?.cancel()
        writeCallLog()
        val uid = myUid ?: return
        AppScope.launch {
            runCatching { rooms.leavePresence(args.roomId, uid) }
            // Stops the other phone ringing if they haven't answered, or closes their call.
            otherUid?.let { CoreServices.push.sendCallEnded(toUserId = it, roomId = args.roomId, desc = "The call has ended") }
        }
    }

    /** One entry for this user's own view of a 1:1 call. Only the first call writes. */
    private fun writeCallLog() {
        if (callLogged) return
        callLogged = true
        val uid = myUid ?: return
        val other = otherUid ?: return
        val status = when {
            answered -> CallLogStatus.Completed
            _ui.value.error != null -> CallLogStatus.Failed
            args.isOutgoing -> CallLogStatus.NoAnswer
            else -> CallLogStatus.Missed
        }
        val duration = _ui.value.elapsedSeconds
        AppScope.launch {
            runCatching {
                CoreServices.callLogs.logCall(
                    uid = uid,
                    roomId = args.roomId,
                    otherUid = other,
                    otherLabel = args.title ?: other,
                    video = args.startWithVideo,
                    outgoing = args.isOutgoing,
                    status = status,
                    startedAt = startedAt,
                    durationSeconds = duration,
                )
            }
        }
    }

    private fun startTimer() {
        if (timerJob != null) return
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                _ui.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    private fun withMedia(action: suspend () -> Unit) {
        if (!_ui.value.mediaGranted) {
            _messages.tryEmit("Allow microphone and camera access in Settings to use this.")
            return
        }
        viewModelScope.launch { runCatching { action() } }
    }
}
