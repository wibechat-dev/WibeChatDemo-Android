package com.wibechat.demo.feature.call

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.PersonAddAlt1
import androidx.compose.material.icons.outlined.ScreenShare
import androidx.compose.material.icons.outlined.StopScreenShare
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wibechat.demo.call.ActiveCall
import com.wibechat.demo.call.CallArgs
import com.wibechat.demo.calls.AudioRoute
import com.wibechat.demo.calls.ProximityScreenLock
import com.wibechat.demo.feature.calllog.formatDuration
import com.wibechat.demo.ui.components.AppReveal
import com.wibechat.demo.ui.components.CallHalo
import com.wibechat.demo.ui.components.LocalSnackbarHostState
import com.wibechat.demo.ui.components.UserAvatar
import com.wibechat.demo.ui.components.rememberShowMessage
import com.wibechat.demo.ui.theme.AppColors

/**
 * Connects to a room and shows everyone's camera in an auto-sizing grid,
 * with mic / camera / flip / screen-share / hang-up controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(args: CallArgs, onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: CallViewModel = viewModel(key = "call-${args.roomId}") { CallViewModel(app, args) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val inPip by ActiveCall.inPipMode.collectAsStateWithLifecycle()
    val showMessage = rememberShowMessage()
    var showAddSheet by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    val quality by vm.quality.collectAsStateWithLifecycle()

    // Ask for mic + camera once, then join (listen-only if refused).
    val permissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        vm.onPermissionsResult(result.values.all { it })
    }
    LaunchedEffect(Unit) {
        val granted = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) vm.onPermissionsResult(true) else permissionLauncher.launch(permissions)
    }

    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) vm.startScreenShare(data)
    }

    LaunchedEffect(vm) { vm.close.collect { onClose() } }
    LaunchedEffect(vm) { vm.messages.collect { showMessage(it) } }
    BackHandler { vm.leave() }
    KeepScreenOnWithLightIcons()

    // Screen off at the ear while audio is on the earpiece (the system uses the proximity sensor).
    val proximity = remember { ProximityScreenLock(context) }
    val atEar = ui.session.audioRoute == AudioRoute.Earpiece && !inPip
    LaunchedEffect(atEar) { proximity.setActive(atEar) }
    DisposableEffect(proximity) { onDispose { proximity.setActive(false) } }

    // Android's PiP window is tiny: only the other person's video, no chrome.
    if (inPip) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            ui.session.remotes.firstOrNull()?.let { ParticipantTileView(vm.session.room, it, Modifier.fillMaxSize()) }
        }
        return
    }

    val title = args.title ?: args.roomId
    val remotes = ui.session.remotes
    val inCall = !ui.connecting && ui.error == null && remotes.isNotEmpty()

    Scaffold(
        containerColor = AppColors.CallBackground,
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        topBar = {
            TopAppBar(
                title = { Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = vm::leave) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "End call", tint = Color.White) }
                },
                actions = {
                    if (inCall && vm.isDirectRoom) {
                        IconButton(onClick = { showAddSheet = true }) {
                            Icon(Icons.Outlined.PersonAddAlt1, contentDescription = "Add to call", tint = Color.White)
                        }
                    }
                    if (inCall) {
                        Text(
                            formatDuration(ui.elapsedSeconds).ifEmpty { "00:00" },
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.CallBackground),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.animateContentSize()) { NetworkBannerFor(ui) }
            Box(Modifier.weight(1f)) {
                when {
                    ui.connecting -> StatusView(title, "Connecting…", ui.reconnecting, ui.session.speakerphone, vm::toggleSpeaker, vm::leave)
                    ui.error != null -> ErrorView(ui.error!!, onRetry = vm::join, onEnd = vm::leave)
                    remotes.isEmpty() -> StatusView(title, if (args.isOutgoing) "Calling…" else "Connecting…", ui.reconnecting, ui.session.speakerphone, vm::toggleSpeaker, vm::leave)
                    else -> Column(Modifier.fillMaxSize()) {
                        if (!ui.mediaGranted) {
                            Text(
                                "Microphone/camera permission was denied — you joined listen-only.",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.14f)).padding(12.dp),
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            VideoGrid(vm.session.room, ui.session.local, remotes)
                            // Over the bottom-right of the video; your own 1:1 tile sits bottom-left.
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp),
                            ) {
                                QualityBadge(quality, onClick = { showQualitySheet = true })
                            }
                        }
                        ControlBar(
                            ui = ui,
                            onMic = vm::toggleMicrophone,
                            onCamera = vm::toggleCamera,
                            onFlip = vm::flipCamera,
                            onSpeaker = vm::toggleSpeaker,
                            onShare = {
                                if (ui.session.screenShareEnabled) {
                                    vm.stopScreenShare()
                                } else {
                                    val mpm = context.getSystemService(MediaProjectionManager::class.java)
                                    projectionLauncher.launch(mpm.createScreenCaptureIntent())
                                }
                            },
                            onEnd = vm::leave,
                        )
                    }
                }
            }
        }
    }

    if (showAddSheet) AddToCallSheet(vm, onDismiss = { showAddSheet = false })
    if (showQualitySheet) QualitySheet(quality, onSelect = vm::setQualityMode, onDismiss = { showQualitySheet = false })
}

@Composable
private fun NetworkBannerFor(ui: CallUiState) {
    val quality = ui.session.localConnectionQuality
    when {
        ui.reconnecting -> NetworkBanner("Connection lost. Reconnecting…", AppColors.Warning, Icons.Rounded.Sync, showProgress = true)
        quality == com.wibechat.android.room.participant.ConnectionQuality.POOR ||
            quality == com.wibechat.android.room.participant.ConnectionQuality.LOST ->
            NetworkBanner("Low network quality", AppColors.WarningLight, Icons.Rounded.NetworkCheck, showProgress = false)
        else -> Spacer(Modifier.fillMaxWidth())
    }
}

/** Phone-dialer style "Calling…" / "Connecting…" before the other person's media arrives. */
@Composable
private fun StatusView(
    title: String,
    status: String,
    reconnecting: Boolean,
    speakerOn: Boolean,
    onSpeaker: () -> Unit,
    onEnd: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AppReveal {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CallHalo { UserAvatar(label = title, size = 112.dp, background = AppColors.BrandGradient) }
                Spacer(Modifier.height(28.dp))
                Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(if (reconnecting) "Reconnecting…" else status, color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
                Spacer(Modifier.height(48.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    SpeakerButton(speakerOn, onSpeaker)
                    ControlButton("End call", Icons.Rounded.CallEnd, AppColors.Error, Color.White, onEnd)
                }
            }
        }
    }
}

@Composable
private fun ErrorView(error: String, onRetry: () -> Unit, onEnd: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.WifiOff, null, tint = AppColors.Error, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(12.dp))
            Text("Could not connect\n$error", color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Try again")
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onEnd) { Text("End call") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlBar(
    ui: CallUiState,
    onMic: () -> Unit,
    onCamera: () -> Unit,
    onFlip: () -> Unit,
    onSpeaker: () -> Unit,
    onShare: () -> Unit,
    onEnd: () -> Unit,
) {
    val micOn = ui.session.microphoneEnabled
    val camOn = ui.session.cameraEnabled
    val sharing = ui.session.screenShareEnabled
    val shape = RoundedCornerShape(28.dp)
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)
            .background(AppColors.CallSurface, shape)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .padding(horizontal = 12.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ControlButton(
            if (micOn) "Mute" else "Unmute",
            if (micOn) Icons.Outlined.Mic else Icons.Outlined.MicOff,
            if (micOn) Color.White.copy(alpha = 0.12f) else Color.White,
            if (micOn) Color.White else AppColors.CallBackground,
            onMic,
        )
        ControlButton(
            if (camOn) "Video off" else "Video on",
            if (camOn) Icons.Outlined.Videocam else Icons.Outlined.VideocamOff,
            if (camOn) Color.White.copy(alpha = 0.12f) else Color.White,
            if (camOn) Color.White else AppColors.CallBackground,
            onCamera,
        )
        if (camOn) {
            ControlButton("Flip", Icons.Outlined.Cameraswitch, Color.White.copy(alpha = 0.12f), Color.White, onFlip)
        }
        SpeakerButton(ui.session.speakerphone, onSpeaker)
        ControlButton(
            if (sharing) "Stop sharing" else "Share",
            if (sharing) Icons.Outlined.StopScreenShare else Icons.Outlined.ScreenShare,
            if (sharing) AppColors.Primary else Color.White.copy(alpha = 0.12f),
            Color.White,
            onShare,
        )
        ControlButton("End call", Icons.Rounded.CallEnd, AppColors.Error, Color.White, onEnd)
    }
}

/** Loudspeaker on = highlighted, like the phone app. */
@Composable
private fun SpeakerButton(on: Boolean, onClick: () -> Unit) {
    ControlButton(
        "Speaker",
        if (on) Icons.AutoMirrored.Outlined.VolumeUp else Icons.Outlined.PhoneInTalk,
        if (on) Color.White else Color.White.copy(alpha = 0.12f),
        if (on) AppColors.CallBackground else Color.White,
        onClick,
    )
}

/** Ring someone else into this 1:1 room. Hides people already in the call. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToCallSheet(vm: CallViewModel, onDismiss: () -> Unit) {
    val usersFlow = remember { vm.watchUsers() }
    val users by usersFlow.collectAsStateWithLifecycle(initialValue = null)
    val exclude = remember { vm.inCallUids() }
    val sheetHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth().height(sheetHeight)) {
            Text(
                "Add to call",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
            )
            val list = users
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> {
                    val candidates = list.filter { it.uid !in exclude }
                    if (candidates.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No one else to add", color = AppColors.TextSecondary)
                        }
                    } else {
                        LazyColumn {
                            items(candidates, key = { it.uid }) { user ->
                                ListItem(
                                    headlineContent = { Text(user.label) },
                                    leadingContent = { UserAvatar(label = user.label, photoUrl = user.photoUrl) },
                                    colors = ListItemDefaults.colors(containerColor = Color.White),
                                    modifier = Modifier.clickable {
                                        onDismiss()
                                        vm.invite(user.uid, user.label)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Keeps the screen awake and switches status-bar icons to light while on this dark screen. */
@Composable
private fun KeepScreenOnWithLightIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val wasLight = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            if (wasLight != null) controller.isAppearanceLightStatusBars = wasLight
        }
    }
}
