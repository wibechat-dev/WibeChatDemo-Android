package com.wibechat.demo.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wibechat.demo.ui.theme.AppColors

/** True when the user turned animations off (Settings > Accessibility). */
@Composable
fun animationsDisabled(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

/** Short fade + slide-up entrance; replays whenever [replayKey] changes. */
@Composable
fun AppReveal(replayKey: Any? = Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val disabled = animationsDisabled()
    val progress = remember { Animatable(if (disabled) 1f else 0f) }
    val density = LocalDensity.current
    LaunchedEffect(replayKey) {
        if (disabled) progress.snapTo(1f) else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(360, easing = EaseOutCubic))
        }
    }
    Box(
        modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * with(density) { 24.dp.toPx() }
        },
    ) { content() }
}

/** Slight shrink while pressed, for tap feedback. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, enabled: Boolean = true): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, tween(140), label = "press")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/** A breathing purple halo around [content] while a call rings or connects. */
@Composable
fun CallHalo(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val disabled = animationsDisabled()
    val t = if (disabled) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "halo")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "halo",
        ).value
    }
    val density = LocalDensity.current
    Box(
        modifier
            .drawBehind {
                val glow = AppColors.Accent.copy(alpha = 0.08f + t * 0.12f)
                val extra = with(density) { (t * 14).dp.toPx() }
                drawCircle(glow, radius = size.minDimension / 2 + extra)
            }
            .border(1.dp, Color.White.copy(alpha = 0.07f + t * 0.08f), CircleShape)
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
