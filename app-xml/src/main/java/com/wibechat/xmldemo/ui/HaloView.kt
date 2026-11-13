package com.wibechat.xmldemo.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.provider.Settings
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.wibechat.xmldemo.R

/**
 * Soft pulsing glow behind the caller's avatar on the ringing and
 * "Calling…" screens. Holds still when system animations are off.
 */
class HaloView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val accent = ContextCompat.getColor(context, R.color.accent)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f.dp
    }
    private var t = 0.5f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1600
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }

    init {
        setWillNotDraw(false)
        clipChildren = false
        val p = 20.dp
        setPadding(p, p, p, p)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        if (scale > 0f) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f
        glow.color = ColorUtils.setAlphaComponent(accent, ((0.08f + t * 0.12f) * 255).toInt())
        canvas.drawCircle(cx, cy, r + t * 14f.dp, glow)
        ring.color = ColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), ((0.07f + t * 0.08f) * 255).toInt())
        canvas.drawCircle(cx, cy, r - ring.strokeWidth / 2, ring)
        super.onDraw(canvas)
    }
}
