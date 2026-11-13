package com.wibechat.demo.feature.call

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wibechat.android.room.participant.ConnectionQuality
import com.wibechat.demo.calls.CallQuality
import com.wibechat.demo.calls.MediaStats
import com.wibechat.demo.calls.QualityMode
import com.wibechat.demo.ui.theme.AppColors

private fun ConnectionQuality.color(): Color = when (this) {
    ConnectionQuality.EXCELLENT -> AppColors.Success
    ConnectionQuality.GOOD -> Color(0xFF8BC34A)
    ConnectionQuality.POOR -> AppColors.WarningLight
    ConnectionQuality.LOST -> AppColors.Error
    ConnectionQuality.UNKNOWN -> Color.White.copy(alpha = 0.5f)
}

private fun ConnectionQuality.label(): String = when (this) {
    ConnectionQuality.EXCELLENT -> "Excellent"
    ConnectionQuality.GOOD -> "Good"
    ConnectionQuality.POOR -> "Poor"
    ConnectionQuality.LOST -> "Lost"
    ConnectionQuality.UNKNOWN -> "Checking…"
}

/**
 * Top-bar badge: coloured dot for the connection + the resolution you're
 * actually receiving ("HD", "Full HD"), or your own when nobody sends video.
 */
@Composable
fun QualityBadge(quality: CallQuality, onClick: () -> Unit) {
    val stats = quality.receiving ?: quality.sending
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .clickable(onClickLabel = "Call quality", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(quality.connection.color()))
        Spacer(Modifier.width(6.dp))
        Text(
            stats?.shortLabel ?: quality.connection.label(),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (quality.mode == QualityMode.Highest) {
            Spacer(Modifier.width(4.dp))
            Text("•  MAX", color = AppColors.PrimaryLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Live numbers + the Auto / Highest switch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySheet(quality: CallQuality, onSelect: (QualityMode) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.CallSurface,
        contentColor = Color.White,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) },
    ) {
        // The app's text styles carry a dark colour for light screens; force white on this dark sheet.
        ProvideTextStyle(TextStyle(color = Color.White)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text("Call quality", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            StatRow("Connection") {
                Box(Modifier.size(10.dp).clip(CircleShape).background(quality.connection.color()))
                Spacer(Modifier.width(8.dp))
                Text(quality.connection.label(), fontWeight = FontWeight.SemiBold)
            }
            StatRow("Receiving") { StatsText(quality.receiving, "No video from others") }
            StatRow("Sending") { StatsText(quality.sending, "Your camera is off") }
            quality.sending?.limitedBy?.let { reason ->
                Text(
                    "Your video is reduced by $reason limits right now.",
                    color = AppColors.WarningLight,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Video quality", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (quality.switching) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            }
            Spacer(Modifier.height(8.dp))
            QualityMode.entries.forEach { mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !quality.switching) { onSelect(mode) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = quality.mode == mode,
                        onClick = { onSelect(mode) },
                        enabled = !quality.switching,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color.White,
                            unselectedColor = Color.White.copy(alpha = 0.6f),
                            disabledSelectedColor = Color.White.copy(alpha = 0.4f),
                            disabledUnselectedColor = Color.White.copy(alpha = 0.25f),
                        ),
                    )
                    Column {
                        Text(mode.label, fontWeight = FontWeight.SemiBold)
                        Text(mode.description, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Highest asks for the best video both ways. The server still lowers it if the network can't keep up, so the numbers above show what you actually get.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
        }
    }
}

@Composable
private fun StatRow(label: String, value: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White.copy(alpha = 0.6f))
        Row(verticalAlignment = Alignment.CenterVertically) { value() }
    }
}

@Composable
private fun StatsText(stats: MediaStats?, empty: String) {
    if (stats == null) {
        Text(empty, color = Color.White.copy(alpha = 0.5f))
        return
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(stats.resolutionLabel, fontWeight = FontWeight.SemiBold)
        Text(
            "${stats.width}×${stats.height} · ${stats.fps} fps · ${formatKbps(stats.kbps)}",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
        )
    }
}

private fun formatKbps(kbps: Int): String =
    if (kbps >= 1000) String.format(java.util.Locale.US, "%.1f Mbps", kbps / 1000f) else "$kbps kbps"
