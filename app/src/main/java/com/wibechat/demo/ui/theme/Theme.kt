package com.wibechat.demo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Same palette as the Flutter app's `AppColors`. */
object AppColors {
    val Primary = Color(0xFF4255D4)
    val PrimaryDark = Color(0xFF283998)
    val PrimaryLight = Color(0xFFEEF0FF)
    val Accent = Color(0xFF7357D8)
    val Background = Color(0xFFF6F7FB)
    val Surface = Color.White
    val TextPrimary = Color(0xFF17213B)
    val TextSecondary = Color(0xFF657089)
    val Hint = Color(0xFF79839A)
    val Border = Color(0xFFE5E8F1)
    val Error = Color(0xFFD83C54)
    val Disabled = Color(0xFFD9DEEA)
    val CallBackground = Color(0xFF10182B)
    val CallSurface = Color(0xFF202B42)
    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFE65100)
    val WarningLight = Color(0xFFF57C00)

    val BrandGradient = Brush.linearGradient(listOf(Primary, Accent))
}

object AppRadius {
    val Field = 16.dp
    val Button = 16.dp
    val Card = 24.dp
}

private val ColorScheme = lightColorScheme(
    primary = AppColors.Primary,
    onPrimary = Color.White,
    primaryContainer = AppColors.PrimaryLight,
    onPrimaryContainer = AppColors.PrimaryDark,
    secondary = AppColors.Accent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E4F9),
    onSecondaryContainer = AppColors.PrimaryDark,
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.Background,
    onSurfaceVariant = AppColors.TextSecondary,
    surfaceContainer = AppColors.Surface,
    surfaceContainerHigh = AppColors.Surface,
    surfaceContainerLow = AppColors.Surface,
    outline = AppColors.Border,
    outlineVariant = AppColors.Border,
    error = AppColors.Error,
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 34.sp, lineHeight = 39.sp, letterSpacing = (-1.2).sp,
        fontWeight = FontWeight.ExtraBold, color = AppColors.TextPrimary,
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.8).sp,
        fontWeight = FontWeight.ExtraBold, color = AppColors.TextPrimary,
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp, letterSpacing = (-0.5).sp,
        fontWeight = FontWeight.Bold, color = AppColors.TextPrimary,
    ),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = AppColors.TextPrimary),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = AppColors.TextPrimary),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, color = AppColors.TextSecondary),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun WibeChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
