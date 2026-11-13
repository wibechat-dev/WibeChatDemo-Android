package com.wibechat.demo.feature.workspace

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wibechat.demo.core.CoreServices
import com.wibechat.demo.core.data.WorkspaceConfig
import com.wibechat.demo.core.data.WorkspaceConfigParser
import com.wibechat.demo.feature.auth.GoogleSignIn
import com.wibechat.demo.ui.components.AppButton
import com.wibechat.demo.ui.components.AppButtonVariant
import com.wibechat.demo.ui.components.AppTextField
import com.wibechat.demo.ui.components.AppTextFieldType
import com.wibechat.demo.ui.components.AuthLayout
import com.wibechat.demo.ui.components.rememberShowMessage
import com.wibechat.demo.ui.theme.AppColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val ClientIdPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

/**
 * Shown once per account, right after sign-in, until the user joins a
 * workspace. Takes the three values WibeChat gives each client — typed into
 * the fields, or pasted as the whole onboarding message into the box at the
 * top, which fills the fields for you ([WorkspaceConfigParser]).
 *
 * The API key goes to the `workspace` Cloud Function once and is stored
 * server-side; it is never saved on the phone.
 */
@Composable
fun WorkspaceSetupScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showMessage = rememberShowMessage()

    var pasted by remember { mutableStateOf("") }
    var pasteNote by remember { mutableStateOf<String?>(null) }
    var serverUrl by rememberSaveable { mutableStateOf("https://api.wibechat.com") }
    // Not saveable on purpose: the key isn't written to saved instance state.
    var apiKey by remember { mutableStateOf("") }
    var clientId by rememberSaveable { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }
    var keyError by remember { mutableStateOf<String?>(null) }
    var clientError by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var verifiedHost by remember { mutableStateOf<String?>(null) }
    val busy = testing || connecting

    fun applyConfig(text: String) {
        val config = WorkspaceConfigParser.parse(text)
        config.serverUrl?.let { serverUrl = it; urlError = null }
        config.apiKey?.let { apiKey = it; keyError = null }
        config.clientId?.let { clientId = it; clientError = null }
        verifiedHost = null
        val found = listOfNotNull(
            "server URL".takeIf { config.serverUrl != null },
            "API key".takeIf { config.apiKey != null },
            "client ID".takeIf { config.clientId != null },
        )
        pasteNote = when {
            text.isBlank() -> null
            found.isEmpty() -> "Couldn't find WibeChat details in that text."
            config.isComplete -> "Filled in all three fields."
            else -> "Found ${found.joinToString()}; fill in the rest below."
        }
    }

    fun validate(): Boolean {
        urlError = if (!Regex("^https?://[^\\s/]+.*$").matches(serverUrl.trim())) "Use a URL like https://api.wibechat.com" else null
        keyError = if (apiKey.isBlank()) "API key is required" else null
        clientError = if (!ClientIdPattern.matches(clientId.trim())) "Client ID looks like 13bb8ea1-e5a9-4718-ad13-a464f0831ce3" else null
        return urlError == null && keyError == null && clientError == null
    }

    fun submit(dryRun: Boolean) {
        if (!validate()) return
        if (dryRun) testing = true else connecting = true
        scope.launch {
            try {
                val workspace = CoreServices.workspace.connect(
                    WorkspaceConfig(serverUrl = serverUrl, apiKey = apiKey, clientId = clientId),
                    dryRun,
                )
                if (dryRun) verifiedHost = workspace.name else apiKey = ""
                // When joined, the gate switches to Home once users/{uid}.workspaceId is set.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                verifiedHost = null
                showMessage(e.message ?: "Couldn't connect. Please try again.")
            } finally {
                testing = false
                connecting = false
            }
        }
    }

    AuthLayout(
        title = "Connect your workspace.",
        subtitle = "Enter the WibeChat details you received. You only do this once. Everyone with the same client ID can see and call each other.",
    ) {
        // 1. Paste the whole onboarding message → fields fill themselves.
        AppTextField(
            value = pasted,
            onValueChange = { pasted = it; applyConfig(it) },
            label = "Paste your WibeChat details",
            hint = "WIBECHAT_SERVER_URL=…\nWIBECHAT_API_KEY=…\nWIBECHAT_CLIENT_ID=…",
            enabled = !busy,
            type = AppTextFieldType.Code,
            minLines = 3,
            imeAction = ImeAction.Default,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pasteNote ?: "Paste the message and the fields below fill in.",
                color = if (pasteNote?.startsWith("Couldn't") == true) AppColors.Error else AppColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                enabled = !busy,
                onClick = {
                    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                    val text = clip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    if (text.isBlank()) showMessage("The clipboard is empty.") else { pasted = text; applyConfig(text) }
                },
            ) {
                Icon(Icons.Outlined.ContentPaste, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Paste")
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = AppColors.Border)

        // 2. Or type them.
        AppTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it; urlError = null; verifiedHost = null },
            type = AppTextFieldType.Url,
            label = "Server URL",
            hint = "https://api.wibechat.com",
            error = urlError,
            enabled = !busy,
            leadingIcon = { Icon(Icons.Outlined.Dns, null, tint = AppColors.TextSecondary) },
        )
        Spacer(Modifier.height(20.dp))
        AppTextField(
            value = apiKey,
            onValueChange = { apiKey = it; keyError = null; verifiedHost = null },
            type = AppTextFieldType.Password,
            label = "API key",
            hint = "lk_…",
            error = keyError,
            enabled = !busy,
            leadingIcon = { Icon(Icons.Outlined.Key, null, tint = AppColors.TextSecondary) },
        )
        Spacer(Modifier.height(20.dp))
        AppTextField(
            value = clientId,
            onValueChange = { clientId = it; clientError = null; verifiedHost = null },
            type = AppTextFieldType.Code,
            label = "Client ID",
            hint = "13bb8ea1-e5a9-4718-ad13-a464f0831ce3",
            error = clientError,
            enabled = !busy,
            imeAction = ImeAction.Done,
            onImeAction = { if (!busy) submit(dryRun = false) },
            leadingIcon = { Icon(Icons.Outlined.Badge, null, tint = AppColors.TextSecondary) },
        )

        verifiedHost?.let { host ->
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = AppColors.Success, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Connection works: $host", color = AppColors.Success, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(24.dp))
        AppButton(label = "Connect workspace", isLoading = connecting, onClick = if (busy) null else { { submit(dryRun = false) } })
        Spacer(Modifier.height(12.dp))
        AppButton(
            label = "Test connection",
            variant = AppButtonVariant.Outlined,
            isLoading = testing,
            onClick = if (busy) null else { { submit(dryRun = true) } },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Your API key is sent once over HTTPS and stored on the server. It is never saved on this phone or shown to other users.",
            color = AppColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = {
                scope.launch {
                    GoogleSignIn.signOut(context)
                    CoreServices.auth.signOut()
                }
            }, enabled = !busy) { Text("Use a different account") }
        }
    }
}
