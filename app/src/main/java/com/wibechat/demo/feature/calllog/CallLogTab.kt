package com.wibechat.demo.feature.calllog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wibechat.demo.core.model.CallLogEntry
import com.wibechat.demo.core.model.CallLogStatus
import com.wibechat.demo.feature.home.HomeViewModel
import com.wibechat.demo.feature.home.LoadState
import com.wibechat.demo.ui.components.AppCard
import com.wibechat.demo.ui.components.AppEmptyState
import com.wibechat.demo.ui.components.UserAvatar
import com.wibechat.demo.ui.theme.AppColors
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun CallLogTab(vm: HomeViewModel, query: String, onCall: (uid: String, label: String, video: Boolean) -> Unit) {
    val state by vm.callLogs.collectAsStateWithLifecycle()

    when (val s = state) {
        LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        LoadState.Failed -> AppEmptyState(Icons.Rounded.CloudOff, "Recent calls couldn’t load", "Please check your connection and try again.")
        is LoadState.Loaded -> {
            val entries = s.data.filter { it.otherLabel.lowercase().contains(query) }
            if (entries.isEmpty()) {
                if (query.isEmpty()) {
                    AppEmptyState(Icons.Outlined.Call, "Your story starts with a hello", "Make a call from People. Your recent conversations will appear here.")
                } else {
                    AppEmptyState(Icons.Rounded.SearchOff, "No calls found", "Try searching for another person.")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        CallLogRow(entry, onCall = { onCall(entry.otherUid, entry.otherLabel, entry.video) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CallLogRow(entry: CallLogEntry, onCall: () -> Unit) {
    val (icon, color) = directionIconAndColor(entry)
    val status = statusLabel(entry).ifEmpty { if (entry.outgoing) "Outgoing" else "Incoming" }

    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(label = entry.otherLabel)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.otherLabel, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(formatWhen(entry.startedAt), color = AppColors.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(status, color = color, fontSize = 12.sp)
                }
            }
            FilledTonalIconButton(onClick = onCall) {
                Icon(
                    if (entry.video) Icons.Outlined.Videocam else Icons.Outlined.Call,
                    contentDescription = if (entry.video) "Video call" else "Call",
                    tint = AppColors.Primary,
                )
            }
        }
    }
}

private fun directionIconAndColor(entry: CallLogEntry): Pair<ImageVector, Color> = when (entry.status) {
    CallLogStatus.Missed, CallLogStatus.Declined -> Icons.AutoMirrored.Rounded.CallMissed to AppColors.Error
    CallLogStatus.Failed ->
        (if (entry.outgoing) Icons.AutoMirrored.Rounded.CallMade else Icons.AutoMirrored.Rounded.CallMissed) to AppColors.Error
    CallLogStatus.NoAnswer ->
        (if (entry.outgoing) Icons.AutoMirrored.Rounded.CallMade else Icons.AutoMirrored.Rounded.CallMissed) to AppColors.Primary
    CallLogStatus.Completed ->
        (if (entry.outgoing) Icons.AutoMirrored.Rounded.CallMade else Icons.AutoMirrored.Rounded.CallReceived) to AppColors.Primary
}

private fun statusLabel(entry: CallLogEntry): String = when (entry.status) {
    CallLogStatus.Completed -> formatDuration(entry.durationSeconds)
    CallLogStatus.NoAnswer -> "No answer"
    CallLogStatus.Missed -> "Missed"
    CallLogStatus.Declined -> if (entry.outgoing) "Declined" else "You declined"
    CallLogStatus.Failed -> "Call failed"
}

/** "14:05", "Yesterday, 14:05" or "03/11/2026 14:05". */
private fun formatWhen(date: Date): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = date }
    val time = String.format(Locale.getDefault(), "%02d:%02d", then.get(Calendar.HOUR_OF_DAY), then.get(Calendar.MINUTE))
    fun sameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(then, now)) return time
    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (sameDay(then, yesterday)) return "Yesterday, $time"
    return String.format(
        Locale.getDefault(), "%02d/%02d/%d %s",
        then.get(Calendar.DAY_OF_MONTH), then.get(Calendar.MONTH) + 1, then.get(Calendar.YEAR), time,
    )
}

/** "" for 0, "mm:ss", or "h:mm:ss". */
internal fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}
