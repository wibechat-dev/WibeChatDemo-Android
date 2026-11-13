package com.wibechat.xmldemo

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Asks once at startup for what ringing needs: notification permission
 * (Android 13+) and full-screen intents (Android 14+, otherwise an incoming
 * call is only a heads-up instead of taking the screen).
 *
 * Create it in the activity's onCreate (it registers a result launcher), then call [ask].
 */
class StartupPermissions(private val activity: ComponentActivity) {

    private val notificationLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        maybeAskFullScreen()
    }

    fun ask() {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            maybeAskFullScreen()
        }
    }

    private fun maybeAskFullScreen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val nm = activity.getSystemService(NotificationManager::class.java)
        if (prefs.getBoolean(KEY_FSI_ASKED, false) || nm.canUseFullScreenIntent()) return

        MaterialAlertDialogBuilder(activity)
            .setTitle("Show incoming calls")
            .setMessage("Allow full-screen notifications so incoming calls can ring on your screen, even when it's locked.")
            .setPositiveButton("Open settings") { _, _ ->
                activity.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${activity.packageName}")),
                )
            }
            .setNegativeButton("Not now", null)
            .setOnDismissListener { prefs.edit().putBoolean(KEY_FSI_ASKED, true).apply() }
            .show()
    }

    private companion object {
        const val PREFS = "startup_permissions"
        const val KEY_FSI_ASKED = "full_screen_intent_asked"
    }
}
