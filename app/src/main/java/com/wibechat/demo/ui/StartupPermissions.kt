package com.wibechat.demo.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Asks once at startup for what ringing needs, like the Flutter app does:
 * notification permission (Android 13+) and full-screen intents (Android 14+,
 * otherwise an incoming call is only a heads-up instead of taking the screen).
 */
@Composable
fun StartupPermissions() {
    val context = LocalContext.current
    var showFullScreenPrompt by rememberSaveable { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        showFullScreenPrompt = needsFullScreenIntentPermission(context)
    }

    LaunchedEffect(Unit) {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            showFullScreenPrompt = needsFullScreenIntentPermission(context)
        }
    }

    if (showFullScreenPrompt) {
        AlertDialog(
            onDismissRequest = { showFullScreenPrompt = false; markAsked(context) },
            title = { Text("Show incoming calls") },
            text = { Text("Allow full-screen notifications so incoming calls can ring on your screen, even when it's locked.") },
            confirmButton = {
                TextButton(onClick = {
                    showFullScreenPrompt = false
                    markAsked(context)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
                        )
                    }
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { showFullScreenPrompt = false; markAsked(context) }) { Text("Not now") }
            },
        )
    }
}

private const val PREFS = "startup_permissions"
private const val KEY_FSI_ASKED = "full_screen_intent_asked"

private fun needsFullScreenIntentPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
    val asked = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_FSI_ASKED, false)
    val nm = context.getSystemService(NotificationManager::class.java)
    return !asked && !nm.canUseFullScreenIntent()
}

private fun markAsked(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_FSI_ASKED, true).apply()
}
