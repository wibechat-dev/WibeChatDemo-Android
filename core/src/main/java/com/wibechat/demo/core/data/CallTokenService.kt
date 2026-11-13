package com.wibechat.demo.core.data

import com.wibechat.demo.core.config.AppConfig
import com.wibechat.demo.core.network.ApiService

/** Media server URL + room token returned by the token function. */
data class RoomToken(val serverUrl: String, val token: String)

/**
 * Calls the deployed `wibechatToken` function. The server checks the
 * Firebase ID token and that the room exists, picks the identity from the
 * verified user, and signs the token. The app never sees API secrets.
 */
class CallTokenService(private val api: ApiService) {

    suspend fun fetchToken(roomId: String): RoomToken {
        val body = api.get(AppConfig.tokenEndpoint, queryParameters = mapOf("room" to roomId))
        return RoomToken(serverUrl = body.getString("serverUrl"), token = body.getString("token"))
    }
}
