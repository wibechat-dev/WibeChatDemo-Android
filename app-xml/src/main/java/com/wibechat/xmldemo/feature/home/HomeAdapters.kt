package com.wibechat.xmldemo.feature.home

import android.content.res.ColorStateList
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wibechat.demo.core.model.AppUser
import com.wibechat.demo.core.model.CallLogEntry
import com.wibechat.demo.core.model.CallLogStatus
import com.wibechat.demo.core.model.PresenceEntry
import com.wibechat.demo.core.model.RoomInfo
import com.wibechat.xmldemo.R
import com.wibechat.xmldemo.databinding.ItemCallLogBinding
import com.wibechat.xmldemo.databinding.ItemPersonBinding
import com.wibechat.xmldemo.databinding.ItemRoomBinding
import com.wibechat.xmldemo.ui.AvatarView
import com.wibechat.xmldemo.ui.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private fun <T : Any> diff(id: (T) -> String) = object : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(oldItem: T, newItem: T) = id(oldItem) == id(newItem)

    @Suppress("DiffUtilEquals") // all items are data classes
    override fun areContentsTheSame(oldItem: T, newItem: T) = oldItem == newItem
}

// ---- Rooms -----------------------------------------------------------------

/**
 * Room cards. Each visible card listens to who is in that room right now
 * ([presence]) and stops listening when the card scrolls away.
 */
class RoomsAdapter(
    private val scope: CoroutineScope,
    private val myUid: String?,
    private val presence: (roomId: String) -> Flow<List<PresenceEntry>>,
    private val onJoin: (RoomInfo) -> Unit,
    private val onDelete: (RoomInfo) -> Unit,
) : ListAdapter<RoomInfo, RoomsAdapter.Holder>(diff { it.id }) {

    class Holder(val b: ItemRoomBinding) : RecyclerView.ViewHolder(b.root) {
        var presenceJob: Job? = null
        var boundRoomId: String? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemRoomBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val room = getItem(position)
        val b = holder.b
        b.name.text = room.name
        b.createdBy.text = "Created by ${room.createdByEmail ?: "unknown"}"
        b.join.setOnClickListener { onJoin(room) }
        b.more.isVisible = room.createdBy == myUid
        b.more.setOnClickListener { view ->
            PopupMenu(view.context, view).apply {
                val label = SpannableString("Delete room").apply {
                    setSpan(ForegroundColorSpan(ContextCompat.getColor(view.context, R.color.error)), 0, length, 0)
                }
                menu.add(label).setOnMenuItemClickListener { onDelete(room); true }
                show()
            }
        }

        // Re-subscribe only when the card shows a different room.
        if (holder.boundRoomId != room.id) {
            holder.presenceJob?.cancel()
            holder.boundRoomId = room.id
            bindPresence(b.presence, emptyList())
            holder.presenceJob = scope.launch { presence(room.id).collect { bindPresence(b.presence, it) } }
        }
    }

    override fun onViewRecycled(holder: Holder) {
        holder.presenceJob?.cancel()
        holder.presenceJob = null
        holder.boundRoomId = null
    }

    private fun bindPresence(row: LinearLayout, participants: List<PresenceEntry>) {
        row.removeAllViews()
        val context = row.context
        if (participants.isEmpty()) {
            row.addView(caption(row, "Be the first to join"))
            return
        }
        participants.take(3).forEach { p ->
            row.addView(
                AvatarView(context).apply { bind(p.label, p.photoUrl) },
                LinearLayout.LayoutParams(28.dp, 28.dp).apply { marginEnd = 4.dp },
            )
        }
        row.addView(caption(row, "${participants.size} joined"))
    }

    private fun caption(row: LinearLayout, text: String) = TextView(row.context).apply {
        this.text = text
        textSize = 12f
        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
    }
}

// ---- People ----------------------------------------------------------------

class PeopleAdapter(
    private val onCall: (uid: String, label: String, video: Boolean) -> Unit,
) : ListAdapter<AppUser, PeopleAdapter.Holder>(diff { it.uid }) {

    class Holder(val b: ItemPersonBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemPersonBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val person = getItem(position)
        val label = person.label
        val email = person.email?.trim()
        with(holder.b) {
            avatar.bind(label, person.photoUrl)
            name.text = label
            this.email.text = email
            this.email.isVisible = !email.isNullOrEmpty() && email != label
            voiceCall.setOnClickListener { onCall(person.uid, label, false) }
            videoCall.setOnClickListener { onCall(person.uid, label, true) }
        }
    }
}

// ---- Recents ---------------------------------------------------------------

class CallLogAdapter(
    private val onCall: (uid: String, label: String, video: Boolean) -> Unit,
) : ListAdapter<CallLogEntry, CallLogAdapter.Holder>(diff { it.id }) {

    class Holder(val b: ItemCallLogBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemCallLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = getItem(position)
        val context = holder.itemView.context
        val (icon, colorRes) = directionIconAndColor(entry)
        val color = ContextCompat.getColor(context, colorRes)
        with(holder.b) {
            avatar.bind(entry.otherLabel)
            name.text = entry.otherLabel
            time.text = formatWhen(entry.startedAt)
            directionIcon.setImageResource(icon)
            directionIcon.imageTintList = ColorStateList.valueOf(color)
            status.text = statusLabel(entry).ifEmpty { if (entry.outgoing) "Outgoing" else "Incoming" }
            status.setTextColor(color)
            callBack.setIconResource(
                if (entry.video) R.drawable.ic_videocam else R.drawable.ic_call,
            )
            callBack.contentDescription = if (entry.video) "Video call" else "Call"
            callBack.setOnClickListener { onCall(entry.otherUid, entry.otherLabel, entry.video) }
        }
    }

    private fun directionIconAndColor(entry: CallLogEntry): Pair<Int, Int> = when (entry.status) {
        CallLogStatus.Missed, CallLogStatus.Declined -> R.drawable.ic_call_missed to R.color.error
        CallLogStatus.Failed -> (if (entry.outgoing) R.drawable.ic_call_made else R.drawable.ic_call_missed) to R.color.error
        CallLogStatus.NoAnswer -> (if (entry.outgoing) R.drawable.ic_call_made else R.drawable.ic_call_missed) to R.color.primary
        CallLogStatus.Completed -> (if (entry.outgoing) R.drawable.ic_call_made else R.drawable.ic_call_received) to R.color.primary
    }

    private fun statusLabel(entry: CallLogEntry): String = when (entry.status) {
        CallLogStatus.Completed -> formatDuration(entry.durationSeconds)
        CallLogStatus.NoAnswer -> "No answer"
        CallLogStatus.Missed -> "Missed"
        CallLogStatus.Declined -> if (entry.outgoing) "Declined" else "You declined"
        CallLogStatus.Failed -> "Call failed"
    }
}
