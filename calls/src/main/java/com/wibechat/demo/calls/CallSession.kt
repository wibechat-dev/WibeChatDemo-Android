package com.wibechat.demo.calls

import android.content.Context
import android.content.Intent
import com.wibechat.android.RoomOptions
import com.wibechat.android.WibeChat
import com.wibechat.android.events.RoomEvent
import com.wibechat.android.events.collect
import com.wibechat.android.room.Room
import com.wibechat.android.room.participant.LocalParticipant
import com.wibechat.android.room.participant.Participant
import com.wibechat.android.room.participant.VideoTrackPublishOptions
import com.wibechat.android.room.track.RemoteTrackPublication
import com.wibechat.android.room.track.VideoPreset169
import com.wibechat.android.room.track.VideoQuality
import livekit.org.webrtc.RtpParameters
import com.wibechat.android.room.track.CameraPosition
import com.wibechat.android.room.track.LocalVideoTrack
import com.wibechat.android.room.track.Track
import com.wibechat.android.room.track.TrackPublication
import com.wibechat.android.room.track.VideoTrack
import com.wibechat.android.room.track.screencapture.ScreenCaptureParams
import com.twilio.audioswitch.AudioDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One WibeChat call: owns the SDK [Room] and turns its events into a single
 * [state] flow the UI can render, plus a few one-off [events].
 *
 * Has no idea where tokens come from or who the users are, so it can be
 * dropped into any app. Create one per call and [release] it when the call
 * screen goes away.
 *
 * ```
 * val session = CallSession(context)
 * session.connect(CallCredentials(serverUrl, token))
 * session.setMicrophoneEnabled(true)
 * session.setCameraEnabled(true)
 * // render session.state; on hang-up:
 * session.release()
 * ```
 */
class CallSession(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The SDK room, for video renderers ([VideoTrackView]) and advanced use. */
    val room: Room = WibeChat.create(
        appContext = context.applicationContext,
        // adaptiveStream stays OFF: with it on, the server only sends a remote
        // video once the SDK's ViewVisibility sees a laid-out, visible View.
        // That check targets classic Views; with Compose (AndroidView) the
        // renderer is attached before layout, so remote video stays paused and
        // the tile shows black/avatar. Dynacast (pausing unused layers on the
        // sender) is unaffected.
        options = RoomOptions(adaptiveStream = false, dynacast = true),
    )

    private val _state = MutableStateFlow(CallSessionState())
    val state: StateFlow<CallSessionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CallSessionEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<CallSessionEvent> = _events.asSharedFlow()

    private val _quality = MutableStateFlow(CallQuality())

    /** Live quality: connection rating + measured send/receive video. Updated every 2 s while connected. */
    val quality: StateFlow<CallQuality> = _quality.asStateFlow()

    private val monitor = QualityMonitor()
    private var statsJob: Job? = null

    /** Set while *we* are disconnecting, so that isn't reported as [ConnectionStatus.Lost]. */
    private var disconnectRequested = false

    /** Reports the SDK's active output (speaker, earpiece, headset) into [state]. Called off the main thread. */
    private val audioDeviceListener: (List<AudioDevice>, AudioDevice?) -> Unit = { _, selected ->
        val route = when (selected) {
            is AudioDevice.Speakerphone -> AudioRoute.Speaker
            is AudioDevice.Earpiece -> AudioRoute.Earpiece
            is AudioDevice.BluetoothHeadset -> AudioRoute.Bluetooth
            is AudioDevice.WiredHeadset -> AudioRoute.Wired
            else -> AudioRoute.Unknown
        }
        scope.launch { _state.update { it.copy(audioRoute = route) } }
    }

    init {
        room.audioSwitchHandler?.registerAudioDeviceChangeListener(audioDeviceListener)
        scope.launch { room.events.collect(::onRoomEvent) }
        // Local publish/mute changes come through the participant's own events.
        scope.launch { room.localParticipant.events.collect { refresh() } }
    }

    /**
     * Connects to the room. Throws if the connection fails; nothing is
     * published yet, so call [setMicrophoneEnabled] / [setCameraEnabled] after.
     */
    suspend fun connect(credentials: CallCredentials) {
        disconnectRequested = false
        _state.update { it.copy(connection = ConnectionStatus.Connecting) }
        try {
            // Warms up DNS/TLS/region lookup so the real connect is faster.
            runCatching { room.prepareConnection(credentials.serverUrl, credentials.token) }
            room.connect(credentials.serverUrl, credentials.token)
            _state.update { it.copy(connection = ConnectionStatus.Connected) }
            startStatsPolling()
        } catch (e: Throwable) {
            _state.update { it.copy(connection = ConnectionStatus.Idle) }
            throw e
        }
        refresh()
    }

    suspend fun setMicrophoneEnabled(enabled: Boolean) {
        room.localParticipant.setMicrophoneEnabled(enabled)
        refresh()
    }

    suspend fun setCameraEnabled(enabled: Boolean) {
        room.localParticipant.setCameraEnabled(enabled)
        refresh()
    }

    /**
     * Loudspeaker (true) or earpiece (false). Set it before [connect] for the
     * starting route: earpiece for audio calls, loudspeaker for video.
     *
     * Uses the SDK's priority list rather than a fixed device, so a Bluetooth
     * or wired headset still takes over when connected, and a device with no
     * earpiece (most tablets) falls back to the loudspeaker.
     */
    fun setSpeakerphone(on: Boolean) {
        val headsets = listOf(AudioDevice.BluetoothHeadset::class.java, AudioDevice.WiredHeadset::class.java)
        val builtIn = if (on) {
            listOf(AudioDevice.Speakerphone::class.java, AudioDevice.Earpiece::class.java)
        } else {
            listOf(AudioDevice.Earpiece::class.java, AudioDevice.Speakerphone::class.java)
        }
        room.audioSwitchHandler?.preferredDeviceList = headsets + builtIn
        _state.update { it.copy(speakerphone = on) }
    }

    /** Switches between the front and back camera. No-op when the camera is off. */
    fun flipCamera() {
        val track = room.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
            ?: return
        val next = if (track.options.position == CameraPosition.BACK) CameraPosition.FRONT else CameraPosition.BACK
        track.switchCamera(position = next)
        refresh()
    }

    /**
     * Starts sharing the screen. [permissionData] is the result `Intent` of
     * `MediaProjectionManager.createScreenCaptureIntent()`. The SDK runs its own
     * foreground service for the capture, as Android requires.
     */
    suspend fun startScreenShare(permissionData: Intent): Boolean {
        val ok = room.localParticipant.setScreenShareEnabled(
            enabled = true,
            screenCaptureParams = ScreenCaptureParams(
                mediaProjectionPermissionResultData = permissionData,
                onStop = {
                    scope.launch {
                        refresh()
                        _events.emit(CallSessionEvent.ScreenShareStopped)
                    }
                },
            ),
        )
        refresh()
        return ok
    }

    suspend fun stopScreenShare() {
        room.localParticipant.setScreenShareEnabled(false)
        refresh()
    }

    /**
     * Switches between [QualityMode.Auto] (720p) and [QualityMode.Highest] (1080p).
     *
     * Capture resolution and encoding are fixed when a camera track is
     * published, so a live camera is replaced by a new track with the new
     * settings (a brief freeze for the other side). Incoming video is asked for
     * at the HIGH simulcast layer. The server may still send less when the
     * network can't carry it — [quality] shows what actually arrives.
     */
    suspend fun setQualityMode(mode: QualityMode) {
        if (_quality.value.mode == mode) return
        _quality.update { it.copy(mode = mode, switching = true) }
        try {
            val preset = if (mode == QualityMode.Highest) VideoPreset169.H1080 else VideoPreset169.H720
            room.videoTrackCaptureDefaults = room.videoTrackCaptureDefaults.copy(captureParams = preset.capture)
            room.videoTrackPublishDefaults = room.videoTrackPublishDefaults.copy(
                videoEncoding = preset.encoding,
                degradationPreference = if (mode == QualityMode.Highest) {
                    RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION
                } else {
                    null // WebRTC default (balanced)
                },
            )

            requestHighestIncomingVideo()
            republishCamera()
        } finally {
            _quality.update { it.copy(switching = false) }
            refresh()
        }
    }

    /** Replaces the published camera track with one built from the current room defaults. */
    private suspend fun republishCamera() {
        val local = room.localParticipant
        val publication = local.getTrackPublication(Track.Source.CAMERA) ?: return // not created yet: defaults apply later
        val old = publication.track as? LocalVideoTrack ?: return
        val wasMuted = publication.muted

        val newTrack = local.createVideoTrack(
            name = old.name,
            // Keep the current camera (front/back) while changing resolution.
            options = room.videoTrackCaptureDefaults.copy(position = old.options.position, deviceId = old.options.deviceId),
        )
        local.unpublishTrack(old) // also stops its capture
        if (old === cameraTrackWeCreated) old.dispose() // the SDK disposes its own default track on release
        cameraTrackWeCreated = newTrack

        newTrack.start() // same order the SDK uses in setCameraEnabled
        newTrack.startCapture()
        local.publishVideoTrack(
            newTrack,
            VideoTrackPublishOptions(name = old.name, base = room.videoTrackPublishDefaults, source = Track.Source.CAMERA),
        )
        if (wasMuted) local.setCameraEnabled(false)
    }

    /** A camera track this class created (as opposed to the SDK's own default), so we can dispose it. */
    private var cameraTrackWeCreated: LocalVideoTrack? = null

    private fun requestHighestIncomingVideo() {
        room.remoteParticipants.values.forEach { participant ->
            participant.trackPublications.values
                .filterIsInstance<RemoteTrackPublication>()
                .filter { it.kind == Track.Kind.VIDEO }
                .forEach { it.setVideoQuality(VideoQuality.HIGH) }
        }
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        monitor.reset()
        statsJob = scope.launch {
            while (isActive) {
                pollStats()
                delay(2_000)
            }
        }
    }

    private suspend fun pollStats() {
        val local = room.localParticipant
        val sendTrack = (local.getTrackPublication(Track.Source.SCREEN_SHARE) ?: local.getTrackPublication(Track.Source.CAMERA))
            ?.takeIf { !it.muted }?.track
        val receiveTrack = room.remoteParticipants.values.firstNotNullOfOrNull { participant ->
            participant.trackPublications.values
                .firstOrNull { it.kind == Track.Kind.VIDEO && !it.muted && it.subscribed }
                ?.track
        }
        val sending = sendTrack?.let { monitor.sending(it) }
        val receiving = receiveTrack?.let { monitor.receiving(it) }
        _quality.update { it.copy(connection = local.connectionQuality, sending = sending, receiving = receiving) }
    }

    /** Leaves the room. The session can [connect] again afterwards. */
    fun disconnect() {
        statsJob?.cancel()
        statsJob = null
        disconnectRequested = true
        room.disconnect()
        _state.update { CallSessionState(speakerphone = it.speakerphone) }
    }

    /** Leaves the room and frees the SDK resources for good. */
    fun release() {
        room.audioSwitchHandler?.unregisterAudioDeviceChangeListener(audioDeviceListener)
        disconnect()
        cameraTrackWeCreated?.let { runCatching { it.dispose() } }
        cameraTrackWeCreated = null
        room.release()
        scope.cancel()
    }

    private suspend fun onRoomEvent(event: RoomEvent) {
        when (event) {
            is RoomEvent.Reconnecting ->
                _state.update { it.copy(connection = ConnectionStatus.Reconnecting) }

            is RoomEvent.Reconnected ->
                _state.update { it.copy(connection = ConnectionStatus.Connected) }

            is RoomEvent.Disconnected -> {
                if (!disconnectRequested) {
                    _state.update { it.copy(connection = ConnectionStatus.Lost) }
                    _events.emit(CallSessionEvent.ConnectionLost)
                }
            }

            is RoomEvent.ParticipantConnected -> {
                refresh()
                _events.emit(CallSessionEvent.ParticipantJoined(event.participant.identity?.value.orEmpty()))
            }

            is RoomEvent.ParticipantDisconnected -> {
                refresh()
                _events.emit(
                    CallSessionEvent.ParticipantLeft(
                        identity = event.participant.identity?.value.orEmpty(),
                        remaining = room.remoteParticipants.size,
                    ),
                )
            }

            else -> Unit
        }
        refresh()
    }

    /** Rebuilds the UI snapshot from the room. Cheap; called on every event. */
    private fun refresh() {
        val connected = room.state == Room.State.CONNECTED || room.state == Room.State.RECONNECTING
        val local = room.localParticipant
        _state.update { current ->
            current.copy(
                local = if (connected) local.toTile(isLocal = true) else null,
                remotes = if (connected) room.remoteParticipants.values.map { it.toTile(isLocal = false) } else emptyList(),
                localConnectionQuality = local.connectionQuality,
                microphoneEnabled = local.isMicrophoneEnabled,
                cameraEnabled = local.isCameraEnabled,
                screenShareEnabled = local.isScreenShareEnabled,
            )
        }
    }

    private fun Participant.toTile(isLocal: Boolean): ParticipantTile {
        val publications = trackPublications.values
        val videoPubs = publications.filter { it.kind == Track.Kind.VIDEO }
        val videoPub: TrackPublication? =
            videoPubs.firstOrNull { it.source == Track.Source.SCREEN_SHARE } ?: videoPubs.firstOrNull()
        val videoTrack = videoPub?.takeIf { !it.muted && (isLocal || it.subscribed) }?.track as? VideoTrack

        val audioPub = publications.firstOrNull { it.kind == Track.Kind.AUDIO }
        val audioMuted = audioPub == null || audioPub.muted || (!isLocal && !audioPub.subscribed)

        val isScreenShare = videoPub?.source == Track.Source.SCREEN_SHARE
        val identityValue = identity?.value.orEmpty()
        val mirror = isLocal && !isScreenShare &&
            (videoTrack as? LocalVideoTrack)?.options?.position != CameraPosition.BACK

        return ParticipantTile(
            key = if (isLocal) "local" else identityValue,
            identity = identityValue,
            name = name?.takeIf { it.isNotBlank() } ?: identityValue,
            videoTrack = videoTrack,
            audioMuted = audioMuted,
            isSpeaking = isSpeaking,
            isLocal = this is LocalParticipant,
            connectionQuality = connectionQuality,
            isScreenShare = isScreenShare,
            mirror = mirror,
        )
    }
}
