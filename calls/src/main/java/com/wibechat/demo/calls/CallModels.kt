package com.wibechat.demo.calls

import com.wibechat.android.room.participant.ConnectionQuality
import com.wibechat.android.room.track.VideoTrack

/** What the app needs to join a room. Issued by your token server, never built on the device. */
data class CallCredentials(
    val serverUrl: String,
    val token: String,
)

/** Connection lifecycle of a [CallSession]. */
enum class ConnectionStatus {
    /** Not connected yet (or after [CallSession.disconnect]). */
    Idle,

    /** First connection in progress. */
    Connecting,

    /** Connected to the room. */
    Connected,

    /** The SDK lost the connection and is retrying on its own. */
    Reconnecting,

    /** The connection dropped and the SDK gave up. Not set when *you* disconnect. */
    Lost,
}

/**
 * One person in the call, flattened into what a video tile needs.
 * A shared screen wins over the camera when a participant publishes both.
 */
data class ParticipantTile(
    /** Stable key for lists: the participant identity (local = "local"). */
    val key: String,
    /** Participant identity as issued by the token server (a Firebase uid in this demo). */
    val identity: String,
    val name: String,
    val videoTrack: VideoTrack?,
    val audioMuted: Boolean,
    val isSpeaking: Boolean,
    val isLocal: Boolean,
    val connectionQuality: ConnectionQuality,
    val isScreenShare: Boolean,
    /** True for your own front camera, so the preview behaves like a mirror. */
    val mirror: Boolean,
) {
    val hasPoorConnection: Boolean
        get() = connectionQuality == ConnectionQuality.POOR || connectionQuality == ConnectionQuality.LOST
}

/** Where call audio is playing right now. */
enum class AudioRoute { Speaker, Earpiece, Bluetooth, Wired, Unknown }

/** Snapshot of everything the call UI renders. */
data class CallSessionState(
    val connection: ConnectionStatus = ConnectionStatus.Idle,
    val local: ParticipantTile? = null,
    val remotes: List<ParticipantTile> = emptyList(),
    val localConnectionQuality: ConnectionQuality = ConnectionQuality.UNKNOWN,
    val microphoneEnabled: Boolean = false,
    val cameraEnabled: Boolean = false,
    val screenShareEnabled: Boolean = false,
    /** The user's choice: loudspeaker (true) or earpiece (false). A headset still wins when connected. */
    val speakerphone: Boolean = true,
    /** The output actually in use, reported by the SDK. */
    val audioRoute: AudioRoute = AudioRoute.Unknown,
)

/** What the user asked for. The server can still lower quality when the network can't keep up. */
enum class QualityMode(val label: String, val description: String) {
    /** SDK defaults: 720p camera, simulcast, adapts freely. */
    Auto("Auto", "720p, adjusts to your network"),

    /** 1080p camera at up to 3 Mbps; keeps resolution under pressure (drops fps first); HIGH layer from others. */
    Highest("Highest", "1080p, uses more data"),
}

/** Measured stats for one direction of video, from WebRTC. */
data class MediaStats(
    val width: Int,
    val height: Int,
    val fps: Int,
    val kbps: Int,
    /** Why the encoder is holding back ("bandwidth", "cpu"); null when it isn't. Sending only. */
    val limitedBy: String? = null,
) {
    /** "1080p Full HD", "720p HD", "540p", "360p SD", … from the shorter side. */
    val resolutionLabel: String
        get() {
            val p = minOf(width, height)
            return when {
                p >= 1080 -> "1080p Full HD"
                p >= 720 -> "720p HD"
                p >= 540 -> "540p"
                p >= 360 -> "360p SD"
                p > 0 -> "${p}p"
                else -> "—"
            }
        }

    /** Short badge text: "Full HD", "HD", "SD", "Low". */
    val shortLabel: String
        get() {
            val p = minOf(width, height)
            return when {
                p >= 1080 -> "Full HD"
                p >= 720 -> "HD"
                p >= 360 -> "SD"
                p > 0 -> "Low"
                else -> "—"
            }
        }
}

/** Live call quality: the server's rating of your link plus measured video in each direction. */
data class CallQuality(
    val mode: QualityMode = QualityMode.Auto,
    val connection: ConnectionQuality = ConnectionQuality.UNKNOWN,
    /** Video you receive from the other person (the first remote with video), or null if none. */
    val receiving: MediaStats? = null,
    /** Your camera or shared screen as actually encoded, or null when you aren't sending video. */
    val sending: MediaStats? = null,
    /** True while switching modes (the camera restarts). */
    val switching: Boolean = false,
)

/** One-off things that happened in the room. */
sealed interface CallSessionEvent {
    data class ParticipantJoined(val identity: String) : CallSessionEvent

    /** [remaining] = remote participants still in the room after this one left. */
    data class ParticipantLeft(val identity: String, val remaining: Int) : CallSessionEvent

    /** The connection dropped and was not recovered (see [ConnectionStatus.Lost]). */
    data object ConnectionLost : CallSessionEvent

    /** Your screen share stopped from outside the app (e.g. the system "Stop" chip). */
    data object ScreenShareStopped : CallSessionEvent
}
