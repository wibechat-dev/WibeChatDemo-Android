package com.wibechat.demo.notifications

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.wibechat.demo.AppScope
import com.wibechat.demo.MainActivity
import com.wibechat.demo.ui.components.CallHalo
import com.wibechat.demo.ui.components.UserAvatar
import com.wibechat.demo.ui.theme.AppColors
import com.wibechat.demo.ui.theme.WibeChatTheme
import kotlinx.coroutines.launch

/**
 * Full-screen ringing UI, shown by the notification's full-screen intent
 * (screen off / locked) or when the user taps the ringing notification.
 */
class IncomingCallActivity : ComponentActivity() {

    private val callId: String? get() = intent.getStringExtra(EXTRA_CALL_ID)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val call = callId?.let { IncomingCallManager.get(this, it) }
        if (call == null) {
            finish()
            return
        }

        // Close as soon as the call is answered/declined/timed out/cancelled elsewhere.
        lifecycleScope.launch {
            IncomingCallManager.changed.collect {
                if (!IncomingCallManager.isRinging(this@IncomingCallActivity, call.id)) finish()
            }
        }

        setContent {
            WibeChatTheme {
                IncomingCallContent(call = call, onAccept = { accept(call) }, onDecline = { decline(call) })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val id = callId
        if (id == null || !IncomingCallManager.isRinging(this, id)) finish()
    }

    private fun accept(call: IncomingCall) {
        val open = {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_ACCEPT_CALL)
                    .putExtra(MainActivity.EXTRA_CALL_ID, call.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            finish()
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            // Ask the user to unlock first; the call opens in the main app.
            keyguard.requestDismissKeyguard(this as Activity, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = open()
            })
        } else {
            open()
        }
    }

    private fun decline(call: IncomingCall) {
        val appContext = applicationContext
        AppScope.launch { IncomingCallManager.decline(appContext, call.id) }
        finish()
    }

    companion object {
        private const val EXTRA_CALL_ID = "callId"

        fun intent(context: Context, callId: String): Intent =
            Intent(context, IncomingCallActivity::class.java)
                .putExtra(EXTRA_CALL_ID, callId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}

@Composable
private fun IncomingCallContent(call: IncomingCall, onAccept: () -> Unit, onDecline: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(AppColors.CallBackground)
            .safeDrawingPadding(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CallHalo {
                UserAvatar(label = call.callerName, size = 112.dp, background = AppColors.BrandGradient)
            }
            Spacer(Modifier.height(28.dp))
            Text(
                call.callerName,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (call.video) "Incoming video call" else "Incoming call",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 48.dp, vertical = 56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RoundAction("Decline", Icons.Rounded.CallEnd, AppColors.Error, onDecline)
            RoundAction("Accept", if (call.video) Icons.Rounded.Videocam else Icons.Rounded.Call, AppColors.Success, onAccept)
        }
    }
}

@Composable
private fun RoundAction(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.White),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
    }
}
