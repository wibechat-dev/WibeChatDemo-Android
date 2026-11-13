package com.wibechat.demo.feature.call

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ScreenShare
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wibechat.android.room.Room
import com.wibechat.demo.calls.ParticipantTile
import com.wibechat.demo.calls.VideoFit
import com.wibechat.demo.calls.VideoTrackView
import com.wibechat.demo.ui.components.pressScale
import com.wibechat.demo.ui.theme.AppColors
import kotlin.math.ceil
import kotlin.math.roundToInt

/** One person: their video (or initial), name chip, speaking ring, weak-network badge. */
@Composable
fun ParticipantTileView(room: Room, tile: ParticipantTile, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .clip(shape)
            .background(AppColors.CallSurface)
            .let { if (tile.isSpeaking) it.border(3.dp, AppColors.Primary, shape) else it },
    ) {
        val track = tile.videoTrack
        if (track != null) {
            VideoTrackView(
                room = room,
                track = track,
                modifier = Modifier.fillMaxSize(),
                // A shared screen must stay uncropped; a camera can fill the tile.
                fit = if (tile.isScreenShare) VideoFit.Contain else VideoFit.Cover,
                mirror = tile.mirror,
            )
        } else {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AppColors.Primary)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center,
            ) {
                Text(tile.name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tile.isScreenShare) {
                Icon(Icons.Outlined.ScreenShare, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            if (tile.audioMuted) {
                Icon(Icons.Rounded.MicOff, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                if (tile.isLocal) "${tile.name} (You)" else tile.name,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }

        if (tile.hasPoorConnection) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.Warning.copy(alpha = 0.9f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.NetworkCheck, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Low network", color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

/**
 * Who's on screen:
 * - 1:1 → the other person full screen, you as a draggable corner tile (tap to swap)
 * - otherwise → an even grid: 2 rows for 2–4 people, then 3 or 4 columns
 */
@Composable
fun VideoGrid(room: Room, local: ParticipantTile?, remotes: List<ParticipantTile>) {
    if (local != null && remotes.size == 1) {
        OneToOneView(room, local, remotes.first())
        return
    }

    val tiles = listOfNotNull(local) + remotes
    when (tiles.size) {
        0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        1 -> ParticipantTileView(room, tiles.first(), Modifier.fillMaxSize().padding(8.dp))
        else -> {
            val count = tiles.size
            val columns = when {
                count <= 4 -> (count + 1) / 2
                count <= 9 -> 3
                else -> 4
            }
            val rows = ceil(count / columns.toFloat()).toInt()
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(rows) { r ->
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(columns) { c ->
                            val tile = tiles.getOrNull(r * columns + c)
                            if (tile != null) {
                                ParticipantTileView(room, tile, Modifier.weight(1f).fillMaxHeight())
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OneToOneView(room: Room, local: ParticipantTile, remote: ParticipantTile) {
    var localIsMain by remember { mutableStateOf(false) }
    var offset by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current
    val pipW = 110.dp
    val pipH = 150.dp
    val margin = 12.dp

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val (pipWpx, pipHpx, marginPx) = with(density) { Triple(pipW.toPx(), pipH.toPx(), margin.toPx()) }
        val maxX = (constraints.maxWidth - pipWpx - marginPx).coerceAtLeast(marginPx)
        val maxY = (constraints.maxHeight - pipHpx - marginPx).coerceAtLeast(marginPx)
        val current = offset ?: Offset(marginPx, maxY) // bottom-left by default
        val x = current.x.coerceIn(marginPx, maxX)
        val y = current.y.coerceIn(marginPx, maxY)

        val main = if (localIsMain) local else remote
        val small = if (localIsMain) remote else local

        key(main.key) { ParticipantTileView(room, main, Modifier.fillMaxSize()) }
        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(pipW, pipH)
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .pointerInput(Unit) { detectTapGestures(onTap = { localIsMain = !localIsMain }) }
                .pointerInput(maxX, maxY) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val base = offset ?: Offset(x, y)
                        offset = Offset((base.x + drag.x).coerceIn(marginPx, maxX), (base.y + drag.y).coerceIn(marginPx, maxY))
                    }
                },
        ) {
            key(small.key) { ParticipantTileView(room, small, Modifier.fillMaxSize()) }
        }
    }
}

/** Round-square button with a caption, used in the call control bar. */
@Composable
fun ControlButton(
    label: String,
    icon: ImageVector,
    background: Color,
    iconColor: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(background, label = "control-bg")
    Column(
        Modifier
            .pressScale(interaction)
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

/** Orange strip at the top: reconnecting, or a weak connection. */
@Composable
fun NetworkBanner(message: String, color: Color, icon: ImageVector, showProgress: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(color)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showProgress) {
            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(message, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}
