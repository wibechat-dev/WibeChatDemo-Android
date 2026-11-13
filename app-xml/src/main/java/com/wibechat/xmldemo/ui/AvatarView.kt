package com.wibechat.xmldemo.ui

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import coil.dispose
import coil.load
import com.wibechat.xmldemo.R

/**
 * Round avatar: the photo when there is one (e.g. a Google account photo),
 * otherwise the first letter of the label. Size comes from the layout.
 * `app:avatarGradient` true uses the brand gradient instead of flat blue.
 */
class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val initial = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(ContextCompat.getColor(context, R.color.white))
        paint.isFakeBoldText = true
    }
    private val photo = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        visibility = GONE
    }

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.AvatarView)
        val gradient = a.getBoolean(R.styleable.AvatarView_avatarGradient, false)
        a.recycle()
        if (gradient) {
            setBackgroundResource(R.drawable.bg_brand_circle)
        } else {
            setBackgroundResource(R.drawable.bg_circle)
            backgroundTintList = ContextCompat.getColorStateList(context, R.color.primary)
        }
        addView(initial, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(photo, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setOval(0, 0, view.width, view.height)
        }
        clipToOutline = true
    }

    fun bind(label: String, photoUrl: String? = null) {
        initial.text = label.trim().firstOrNull()?.uppercase() ?: "?"
        val url = photoUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (url == null) {
            photo.dispose()
            photo.visibility = GONE
        } else {
            photo.visibility = VISIBLE
            photo.load(url) {
                crossfade(true)
                listener(onError = { _, _ -> photo.visibility = GONE })
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initial.setTextSize(TypedValue.COMPLEX_UNIT_PX, minOf(w, h) * 0.35f)
    }
}
