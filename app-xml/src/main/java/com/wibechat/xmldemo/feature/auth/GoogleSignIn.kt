package com.wibechat.xmldemo.feature.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.wibechat.demo.core.config.AppConfig

/**
 * Google sign-in through Credential Manager. Returns a Google ID token for
 * Firebase, or null if the user closed the account picker (not an error).
 *
 * Needs GOOGLE_WEB_CLIENT_ID in secrets.properties and the app's SHA-1
 * fingerprint registered in the Firebase console.
 */
object GoogleSignIn {

    suspend fun requestIdToken(activityContext: Context): String? {
        check(AppConfig.isGoogleSignInConfigured) {
            "Google sign-in isn't set up. Add GOOGLE_WEB_CLIENT_ID to secrets.properties."
        }
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(AppConfig.googleWebClientId).build())
            .build()
        return try {
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } else {
                error("Unexpected credential type from Google sign-in.")
            }
        } catch (_: GetCredentialCancellationException) {
            null
        }
    }

    /** Best-effort; signing out of Firebase is what actually matters. */
    suspend fun signOut(context: Context) {
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }
}
