package com.wibechat.xmldemo.ui

import android.view.View
import android.widget.Button
import androidx.core.view.isVisible

/**
 * Shows a spinner in place of the button's label (the spinner sits on top of
 * the button in the layout, in the same FrameLayout). Like Compose AppButton's isLoading.
 */
fun Button.setLoading(progress: View, loading: Boolean) {
    if (tag == null) tag = text // remember the label once
    text = if (loading) "" else tag as CharSequence
    progress.isVisible = loading
}
