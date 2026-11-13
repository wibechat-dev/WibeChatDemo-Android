package com.wibechat.demo.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.launch

/**
 * One snackbar queue for the whole app (like Flutter's ScaffoldMessenger).
 * Each screen's Scaffold renders it; [rememberShowMessage] posts to it.
 */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun rememberShowMessage(): (String) -> Unit {
    val host = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    return remember(host, scope) {
        { message: String ->
            scope.launch {
                host.currentSnackbarData?.dismiss()
                host.showSnackbar(message)
            }
        }
    }
}
