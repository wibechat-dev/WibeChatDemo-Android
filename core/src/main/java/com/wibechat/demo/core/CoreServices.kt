package com.wibechat.demo.core

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.wibechat.demo.core.auth.AuthRepository
import com.wibechat.demo.core.config.AppConfig
import com.wibechat.demo.core.data.CallLogRepository
import com.wibechat.demo.core.data.CallTokenService
import com.wibechat.demo.core.data.PushNotificationApi
import com.wibechat.demo.core.data.RoomRepository
import com.wibechat.demo.core.data.UserRepository
import com.wibechat.demo.core.data.WorkspaceRepository
import com.wibechat.demo.core.network.ApiService

/**
 * Tiny service locator: one shared instance of each repository/API client.
 * Swap for Hilt/Koin in a production app; kept plain here so the demo reads
 * top to bottom.
 */
object CoreServices {

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(FirebaseApp.getInstance(), AppConfig.firestoreDatabaseId)
    }

    val auth: AuthRepository by lazy {
        AuthRepository(FirebaseAuth.getInstance(), firestore, FirebaseMessaging.getInstance())
    }

    private val api: ApiService by lazy { ApiService(idTokenProvider = { auth.idToken() }) }

    val users: UserRepository by lazy { UserRepository(firestore) }
    val rooms: RoomRepository by lazy { RoomRepository(firestore) }
    val callLogs: CallLogRepository by lazy { CallLogRepository(firestore) }
    val callTokens: CallTokenService by lazy { CallTokenService(api) }
    val push: PushNotificationApi by lazy { PushNotificationApi(api) }
    val workspace: WorkspaceRepository by lazy { WorkspaceRepository(firestore, api, auth) }
}
