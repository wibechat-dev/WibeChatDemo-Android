package com.wibechat.demo.feature.rooms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wibechat.demo.call.CallArgs
import com.wibechat.demo.call.CallLauncher
import com.wibechat.demo.core.CoreServices
import com.wibechat.demo.core.model.PresenceEntry
import com.wibechat.demo.core.model.RoomInfo
import com.wibechat.demo.feature.home.HomeViewModel
import com.wibechat.demo.feature.home.LoadState
import com.wibechat.demo.ui.components.AppButton
import com.wibechat.demo.ui.components.AppCard
import com.wibechat.demo.ui.components.AppEmptyState
import com.wibechat.demo.ui.components.AppTextField
import com.wibechat.demo.ui.components.UserAvatar
import com.wibechat.demo.ui.components.rememberShowMessage
import com.wibechat.demo.ui.theme.AppColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun RoomsTab(vm: HomeViewModel, query: String) {
    val state by vm.rooms.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val showMessage = rememberShowMessage()
    val myUid = CoreServices.auth.currentUser?.uid
    var roomToDelete by remember { mutableStateOf<RoomInfo?>(null) }

    when (val s = state) {
        LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        LoadState.Failed -> AppEmptyState(Icons.Rounded.CloudOff, "Rooms couldn’t load", "Please check your connection and try again.")
        is LoadState.Loaded -> {
            val rooms = s.data.filter { !it.direct && it.name.lowercase().contains(query) }
            if (rooms.isEmpty()) {
                if (query.isEmpty()) {
                    AppEmptyState(Icons.Outlined.Forum, "Make room for your people", "Create a room, invite someone, and let the conversation flow.")
                } else {
                    AppEmptyState(Icons.Rounded.SearchOff, "No rooms found", "Try a different room name.")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(rooms, key = { it.id }) { room ->
                        RoomCard(
                            room = room,
                            vm = vm,
                            isOwner = room.createdBy == myUid,
                            onDelete = { roomToDelete = room },
                            onJoin = { CallLauncher.open(CallArgs(roomId = room.id, title = room.name)) },
                        )
                    }
                }
            }
        }
    }

    roomToDelete?.let { room ->
        AlertDialog(
            onDismissRequest = { roomToDelete = null },
            title = { Text("Delete room") },
            text = { Text("Delete \"${room.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    roomToDelete = null
                    scope.launch {
                        try {
                            vm.deleteRoom(room.id)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            showMessage("Could not delete room: ${e.message}")
                        }
                    }
                }) { Text("Delete", color = AppColors.Error) }
            },
            dismissButton = { TextButton(onClick = { roomToDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RoomCard(room: RoomInfo, vm: HomeViewModel, isOwner: Boolean, onDelete: () -> Unit, onJoin: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val presenceFlow = remember(room.id) { vm.presence(room.id) }
    val participants by presenceFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    AppCard(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.PrimaryLight)
                        .padding(12.dp),
                ) { Icon(Icons.Outlined.Forum, null, tint = AppColors.Primary) }
                Spacer(Modifier.width(14.dp))
                Text(
                    room.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isOwner) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreHoriz, contentDescription = "Room options", tint = AppColors.TextSecondary)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = Color.White) {
                            DropdownMenuItem(
                                text = { Text("Delete room", color = AppColors.Error) },
                                onClick = { menuOpen = false; onDelete() },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Created by ${room.createdByEmail ?: "unknown"}",
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = AppColors.Border)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PresenceRow(participants) }
                Spacer(Modifier.width(12.dp))
                AppButton(label = "Join", onClick = onJoin, height = 48.dp, fillWidth = false)
            }
        }
    }
}

@Composable
private fun PresenceRow(participants: List<PresenceEntry>) {
    if (participants.isEmpty()) {
        Text("Be the first to join", color = AppColors.TextSecondary, fontSize = 12.sp)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        participants.take(3).forEach {
            UserAvatar(label = it.label, photoUrl = it.photoUrl, size = 28.dp, borderColor = Color.White)
        }
        Text("${participants.size} joined", color = AppColors.TextSecondary, fontSize = 12.sp)
    }
}

/** Room names double as Firestore doc ids, so keep them simple. */
@Composable
fun CreateRoomDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    val validName = remember { Regex("^[a-zA-Z0-9_-]{1,40}$") }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun submit() {
        val value = name.trim()
        error = when {
            value.isEmpty() -> "Room name is required"
            !validName.matches(value) -> "Letters, numbers, - and _ only"
            else -> null
        }
        if (error == null) onCreate(value)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create room") },
        text = {
            AppTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = "Room name",
                hint = "e.g. team-standup",
                error = error,
                imeAction = ImeAction.Done,
                onImeAction = ::submit,
                focusRequester = focus,
            )
        },
        confirmButton = { TextButton(onClick = ::submit) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
