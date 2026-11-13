package com.wibechat.xmldemo.notifications

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wibechat.xmldemo.AppScope
import com.wibechat.xmldemo.MainActivity
import com.wibechat.xmldemo.R
import com.wibechat.xmldemo.databinding.ActivityIncomingCallBinding
import com.wibechat.xmldemo.ui.applySystemBarsPadding
import kotlinx.coroutines.launch

/**
 * Full-screen ringing UI, shown by the notification's full-screen intent
 * (screen off / locked) or when the user taps the ringing notification.
 */
class IncomingCallActivity : AppCompatActivity() {

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

        val b = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.applySystemBarsPadding()
        b.avatar.bind(call.callerName)
        b.callerName.text = call.callerName
        b.callType.text = if (call.video) "Incoming video call" else "Incoming call"
        b.accept.setImageResource(if (call.video) R.drawable.ic_videocam else R.drawable.ic_call)
        b.accept.setOnClickListener { accept(call) }
        b.decline.setOnClickListener { decline(call) }
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
