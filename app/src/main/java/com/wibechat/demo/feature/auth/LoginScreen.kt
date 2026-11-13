package com.wibechat.demo.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wibechat.demo.R
import com.wibechat.demo.core.CoreServices
import com.wibechat.demo.core.auth.AuthErrors
import com.wibechat.demo.ui.components.AppButton
import com.wibechat.demo.ui.components.AppButtonVariant
import com.wibechat.demo.ui.components.AppTextField
import com.wibechat.demo.ui.components.AppTextFieldType
import com.wibechat.demo.ui.components.AuthLayout
import com.wibechat.demo.ui.components.Validators
import com.wibechat.demo.ui.components.rememberShowMessage
import com.wibechat.demo.ui.theme.AppColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onCreateAccount: () -> Unit) {
    val auth = CoreServices.auth
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showMessage = rememberShowMessage()

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var googleLoading by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    val busy = loading || googleLoading

    fun submit() {
        emailError = Validators.email(email)
        passwordError = Validators.password(password)
        if (emailError != null || passwordError != null) return
        loading = true
        scope.launch {
            try {
                auth.signIn(email, password) // the auth gate switches to Home on success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(AuthErrors.messageFor(e))
            } finally {
                loading = false
            }
        }
    }

    fun signInWithGoogle() {
        googleLoading = true
        scope.launch {
            try {
                val idToken = GoogleSignIn.requestIdToken(context) ?: return@launch // picker dismissed
                auth.signInWithGoogle(idToken)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                showMessage(e.message ?: "Could not sign in with Google.")
            } catch (e: Exception) {
                showMessage("Could not sign in with Google: ${AuthErrors.messageFor(e)}")
            } finally {
                googleLoading = false
            }
        }
    }

    AuthLayout(title = "Welcome back.", subtitle = "Your people are one conversation away.") {
        AppTextField(
            value = email,
            onValueChange = { email = it; emailError = null },
            type = AppTextFieldType.Email,
            label = "Email address",
            hint = "you@example.com",
            error = emailError,
            enabled = !busy,
        )
        Spacer(Modifier.height(20.dp))
        AppTextField(
            value = password,
            onValueChange = { password = it; passwordError = null },
            type = AppTextFieldType.Password,
            label = "Password",
            hint = "Enter your password",
            error = passwordError,
            enabled = !busy,
            imeAction = ImeAction.Done,
            onImeAction = { if (!busy) submit() },
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = { showReset = true }, enabled = !busy) { Text("Forgot password?") }
        }
        Spacer(Modifier.height(8.dp))
        AppButton(label = "Sign in", isLoading = loading, onClick = if (busy) null else ::submit)

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(Modifier.weight(1f), color = AppColors.Border)
            Text(
                "or continue with",
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            HorizontalDivider(Modifier.weight(1f), color = AppColors.Border)
        }

        AppButton(
            label = "Google",
            variant = AppButtonVariant.Outlined,
            isLoading = googleLoading,
            onClick = if (busy) null else ::signInWithGoogle,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "New to ${stringResource(R.string.app_name_title_case)}?",
            color = AppColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onCreateAccount, enabled = !busy) { Text("Create an account") }
        }
    }

    if (showReset) {
        ResetPasswordDialog(
            initialEmail = email.trim(),
            onDismiss = { showReset = false },
            onSend = { address ->
                showReset = false
                scope.launch {
                    try {
                        auth.sendPasswordResetEmail(address)
                        showMessage("Password reset link sent to $address")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        showMessage(AuthErrors.messageFor(e))
                    }
                }
            },
        )
    }
}

@Composable
private fun ResetPasswordDialog(initialEmail: String, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var email by remember { mutableStateOf(initialEmail) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun send() {
        error = Validators.email(email)
        if (error == null) onSend(email.trim())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset password") },
        text = {
            AppTextField(
                value = email,
                onValueChange = { email = it; error = null },
                type = AppTextFieldType.Email,
                label = "Email",
                hint = "you@example.com",
                error = error,
                imeAction = ImeAction.Send,
                onImeAction = ::send,
                focusRequester = focus,
            )
        },
        confirmButton = { TextButton(onClick = ::send) { Text("Send link") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(4.dp))
            }
        },
    )
}
