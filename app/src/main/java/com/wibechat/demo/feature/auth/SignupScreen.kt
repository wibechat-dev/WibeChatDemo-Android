package com.wibechat.demo.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.wibechat.demo.core.CoreServices
import com.wibechat.demo.core.auth.AuthErrors
import com.wibechat.demo.ui.components.AppButton
import com.wibechat.demo.ui.components.AppTextField
import com.wibechat.demo.ui.components.AppTextFieldType
import com.wibechat.demo.ui.components.AuthLayout
import com.wibechat.demo.ui.components.Validators
import com.wibechat.demo.ui.components.rememberShowMessage
import com.wibechat.demo.ui.theme.AppColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SignupScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val showMessage = rememberShowMessage()

    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    BackHandler(enabled = loading) { /* stay put while the account is being created */ }

    fun submit() {
        nameError = if (name.isBlank()) "Name is required" else null
        emailError = Validators.email(email)
        passwordError = Validators.password(password)
        if (nameError != null || emailError != null || passwordError != null) return
        loading = true
        scope.launch {
            try {
                // The auth gate swaps this whole graph for Home on success.
                CoreServices.auth.signUp(email, password, name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(AuthErrors.messageFor(e))
                loading = false
            }
        }
    }

    AuthLayout(
        title = "Closer starts here.",
        subtitle = "Create your account and make room for great conversations.",
        onBack = if (loading) null else onBack,
    ) {
        AppTextField(
            value = name,
            onValueChange = { name = it; nameError = null },
            label = "Your name",
            hint = "How should we call you?",
            error = nameError,
            enabled = !loading,
            leadingIcon = { Icon(Icons.Outlined.PersonOutline, null, tint = AppColors.TextSecondary) },
        )
        Spacer(Modifier.height(20.dp))
        AppTextField(
            value = email,
            onValueChange = { email = it; emailError = null },
            type = AppTextFieldType.Email,
            label = "Email address",
            hint = "you@example.com",
            error = emailError,
            enabled = !loading,
        )
        Spacer(Modifier.height(20.dp))
        AppTextField(
            value = password,
            onValueChange = { password = it; passwordError = null },
            type = AppTextFieldType.Password,
            label = "Password",
            hint = "At least 6 characters",
            error = passwordError,
            enabled = !loading,
            imeAction = ImeAction.Done,
            onImeAction = { if (!loading) submit() },
        )
        Spacer(Modifier.height(28.dp))
        AppButton(label = "Create account", isLoading = loading, onClick = if (loading) null else ::submit)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onBack, enabled = !loading) { Text("Already a member? Sign in") }
        }
    }
}
