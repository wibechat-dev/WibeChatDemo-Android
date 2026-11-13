package com.wibechat.demo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wibechat.demo.ui.theme.AppColors

/**
 * Round avatar: the photo when there is one (e.g. a Google account photo),
 * otherwise the first letter of [label].
 */
@Composable
fun UserAvatar(
    label: String,
    modifier: Modifier = Modifier,
    photoUrl: String? = null,
    size: Dp = 40.dp,
    background: Brush = Brush.linearGradient(listOf(AppColors.Primary, AppColors.Primary)),
    borderColor: Color? = null,
) {
    val url = photoUrl?.trim()?.takeIf { it.isNotEmpty() }
    Box(
        modifier
            .size(size)
            .let { if (borderColor != null) it.border(1.5.dp, borderColor, CircleShape) else it }
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        } else {
            val fontSize = with(LocalDensity.current) { (size * 0.35f).toSp() }
            Text(
                text = label.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
