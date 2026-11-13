package com.wibechat.demo.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Decline (notification button) and the ring timeout (alarm). Neither opens the app. */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        val appContext = context.applicationContext
        val pending = goAsync() // keep the process alive while we hit the network
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_DECLINE -> IncomingCallManager.decline(appContext, callId)
                    ACTION_TIMEOUT -> IncomingCallManager.timeout(appContext, callId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DECLINE = "com.wibechat.demo.action.DECLINE_CALL"
        const val ACTION_TIMEOUT = "com.wibechat.demo.action.CALL_TIMEOUT"
        const val EXTRA_CALL_ID = "callId"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
