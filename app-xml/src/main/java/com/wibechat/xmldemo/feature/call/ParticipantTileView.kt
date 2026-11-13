package com.wibechat.xmldemo.feature.call

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.wibechat.android.renderer.TextureViewRenderer
import com.wibechat.android.room.Room
import com.wibechat.android.room.track.VideoTrack
import com.wibechat.demo.calls.ParticipantTile
import com.wibechat.xmldemo.R
import com.wibechat.xmldemo.databinding.ViewParticipantTileBinding
import livekit.org.webrtc.RendererCommon

/**
 * One person: their video (or initial), name chip, speaking ring, weak-network badge.
 *
 * The video uses the SDK's [TextureViewRenderer] (a TextureView, so tiles can
 * overlap, be clipped to rounded corners and move). A renderer is created per
 * track and released when the track changes or the tile goes away ([release]).
 */
class ParticipantTileView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val b = ViewParticipantTileBinding.inflate(LayoutInflater.from(context), this)
    private var renderer: TextureViewRenderer? = null
    private var track: VideoTrack? = null
    private val speakingRing = ContextCompat.getDrawable(context, R.drawable.fg_tile_speaking)

    init {
        setBackgroundResource(R.drawable.bg_tile)
        clipToOutline = true // rounded corners from the background shape, video included
    }

    fun bind(room: Room, tile: ParticipantTile) {
        val newTrack = tile.videoTrack
        if (newTrack !== track) {
            detachRenderer()
            if (newTrack != null) attachRenderer(room, newTrack)
        }
        renderer?.let {
            // A shared screen must stay uncropped; a camera can fill the tile.
            it.setScalingType(
                if (tile.isScreenShare) RendererCommon.ScalingType.SCALE_ASPECT_FIT else RendererCommon.ScalingType.SCALE_ASPECT_FILL,
            )
            it.setMirror(tile.mirror)
        }

        b.initial.isVisible = newTrack == null
        b.initial.text = tile.name.firstOrNull()?.uppercase() ?: "?"
        b.screenShareIcon.isVisible = tile.isScreenShare
        b.mutedIcon.isVisible = tile.audioMuted
        b.name.text = if (tile.isLocal) "${tile.name} (You)" else tile.name
        b.lowNetwork.isVisible = tile.hasPoorConnection
        foreground = if (tile.isSpeaking) speakingRing else null
    }

    private fun attachRenderer(room: Room, videoTrack: VideoTrack) {
        val view = TextureViewRenderer(context)
        room.initVideoRenderer(view)
        videoTrack.addRenderer(view)
        addView(view, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        renderer = view
        track = videoTrack
    }

    private fun detachRenderer() {
        val view = renderer ?: return
        track?.removeRenderer(view)
        view.release()
        removeView(view)
        renderer = null
        track = null
    }

    /** Stops rendering and frees the renderer. Call when the tile leaves the screen for good. */
    fun release() = detachRenderer()
}
