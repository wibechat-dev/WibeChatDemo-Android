package com.wibechat.xmldemo

import android.app.Application
import com.wibechat.demo.core.CoreServices
import com.wibechat.xmldemo.notifications.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class WibeChatXmlApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        // Start listening to Firebase Auth right away so MainActivity's gate has a state.
        CoreServices.auth
    }
}

/**
 * App-lifetime scope for fire-and-forget work that must outlive a screen,
 * e.g. writing the call log and notifying the other person after hang-up.
 */
object AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
