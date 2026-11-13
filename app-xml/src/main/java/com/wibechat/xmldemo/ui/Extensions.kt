package com.wibechat.xmldemo.ui

import android.content.Context
import android.content.res.Resources
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** dp → px. */
val Int.dp: Int get() = (this * Resources.getSystem().displayMetrics.density + 0.5f).toInt()
val Float.dp: Float get() = this * Resources.getSystem().displayMetrics.density

/**
 * Collects [flow] while this fragment's view is at least STARTED, like
 * `collectAsStateWithLifecycle` in Compose. Stops when the view goes away.
 */
fun <T> Fragment.collectOnView(flow: Flow<T>, block: suspend (T) -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { block(it) }
        }
    }
}

/**
 * Pads this view by the system bars (and the keyboard when [ime] is true).
 * The app draws edge to edge (required from Android 15), so every screen
 * root calls this once. Keeps the padding set in XML and adds the insets.
 */
fun View.applySystemBarsPadding(top: Boolean = true, bottom: Boolean = true, ime: Boolean = false) {
    val base = intArrayOf(paddingLeft, paddingTop, paddingRight, paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or
            (if (ime) WindowInsetsCompat.Type.ime() else 0)
        val i = insets.getInsets(types)
        v.updatePadding(
            left = base[0] + i.left,
            top = base[1] + if (top) i.top else 0,
            right = base[2] + i.right,
            bottom = base[3] + if (bottom) i.bottom else 0,
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/** A snackbar at the bottom of the activity, shared by every screen (like Flutter's ScaffoldMessenger). */
fun Fragment.showMessage(message: String) {
    val root = activity?.findViewById<View>(android.R.id.content) ?: return
    Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
}

fun View.hideKeyboard() {
    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
        .hideSoftInputFromWindow(windowToken, 0)
}
