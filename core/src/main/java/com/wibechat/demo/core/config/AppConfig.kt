package com.wibechat.demo.core.config

import com.wibechat.demo.core.BuildConfig

/**
 * Every environment-specific value in one place. Values come from
 * `secrets.properties` at build time (see `secrets.defaults.properties`),
 * so nothing project-specific is hard-coded in source.
 */
object AppConfig {

    /** Base URL of the deployed Cloud Functions, without a trailing slash. */
    val functionsBaseUrl: String = BuildConfig.WIBECHAT_FUNCTIONS_BASE_URL.trimEnd('/')

    /** `GET ?room=<id>` → `{ serverUrl, token }`. Requires a Firebase ID token. */
    val tokenEndpoint: String get() = "$functionsBaseUrl/wibechatToken/api/wibechat-token"

    /** `POST { user_id, title, desc, data }` → pushes to that user's device. */
    val sendNotificationEndpoint: String get() = "$functionsBaseUrl/sendNotification/api/send-notification"

    /** `POST { serverUrl, apiKey, clientId, dryRun? }` → joins (or just tests) a workspace. */
    val workspaceConnectEndpoint: String get() = "$functionsBaseUrl/workspace/api/connect"

    /** `POST` → leaves the current workspace. */
    val workspaceLeaveEndpoint: String get() = "$functionsBaseUrl/workspace/api/leave"

    /** Named Firestore database; "(default)" when not set. */
    val firestoreDatabaseId: String = BuildConfig.FIRESTORE_DATABASE_ID.ifBlank { "(default)" }

    /** OAuth web client ID for Google sign-in, or empty when Google sign-in is not set up. */
    val googleWebClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID

    val isGoogleSignInConfigured: Boolean get() = googleWebClientId.isNotBlank()
}
