package com.wibechat.demo.core.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException

/** Turns an auth failure into a message that is safe to show in the UI. */
object AuthErrors {
    fun messageFor(error: Throwable): String = when (error) {
        is FirebaseTooManyRequestsException -> "Too many attempts. Please try again later."
        is FirebaseNetworkException -> "No connection. Check your network and try again."
        is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_INVALID_EMAIL" -> "That email address looks invalid."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "An account already exists for that email."
            "ERROR_WEAK_PASSWORD" -> "Password is too weak — use at least 6 characters."
            "ERROR_USER_NOT_FOUND", "ERROR_INVALID_CREDENTIAL" -> "No account found for that email and password."
            "ERROR_WRONG_PASSWORD" -> "Incorrect password."
            "ERROR_USER_DISABLED" -> "This account has been disabled."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please try again later."
            "ERROR_REQUIRES_RECENT_LOGIN" -> "Please sign out and sign in again, then retry."
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
                "An account already exists with this email using a different sign-in method."
            else -> error.message ?: "Something went wrong. Please try again."
        }
        else -> error.message ?: "Something went wrong. Please try again."
    }
}
