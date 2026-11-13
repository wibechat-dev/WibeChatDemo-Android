package com.wibechat.demo.calls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.wibechat.android.renderer.TextureViewRenderer
import com.wibechat.android.room.Room
import com.wibechat.android.room.track.VideoTrack
import livekit.org.webrtc.RendererCommon

/** How a video fills its tile. */
enum class VideoFit(internal val scaling: RendererCommon.ScalingType) {
    /** Fill the tile and crop the edges. Right for camera feeds. */
    Cover(RendererCommon.ScalingType.SCALE_ASPECT_FILL),

    /** Show the whole frame, letterboxed. Right for shared screens. */
    Contain(RendererCommon.ScalingType.SCALE_ASPECT_FIT),
}

/**
 * Renders a WibeChat [VideoTrack] in Compose.
 *
 * Uses the SDK's [TextureViewRenderer] (a TextureView, so tiles can overlap,
 * be clipped to rounded corners and animate). The renderer is created per
 * track and detached + released when it leaves the composition.
 */
@Composable
fun VideoTrackView(
    room: Room,
    track: VideoTrack,
    modifier: Modifier = Modifier,
    fit: VideoFit = VideoFit.Cover,
    mirror: Boolean = false,
) {
    // A new track gets a fresh renderer; simpler than re-pointing one.
    key(track) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                TextureViewRenderer(context).apply {
                    room.initVideoRenderer(this)
                    setScalingType(fit.scaling)
                    setMirror(mirror)
                    track.addRenderer(this)
                }
            },
            update = { view ->
                view.setScalingType(fit.scaling)
                view.setMirror(mirror)
            },
            onRelease = { view ->
                track.removeRenderer(view)
                view.release()
            },
        )
    }
}
