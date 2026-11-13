package com.wibechat.xmldemo.feature.call

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import com.wibechat.android.room.Room
import com.wibechat.demo.calls.ParticipantTile
import com.wibechat.xmldemo.ui.dp
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Who's on screen:
 * - 1:1 → the other person full screen, you as a draggable corner tile (tap to swap)
 * - otherwise → an even grid: 2 rows for 2–4 people, then 3 or 4 columns
 * - Picture-in-Picture → only the other person
 *
 * Tiles are kept per participant across updates, so video renderers are only
 * created when someone joins or changes track, not on every state change.
 */
class VideoGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {

    private val tiles = LinkedHashMap<String, ParticipantTileView>()
    private var order: List<String> = emptyList()

    /** Non-null in 1:1 mode. */
    private var oneToOne: Pair<String, String>? = null // (localKey, remoteKey)
    private var localIsMain = false
    private var smallX: Float? = null
    private var smallY: Float? = null

    private val gap = 8.dp
    private val pipW = 110.dp
    private val pipH = 150.dp
    private val margin = 12.dp

    fun update(room: Room, local: ParticipantTile?, remotes: List<ParticipantTile>, pipMode: Boolean) {
        val shown: List<ParticipantTile> = when {
            pipMode -> listOfNotNull(remotes.firstOrNull())
            local != null && remotes.size == 1 -> listOf(local, remotes.first())
            else -> listOfNotNull(local) + remotes
        }
        oneToOne = if (!pipMode && local != null && remotes.size == 1) local.key to remotes.first().key else null

        // Drop tiles of people who left (and free their renderers).
        val keys = shown.map { it.key }.toSet()
        tiles.keys.filter { it !in keys }.forEach { key ->
            tiles.remove(key)?.let { it.release(); removeView(it) }
        }
        shown.forEach { tile ->
            val view = tiles.getOrPut(tile.key) {
                ParticipantTileView(context).also { addView(it); attachGestures(it) }
            }
            view.bind(room, tile)
        }
        order = shown.map { it.key }
        requestLayout()
    }

    /** Frees every renderer. Call from the screen's onDestroyView. */
    fun release() {
        tiles.values.forEach { it.release() }
        tiles.clear()
        removeAllViews()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        layoutFrames(w, h).forEach { (view, frame) ->
            view.measure(
                MeasureSpec.makeMeasureSpec(frame[2] - frame[0], MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(frame[3] - frame[1], MeasureSpec.EXACTLY),
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        layoutFrames(r - l, b - t).forEach { (view, f) -> view.layout(f[0], f[1], f[2], f[3]) }
    }

    /** left, top, right, bottom for each visible tile. */
    private fun layoutFrames(w: Int, h: Int): List<Pair<View, IntArray>> {
        val pair = oneToOne
        if (pair != null) {
            val main = tiles[if (localIsMain) pair.first else pair.second] ?: return emptyList()
            val small = tiles[if (localIsMain) pair.second else pair.first] ?: return emptyList()
            val (x, y) = smallPosition(w, h)
            main.elevation = 0f
            small.elevation = 8f.dp // above the main video, with a shadow
            return listOf(
                main to intArrayOf(0, 0, w, h),
                small to intArrayOf(x, y, x + pipW, y + pipH),
            )
        }

        val views = order.mapNotNull { tiles[it] }
        views.forEach { it.elevation = 0f }
        if (views.size <= 1) {
            return views.map { it to intArrayOf(gap, gap, w - gap, h - gap) }
        }
        val count = views.size
        val columns = when {
            count <= 4 -> (count + 1) / 2
            count <= 9 -> 3
            else -> 4
        }
        val rows = ceil(count / columns.toFloat()).toInt()
        val cellW = (w - gap * 2 - gap * (columns - 1)) / columns
        val cellH = (h - gap * 2 - gap * (rows - 1)) / rows
        return views.mapIndexed { i, view ->
            val left = gap + (i % columns) * (cellW + gap)
            val top = gap + (i / columns) * (cellH + gap)
            view to intArrayOf(left, top, left + cellW, top + cellH)
        }
    }

    /** Bottom-left by default; kept inside the view after drags and resizes. */
    private fun smallPosition(w: Int, h: Int): Pair<Int, Int> {
        val maxX = (w - pipW - margin).coerceAtLeast(margin).toFloat()
        val maxY = (h - pipH - margin).coerceAtLeast(margin).toFloat()
        val x = (smallX ?: margin.toFloat()).coerceIn(margin.toFloat(), maxX)
        val y = (smallY ?: maxY).coerceIn(margin.toFloat(), maxY)
        return x.toInt() to y.toInt()
    }

    private fun isSmall(view: View): Boolean {
        val pair = oneToOne ?: return false
        return view === tiles[if (localIsMain) pair.second else pair.first]
    }

    /** Drag the corner tile around; tap it to swap who is big. */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachGestures(tile: View) {
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false
        tile.setOnTouchListener { v, e ->
            if (!isSmall(v)) return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = v.left.toFloat(); startY = v.top.toFloat()
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        smallX = startX + dx
                        smallY = startY + dy
                        requestLayout()
                    }
                }
                MotionEvent.ACTION_UP -> if (!dragging) {
                    localIsMain = !localIsMain
                    requestLayout()
                }
            }
            true
        }
    }
}
