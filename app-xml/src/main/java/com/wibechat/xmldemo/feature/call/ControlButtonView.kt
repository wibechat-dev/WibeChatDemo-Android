package com.wibechat.xmldemo.feature.call

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.wibechat.xmldemo.databinding.ViewControlButtonBinding
import com.wibechat.xmldemo.ui.dp

/** Round-square button with a caption, used in the call control bar. */
class ControlButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val b = ViewControlButtonBinding.inflate(LayoutInflater.from(context), this)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val p = 2.dp
        setPadding(p, p, p, p)
        isClickable = true
        isFocusable = true
        // Shrinks a little while pressed (Compose pressScale).
        stateListAnimator = android.animation.AnimatorInflater.loadStateListAnimator(
            context, com.wibechat.xmldemo.R.animator.press_scale,
        )
    }

    fun set(label: String, @DrawableRes icon: Int, @ColorInt background: Int, @ColorInt iconColor: Int) {
        b.label.text = label
        contentDescription = label
        b.icon.setImageResource(icon)
        b.icon.imageTintList = ColorStateList.valueOf(iconColor)
        b.box.backgroundTintList = ColorStateList.valueOf(background)
    }
}
