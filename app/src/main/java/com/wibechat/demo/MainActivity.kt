package com.wibechat.demo

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.wibechat.demo.call.ActiveCall
import com.wibechat.demo.call.CallArgs
import com.wibechat.demo.call.CallLauncher
import com.wibechat.demo.core.data.NotificationType
import com.wibechat.demo.core.data.PushKeys
import com.wibechat.demo.notifications.IncomingCallManager
import com.wibechat.demo.ui.AppRoot
import com.wibechat.demo.ui.theme.WibeChatTheme

/**
 * The only regular activity. Hosts the Compose UI, routes notification
 * intents to the call screen, and does system Picture-in-Picture for calls.
 */
class MainActivity : ComponentActivity() {

    private var hangUpReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WibeChatTheme { AppRoot() }
        }
        // Only on a fresh start; a recreated activity already handled its intent.
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Opens a call from an incoming-call Accept, a local notification tap, or an FCM notification tap. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            ACTION_ACCEPT_CALL -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
                val call = IncomingCallManager.accept(this, callId) ?: return
                CallLauncher.open(
                    CallArgs(roomId = call.roomId, title = call.callerName, startWithVideo = call.video, isOutgoing = false),
                )
            }

            else -> {
                // Local notifications and FCM's own notifications both carry the
                // push `data` as extras. Only call pushes open a room on tap.
                val type = intent.getStringExtra(PushKeys.TYPE)
                val roomId = intent.getStringExtra(PushKeys.ROOM_ID)
                if (NotificationType.isIncomingCall(type) && !roomId.isNullOrBlank()) {
                    CallLauncher.open(
                        CallArgs(
                            roomId = roomId,
                            title = intent.getStringExtra(PushKeys.TITLE),
                            startWithVideo = type == NotificationType.VIDEO_CALL,
                        ),
                    )
                }
            }
        }
        intent.action = null
    }

    // ---- Picture-in-Picture ------------------------------------------------

    /**
     * Home / app switch / Recents while a call is open → shrink into PiP.
     * (Not called for the screen turning off.)
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (ActiveCall.isActive.value) enterPip()
    }

    private fun enterPip(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(9, 16))
            .setActions(listOf(hangUpAction()))
            .build()
        return try {
            enterPictureInPictureMode(params)
        } catch (_: Exception) {
            false // some devices report support but refuse; fall back to a normal minimise
        }
    }

    private fun hangUpAction(): RemoteAction {
        val intent = Intent(ACTION_PIP_HANG_UP).setPackage(packageName)
        val pending = PendingIntent.getBroadcast(
            this, REQUEST_HANG_UP, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel)
        return RemoteAction(icon, "End call", "End call", pending)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        ActiveCall.setPipMode(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            if (hangUpReceiver == null) {
                hangUpReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) = ActiveCall.requestHangUp()
                }
                ContextCompat.registerReceiver(
                    this, hangUpReceiver, IntentFilter(ACTION_PIP_HANG_UP), ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }
        } else {
            unregisterHangUpReceiver()
        }
    }

    override fun onDestroy() {
        unregisterHangUpReceiver()
        super.onDestroy()
    }

    private fun unregisterHangUpReceiver() {
        hangUpReceiver?.let { runCatching { unregisterReceiver(it) } }
        hangUpReceiver = null
    }

    companion object {
        const val ACTION_ACCEPT_CALL = "com.wibechat.demo.action.ACCEPT_CALL"
        const val EXTRA_CALL_ID = "callId"
        private const val ACTION_PIP_HANG_UP = "com.wibechat.demo.action.PIP_HANG_UP"
        private const val REQUEST_HANG_UP = 1001
    }
}
