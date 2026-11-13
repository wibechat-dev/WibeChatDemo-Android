package com.wibechat.demo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wibechat.demo.ui.theme.AppColors
import com.wibechat.demo.ui.theme.AppRadius

enum class AppTextFieldType {
    Normal, Email, Password,

    /** URLs: URL keyboard, no auto-capitalisation or autocorrect. */
    Url,

    /** Keys/codes: typed exactly as given, no autocorrect. */
    Code,
}

/** Default validation rules, same as the Flutter `AppTextField`. */
object Validators {
    private val emailRegex = Regex("""^[\w.\-]+@([\w\-]+\.)+[\w\-]{2,4}$""")

    fun email(value: String): String? = when {
        value.isBlank() -> "Email is required"
        !emailRegex.matches(value.trim()) -> "Enter a valid email"
        else -> null
    }

    fun password(value: String): String? = when {
        value.isEmpty() -> "Password is required"
        value.length < 6 -> "Minimum 6 characters"
        else -> null
    }

    fun forType(type: AppTextFieldType, value: String): String? = when (type) {
        AppTextFieldType.Email -> email(value)
        AppTextFieldType.Password -> password(value)
        else -> null
    }
}

/**
 * Label above a rounded, filled outlined field. The [type] picks keyboard,
 * icon and obscuring; [error] shows under the field.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    type: AppTextFieldType = AppTextFieldType.Normal,
    label: String? = null,
    hint: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    /** > 1 makes a multi-line box (e.g. for pasting a config message). */
    minLines: Int = 1,
) {
    var obscured by remember { mutableStateOf(type == AppTextFieldType.Password) }

    val icon: (@Composable () -> Unit)? = leadingIcon ?: when (type) {
        AppTextFieldType.Email -> { { Icon(Icons.Outlined.Email, null, tint = AppColors.TextSecondary) } }
        AppTextFieldType.Password -> { { Icon(Icons.Outlined.Lock, null, tint = AppColors.TextSecondary) } }
        else -> null
    }

    val trailing: (@Composable () -> Unit)? = if (type == AppTextFieldType.Password) {
        {
            IconButton(onClick = { obscured = !obscured }) {
                Icon(
                    if (obscured) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (obscured) "Show password" else "Hide password",
                    tint = AppColors.TextSecondary,
                )
            }
        }
    } else {
        null
    }

    Column(modifier) {
        if (label != null) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
            Spacer(Modifier.height(6.dp))
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
            enabled = enabled,
            singleLine = minLines <= 1,
            minLines = minLines,
            textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 15.sp),
            placeholder = hint?.let { { Text(it, color = AppColors.Hint, fontSize = 14.sp) } },
            leadingIcon = icon,
            trailingIcon = trailing,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            visualTransformation = if (obscured) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = when (type) {
                    AppTextFieldType.Email -> KeyboardType.Email
                    AppTextFieldType.Password -> KeyboardType.Password
                    AppTextFieldType.Url -> KeyboardType.Uri
                    else -> KeyboardType.Text
                },
                capitalization = if (type == AppTextFieldType.Normal) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                autoCorrectEnabled = type == AppTextFieldType.Normal,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
            shape = RoundedCornerShape(AppRadius.Field),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AppColors.Background,
                unfocusedContainerColor = AppColors.Background,
                disabledContainerColor = AppColors.Background,
                errorContainerColor = AppColors.Background,
                unfocusedBorderColor = AppColors.Border,
                focusedBorderColor = AppColors.Primary,
                errorBorderColor = AppColors.Error,
            ),
        )
    }
}
