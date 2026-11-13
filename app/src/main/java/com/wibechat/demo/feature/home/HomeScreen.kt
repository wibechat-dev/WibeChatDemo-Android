package com.wibechat.demo.feature.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wibechat.demo.BuildConfig
import com.wibechat.demo.R
import com.wibechat.demo.core.CoreServices
import com.wibechat.demo.core.auth.AuthErrors
import com.wibechat.demo.core.model.Workspace
import com.wibechat.demo.feature.auth.GoogleSignIn
import com.wibechat.demo.feature.calllog.CallLogTab
import com.wibechat.demo.feature.rooms.CreateRoomDialog
import com.wibechat.demo.feature.rooms.RoomsTab
import com.wibechat.demo.feature.users.UsersTab
import com.wibechat.demo.ui.components.AppReveal
import com.wibechat.demo.ui.components.AppSearchField
import com.wibechat.demo.ui.components.LocalSnackbarHostState
import com.wibechat.demo.ui.components.UserAvatar
import com.wibechat.demo.ui.components.rememberShowMessage
import com.wibechat.demo.ui.theme.AppColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class HomeTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val title: String,
    val subtitle: String,
    val searchHint: String,
)

private val Tabs = listOf(
    HomeTab("Rooms", Icons.Outlined.MeetingRoom, Icons.Filled.MeetingRoom, "A space to connect.", "Gather your people in a room.", "Search rooms"),
    HomeTab("People", Icons.Outlined.PersonOutline, Icons.Filled.Person, "Your next conversation.", "A voice or video call is just a tap away.", "Search people"),
    HomeTab("Recents", Icons.Rounded.History, Icons.Rounded.History, "Pick up where you left off.", "Recent conversations, all in one place.", "Search recent calls"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    workspace: Workspace,
    vm: HomeViewModel = viewModel(key = "home-${workspace.id}") { HomeViewModel(workspace.id) },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showMessage = rememberShowMessage()
    val focusManager = LocalFocusManager.current
    val user = CoreServices.auth.sessionUser
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    val query = search.trim().lowercase()
    var showCreateRoom by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }
    var showLeaveWorkspace by remember { mutableStateOf(false) }
    val tab = Tabs[tabIndex]

    fun startCall(uid: String, label: String, video: Boolean) {
        scope.launch {
            try {
                vm.startCall(uid, label, video)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("Could not start call: ${e.message}")
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AccountDrawer(
                name = user?.displayName,
                email = user?.email,
                photoUrl = user?.photoUrl,
                workspace = workspace,
                onLeaveWorkspace = {
                    scope.launch { drawerState.close() }
                    showLeaveWorkspace = true
                },
                onCopyIdToken = {
                    scope.launch {
                        drawerState.close()
                        val token = runCatching { CoreServices.auth.idToken() }.getOrNull()
                        if (token == null) showMessage("No active session") else {
                            copyToClipboard(context, token)
                            showMessage("Copied to clipboard!")
                        }
                    }
                },
                onSignOut = {
                    scope.launch {
                        drawerState.close()
                        GoogleSignIn.signOut(context)
                        CoreServices.auth.signOut()
                    }
                },
                onDeleteAccount = {
                    scope.launch { drawerState.close() }
                    showDeleteAccount = true
                },
            )
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
            containerColor = AppColors.Background,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp) },
                    actions = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }, modifier = Modifier.padding(end = 12.dp)) {
                            UserAvatar(label = user?.displayName ?: user?.email.orEmpty(), photoUrl = user?.photoUrl, size = 38.dp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
                    expandedHeight = 72.dp,
                )
            },
            floatingActionButton = {
                if (tabIndex == 0) {
                    ExtendedFloatingActionButton(
                        onClick = { showCreateRoom = true },
                        containerColor = AppColors.Primary,
                        contentColor = Color.White,
                        icon = { Icon(Icons.Rounded.Add, null) },
                        text = { Text("Create room") },
                    )
                }
            },
            bottomBar = {
                NavigationBar(containerColor = AppColors.Surface) {
                    Tabs.forEachIndexed { index, t ->
                        val selected = index == tabIndex
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (index != tabIndex) {
                                    focusManager.clearFocus()
                                    tabIndex = index
                                    search = ""
                                }
                            },
                            icon = { Icon(if (selected) t.selectedIcon else t.icon, null) },
                            label = { Text(t.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = AppColors.PrimaryLight,
                                selectedIconColor = AppColors.Primary,
                                selectedTextColor = AppColors.Primary,
                                unselectedIconColor = AppColors.TextSecondary,
                                unselectedTextColor = AppColors.TextSecondary,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                val showHeader = maxHeight > 400.dp
                Column(Modifier.widthIn(max = 960.dp).fillMaxSize()) {
                    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 20.dp)) {
                        if (showHeader) {
                            Text(tab.title, style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(tab.subtitle, color = AppColors.TextSecondary)
                            Spacer(Modifier.height(24.dp))
                        }
                        AppSearchField(value = search, onValueChange = { search = it }, hint = tab.searchHint)
                    }
                    AppReveal(replayKey = tabIndex, modifier = Modifier.weight(1f)) {
                        when (tabIndex) {
                            0 -> RoomsTab(vm = vm, query = query)
                            1 -> UsersTab(vm = vm, query = query, onCall = ::startCall)
                            else -> CallLogTab(vm = vm, query = query, onCall = ::startCall)
                        }
                    }
                }
            }
        }
    }

    if (showCreateRoom) {
        CreateRoomDialog(
            onDismiss = { showCreateRoom = false },
            onCreate = { name ->
                showCreateRoom = false
                scope.launch {
                    try {
                        vm.createRoom(name)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        showMessage("Could not create room: ${e.message}")
                    }
                }
            },
        )
    }

    if (showLeaveWorkspace) {
        AlertDialog(
            onDismissRequest = { showLeaveWorkspace = false },
            title = { Text("Leave workspace") },
            text = { Text("You'll stop seeing people and rooms in ${workspace.name}, and you'll need the server URL, API key and client ID to join again.") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveWorkspace = false
                    scope.launch {
                        try {
                            vm.leaveWorkspace() // the gate shows the setup screen
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            showMessage("Could not leave workspace: ${e.message}")
                        }
                    }
                }) { Text("Leave", color = AppColors.Error) }
            },
            dismissButton = { TextButton(onClick = { showLeaveWorkspace = false }) { Text("Cancel") } },
        )
    }

    if (showDeleteAccount) {
        AlertDialog(
            onDismissRequest = { showDeleteAccount = false },
            title = { Text("Delete account") },
            text = { Text("This permanently deletes your account and profile. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAccount = false
                    scope.launch {
                        try {
                            CoreServices.auth.deleteAccount() // the auth gate returns to sign-in
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            showMessage(AuthErrors.messageFor(e))
                        }
                    }
                }) { Text("Delete", color = AppColors.Error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteAccount = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AccountDrawer(
    name: String?,
    email: String?,
    photoUrl: String?,
    workspace: Workspace,
    onLeaveWorkspace: () -> Unit,
    onCopyIdToken: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val itemColors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
    ModalDrawerSheet(drawerContainerColor = Color.White) {
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(label = name ?: email.orEmpty(), photoUrl = photoUrl, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name?.takeIf { it.isNotBlank() } ?: "Signed in",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(email.orEmpty(), fontSize = 13.sp, color = AppColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HorizontalDivider(color = AppColors.Border)
        // Which workspace you're in. Only the masked key ever reaches the app.
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Dns, null, tint = AppColors.Primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Workspace", fontSize = 12.sp, color = AppColors.TextSecondary)
                Text(workspace.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Client ${workspace.clientId.take(8)}…  ·  Key ${workspace.apiKeyMasked}", fontSize = 12.sp, color = AppColors.TextSecondary)
            }
        }
        NavigationDrawerItem(
            label = { Text("Switch / leave workspace") },
            icon = { Icon(Icons.Outlined.SwapHoriz, null, tint = AppColors.TextSecondary) },
            selected = false,
            onClick = onLeaveWorkspace,
            colors = itemColors,
        )
        HorizontalDivider(color = AppColors.Border)
        Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
            // Debug aid for testing the Cloud Functions with curl. Hidden in release builds
            // because it hands out a live session token.
            if (BuildConfig.DEBUG) {
                NavigationDrawerItem(
                    label = { Text("Copy ID token") },
                    icon = { Icon(Icons.Outlined.VpnKey, null, tint = AppColors.TextSecondary) },
                    selected = false,
                    onClick = onCopyIdToken,
                    colors = itemColors,
                )
            }
        }
        HorizontalDivider(color = AppColors.Border)
        NavigationDrawerItem(
            label = { Text("Sign out") },
            icon = { Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = AppColors.TextSecondary) },
            selected = false,
            onClick = onSignOut,
            colors = itemColors,
        )
        NavigationDrawerItem(
            label = { Text("Delete account", color = AppColors.Error) },
            icon = { Icon(Icons.Rounded.DeleteOutline, null, tint = AppColors.Error) },
            selected = false,
            onClick = onDeleteAccount,
            colors = itemColors,
        )
        Spacer(Modifier.height(8.dp).background(Color.Transparent))
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("ID token", text))
}
