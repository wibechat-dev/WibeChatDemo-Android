package com.wibechat.demo.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wibechat.demo.call.CallArgs
import com.wibechat.demo.call.CallLauncher
import com.wibechat.demo.core.CoreServices
import com.wibechat.demo.core.auth.SessionState
import com.wibechat.demo.core.data.WorkspaceState
import com.wibechat.demo.core.model.Workspace
import com.wibechat.demo.feature.auth.LoginScreen
import com.wibechat.demo.feature.auth.SignupScreen
import com.wibechat.demo.feature.call.CallScreen
import com.wibechat.demo.feature.home.HomeScreen
import com.wibechat.demo.feature.workspace.WorkspaceSetupScreen
import com.wibechat.demo.ui.components.LocalSnackbarHostState

private object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val HOME = "home"
    const val CALL = "call?room={room}&title={title}&video={video}&outgoing={outgoing}"

    fun call(args: CallArgs) = "call?room=${Uri.encode(args.roomId)}" +
        "&title=${Uri.encode(args.title.orEmpty())}" +
        "&video=${args.startWithVideo}&outgoing=${args.isOutgoing}"
}

/**
 * Gate (like Flutter's `AuthGate`), in three steps:
 * 1. signed out → login / sign-up
 * 2. signed in, no workspace yet → "Connect your workspace" (once per account)
 * 3. signed in with a workspace → home + calls
 * Membership is read from Firestore, so a returning user skips step 2.
 */
@Composable
fun AppRoot() {
    val snackbarHost = remember { SnackbarHostState() }
    val session by CoreServices.auth.session.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHost) {
        StartupPermissions()
        when (val s = session) {
            SessionState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            SessionState.SignedOut -> SignedOutGraph()
            is SessionState.SignedIn -> key(s.user.uid) { WorkspaceGate() }
        }
    }
}

@Composable
private fun SignedOutGraph() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(onCreateAccount = { nav.navigate(Routes.SIGNUP) })
        }
        composable(Routes.SIGNUP) {
            SignupScreen(onBack = { nav.popBackStack() })
        }
    }
}

@Composable
private fun WorkspaceGate() {
    val state by CoreServices.workspace.state.collectAsStateWithLifecycle()
    when (val w = state) {
        WorkspaceState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        WorkspaceState.None -> WorkspaceSetupScreen()
        is WorkspaceState.Joined -> key(w.workspace.id) { SignedInGraph(w.workspace) }
    }
}

@Composable
private fun SignedInGraph(workspace: Workspace) {
    val nav = rememberNavController()
    val pendingCall by CallLauncher.pending.collectAsStateWithLifecycle()

    // Any request to open a call (button, notification, Accept) lands here.
    LaunchedEffect(pendingCall) {
        val args = pendingCall ?: return@LaunchedEffect
        CallLauncher.consume()
        val current = nav.currentBackStackEntry
        val alreadyInRoom = current?.destination?.route == Routes.CALL &&
            current.arguments?.getString("room") == args.roomId
        if (!alreadyInRoom) nav.navigate(Routes.call(args)) { launchSingleTop = true }
    }

    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(workspace) }
        composable(
            Routes.CALL,
            arguments = listOf(
                navArgument("room") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("video") { type = NavType.BoolType; defaultValue = true },
                navArgument("outgoing") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val a = entry.arguments!!
            val args = CallArgs(
                roomId = a.getString("room").orEmpty(),
                title = a.getString("title")?.takeIf { it.isNotEmpty() },
                startWithVideo = a.getBoolean("video"),
                isOutgoing = a.getBoolean("outgoing"),
            )
            CallScreen(args = args, onClose = { nav.popBackStack() })
        }
    }
}
