package com.wibechat.demo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wibechat.demo.ui.theme.AppColors
import com.wibechat.demo.ui.theme.AppRadius

enum class AppButtonVariant { Filled, Outlined, Text }

/**
 * The shared button: 54dp tall, rounded 16dp, spinner while [isLoading],
 * slight press-shrink. Same API shape as the Flutter `AppButton`.
 */
@Composable
fun AppButton(
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.Filled,
    isLoading: Boolean = false,
    height: Dp = 54.dp,
    fillWidth: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val disabled = onClick == null || isLoading
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(AppRadius.Button)
    val sizeModifier = modifier
        .pressScale(interaction, enabled = !disabled)
        .defaultMinSize(minHeight = height)
        .let { if (fillWidth) it.fillMaxWidth() else it }

    val content: @Composable () -> Unit = {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.4.dp,
                color = if (variant == AppButtonVariant.Filled) Color.White else AppColors.Primary,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(8.dp))
                }
                Text(label, textAlign = TextAlign.Center, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    when (variant) {
        AppButtonVariant.Filled -> Button(
            onClick = { onClick?.invoke() },
            enabled = !disabled,
            modifier = sizeModifier,
            shape = shape,
            interactionSource = interaction,
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.Primary,
                contentColor = Color.White,
                disabledContainerColor = if (isLoading) AppColors.Primary else AppColors.Disabled,
                disabledContentColor = Color.White,
            ),
        ) { content() }

        AppButtonVariant.Outlined -> OutlinedButton(
            onClick = { onClick?.invoke() },
            enabled = !disabled,
            modifier = sizeModifier,
            shape = shape,
            interactionSource = interaction,
            border = BorderStroke(1.4.dp, if (disabled) AppColors.Disabled else AppColors.Primary),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Primary),
        ) { content() }

        AppButtonVariant.Text -> TextButton(
            onClick = { onClick?.invoke() },
            enabled = !disabled,
            modifier = sizeModifier,
            shape = shape,
            interactionSource = interaction,
        ) { content() }
    }
}
