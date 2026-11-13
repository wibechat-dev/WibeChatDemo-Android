package com.wibechat.xmldemo.feature.call

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.wibechat.android.room.participant.ConnectionQuality
import com.wibechat.demo.calls.CallQuality
import com.wibechat.demo.calls.MediaStats
import com.wibechat.demo.calls.QualityMode
import com.wibechat.demo.core.model.AppUser
import com.wibechat.xmldemo.R
import com.wibechat.xmldemo.databinding.ItemAddUserBinding
import com.wibechat.xmldemo.databinding.SheetAddToCallBinding
import com.wibechat.xmldemo.databinding.SheetQualityBinding
import com.wibechat.xmldemo.ui.dp
import kotlinx.coroutines.launch
import java.util.Locale
import android.graphics.Typeface

fun ConnectionQuality.color(context: Context): Int = when (this) {
    ConnectionQuality.EXCELLENT -> ContextCompat.getColor(context, R.color.success)
    ConnectionQuality.GOOD -> ContextCompat.getColor(context, R.color.quality_good)
    ConnectionQuality.POOR -> ContextCompat.getColor(context, R.color.warning_light)
    ConnectionQuality.LOST -> ContextCompat.getColor(context, R.color.error)
    ConnectionQuality.UNKNOWN -> ContextCompat.getColor(context, R.color.white_50)
}

fun ConnectionQuality.label(): String = when (this) {
    ConnectionQuality.EXCELLENT -> "Excellent"
    ConnectionQuality.GOOD -> "Good"
    ConnectionQuality.POOR -> "Poor"
    ConnectionQuality.LOST -> "Lost"
    ConnectionQuality.UNKNOWN -> "Checking…"
}

private fun formatKbps(kbps: Int): String =
    if (kbps >= 1000) String.format(Locale.US, "%.1f Mbps", kbps / 1000f) else "$kbps kbps"

/**
 * Bottom sheet with the live numbers (connection, receiving, sending) and the
 * Auto / Highest switch. Updates while open from [CallViewModel.quality].
 */
fun Fragment.showQualitySheet(vm: CallViewModel) {
    val context = requireContext()
    val b = SheetQualityBinding.inflate(LayoutInflater.from(context))
    val dialog = BottomSheetDialog(context, R.style.ThemeOverlay_WibeChat_BottomSheet_Dark)
    dialog.setContentView(b.root)
    dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    dialog.behavior.skipCollapsed = true

    val white = Color.WHITE
    val dim = ContextCompat.getColor(context, R.color.white_60)
    val buttons = QualityMode.entries.associateWith { mode ->
        RadioButton(context).apply {
            id = ViewGroup.generateViewId()
            text = SpannableStringBuilder().apply {
                append(mode.label, StyleSpan(Typeface.BOLD), 0)
                append("\n")
                val start = length
                append(mode.description)
                setSpan(ForegroundColorSpan(dim), start, length, 0)
                setSpan(AbsoluteSizeSpan(12, true), start, length, 0)
            }
            setTextColor(white)
            buttonTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(white, dim),
            )
            setPadding(8.dp)
            setOnClickListener { vm.setQualityMode(mode) }
            b.modes.addView(this)
        }
    }

    fun render(q: CallQuality) {
        b.connectionDot.backgroundTintList = ColorStateList.valueOf(q.connection.color(context))
        b.connectionLabel.text = q.connection.label()
        bindStats(q.receiving, "No video from others", b.receivingMain, b.receivingDetail)
        bindStats(q.sending, "Your camera is off", b.sendingMain, b.sendingDetail)
        val reason = q.sending?.limitedBy
        b.limitedBy.isVisible = reason != null
        b.limitedBy.text = "Your video is reduced by $reason limits right now."
        b.switching.isVisible = q.switching
        buttons.forEach { (mode, button) ->
            button.isChecked = q.mode == mode
            button.isEnabled = !q.switching
        }
    }

    // Live while the sheet is open.
    val job = viewLifecycleOwner.lifecycleScope.launch { vm.quality.collect(::render) }
    dialog.setOnDismissListener { job.cancel() }
    dialog.show()
}

private fun bindStats(stats: MediaStats?, empty: String, main: android.widget.TextView, detail: android.widget.TextView) {
    if (stats == null) {
        main.text = empty
        main.setTextColor(ContextCompat.getColor(main.context, R.color.white_50))
        detail.isVisible = false
        return
    }
    main.text = stats.resolutionLabel
    main.setTextColor(Color.WHITE)
    detail.isVisible = true
    detail.text = "${stats.width}×${stats.height} · ${stats.fps} fps · ${formatKbps(stats.kbps)}"
}

/** Ring someone else into this 1:1 room. Hides people already in the call. */
fun Fragment.showAddToCallSheet(vm: CallViewModel) {
    val context = requireContext()
    val b = SheetAddToCallBinding.inflate(LayoutInflater.from(context))
    val dialog = BottomSheetDialog(context)
    dialog.setContentView(b.root)
    dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    dialog.behavior.skipCollapsed = true
    b.body.layoutParams.height = (resources.displayMetrics.heightPixels * 0.6f).toInt()

    val exclude = vm.inCallUids()
    val adapter = AddUserAdapter { user ->
        dialog.dismiss()
        vm.invite(user.uid, user.label)
    }
    b.users.layoutManager = LinearLayoutManager(context)
    b.users.adapter = adapter

    val job = viewLifecycleOwner.lifecycleScope.launch {
        vm.watchUsers().collect { list ->
            val candidates = list.filter { it.uid !in exclude }
            b.progress.isVisible = false
            b.empty.isVisible = candidates.isEmpty()
            b.users.isVisible = candidates.isNotEmpty()
            adapter.items = candidates
        }
    }
    dialog.setOnDismissListener { job.cancel() }
    dialog.show()
}

private class AddUserAdapter(private val onPick: (AppUser) -> Unit) : RecyclerView.Adapter<AddUserAdapter.Holder>() {
    class Holder(val b: ItemAddUserBinding) : RecyclerView.ViewHolder(b.root)

    var items: List<AppUser> = emptyList()
        @android.annotation.SuppressLint("NotifyDataSetChanged") // small list, replaced wholesale
        set(value) { field = value; notifyDataSetChanged() }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemAddUserBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val user = items[position]
        holder.b.avatar.bind(user.label, user.photoUrl)
        holder.b.name.text = user.label
        holder.b.root.setOnClickListener { onPick(user) }
    }
}
